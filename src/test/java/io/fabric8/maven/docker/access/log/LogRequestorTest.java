package io.fabric8.maven.docker.access.log;

import io.fabric8.maven.docker.access.UrlBuilder;
import org.apache.commons.text.RandomStringGenerator;
import org.apache.http.HttpEntity;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.CloseableHttpClient;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.CharBuffer;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.regex.Matcher;

@ExtendWith(MockitoExtension.class)
class LogRequestorTest {
    private static final String containerId = new RandomStringGenerator.Builder().build().generate(64);

    @Mock
    CloseableHttpResponse httpResponse;

    @Mock
    UrlBuilder urlBuilder;

    @Mock
    StatusLine statusLine;

    @Mock
    HttpEntity httpEntity;

    @Mock
    HttpUriRequest httpUriRequest;

    @Mock
    LogCallback callback;

    @Mock
    CloseableHttpClient client;

    @Test
    void testEmptyMessage() throws Exception {
        final Streams type = Streams.STDOUT;
        final ByteBuffer headerBuffer = ByteBuffer.allocate(8);
        headerBuffer.put((byte) type.type);
        headerBuffer.putInt(4, 0);
        final InputStream inputStream = new ByteArrayInputStream(headerBuffer.array());

        setupMocks(inputStream);
        new LogRequestor(client, urlBuilder, containerId, callback).fetchLogs();

        Mockito.verify(callback, Mockito.never()).log(Mockito.eq(type.type), Mockito.any(ZonedDateTime.class), Mockito.anyString());
    }

    @Test
    void testStdoutMessage() throws Exception {
        final Streams type = Streams.STDOUT;
        RandomStringGenerator randomGenerator = new RandomStringGenerator.Builder().build();
        final String message0 = randomGenerator.generate(257);
        final String message1 = "test test";
        final String message2 = randomGenerator.generate(666);

        final ByteBuffer body = responseContent(type, message0, message1, message2);
        final InputStream inputStream = new ByteArrayInputStream(body.array());

        setupMocks(inputStream);
        new LogRequestor(client, urlBuilder, containerId, callback).fetchLogs();

        Mockito.verify(callback).log(Mockito.eq(type.type), Mockito.any(ZonedDateTime.class), Mockito.eq(message0));
        Mockito.verify(callback).log(Mockito.eq(type.type), Mockito.any(ZonedDateTime.class), Mockito.eq(message1));
        Mockito.verify(callback).log(Mockito.eq(type.type), Mockito.any(ZonedDateTime.class), Mockito.eq(message2));
    }

    @Test
    void testMessageWithLeadingWhitespace() throws Exception {
        final Streams type = Streams.STDOUT;
        final String message0 = " I have a leading space";
        final String message1 = "\tI have a leading tab";

        final ByteBuffer body = responseContent(type, message0, message1);
        final InputStream inputStream = new ByteArrayInputStream(body.array());

        setupMocks(inputStream);
        new LogRequestor(client, urlBuilder, containerId, callback).fetchLogs();

        Mockito.verify(callback).log(Mockito.eq(type.type), Mockito.any(ZonedDateTime.class), Mockito.eq(message0));
        Mockito.verify(callback).log(Mockito.eq(type.type), Mockito.any(ZonedDateTime.class), Mockito.eq(message1));
    }


    @Test
    void testAllStreams() throws Exception {
        final Random rand = new Random();
        final int upperBound = 1024;

        RandomStringGenerator randomGenerator = new RandomStringGenerator.Builder().build();

        final Streams type0 = Streams.STDIN;
        final String msg0 = randomGenerator.generate(rand.nextInt(upperBound));
        final ByteBuffer buf0 = messageToBuffer(type0, msg0);

        final Streams type1 = Streams.STDOUT;
        final String msg1 = randomGenerator.generate(rand.nextInt(upperBound));
        final ByteBuffer buf1 = messageToBuffer(type1, msg1);

        final Streams type2 = Streams.STDERR;
        final String msg2 = randomGenerator.generate(rand.nextInt(upperBound));
        final ByteBuffer buf2 = messageToBuffer(type2, msg2);

        final ByteBuffer body = combineBuffers(buf0, buf1, buf2);
        final InputStream inputStream = new ByteArrayInputStream(body.array());

        setupMocks(inputStream);
        new LogRequestor(client, urlBuilder, containerId, callback).fetchLogs();

        Mockito.verify(callback).log(Mockito.eq(type0.type), Mockito.any(ZonedDateTime.class), Mockito.eq(msg0));
        Mockito.verify(callback).log(Mockito.eq(type1.type), Mockito.any(ZonedDateTime.class), Mockito.eq(msg1));
        Mockito.verify(callback).log(Mockito.eq(type2.type), Mockito.any(ZonedDateTime.class), Mockito.eq(msg2));
    }

    @Test
    void testGarbageMessage() throws Exception {
        final Streams type = Streams.STDERR;
        final ByteBuffer buf0 = messageToBuffer(type, "This is a test message");
        final ByteBuffer buf1 = messageToBuffer(type, "This is another test message!");

        // Add one extra byte to buf0.
        int l0 = buf0.getInt(4);
        buf0.putInt(4, l0 + 1);

        // Set incorrect length in buf1.
        int l1 = buf1.getInt(4);
        buf1.putInt(4, l1 + 512);

        final ByteBuffer messages = ByteBuffer.allocate(buf0.limit() + buf1.limit());
        buf0.position(0);
        buf1.position(0);
        messages.put(buf0);
        messages.put(buf1);

        final InputStream inputStream = new ByteArrayInputStream(messages.array());

        setupMocks(inputStream);

        new LogRequestor(client, urlBuilder, containerId, callback).fetchLogs();

        // Should have called log() one time (for the first message). The message itself would
        // have been incorrect, since we gave it the wrong buffer length. The second message
        // fails to parse as the buffer runs out.
        Mockito.verify(callback).log(Mockito.eq(type.type), Mockito.any(ZonedDateTime.class), Mockito.anyString());
    }

    @Test
    void testMessageTooShort() throws Exception {
        final Streams type = Streams.STDIN;
        final ByteBuffer buf = messageToBuffer(type, "A man, a plan, a canal, Panama!");

        // Set length too long so reading buffer overflows.
        int l = buf.getInt(4);
        buf.putInt(4, l + 1);

        final InputStream inputStream = new ByteArrayInputStream(buf.array());
        setupMocks(inputStream);

        new LogRequestor(client, urlBuilder, containerId, callback).fetchLogs();

        // No calls to .log() should be made, as message parsing fails.
        Mockito.verify(callback, Mockito.never()).log(Mockito.eq(type.type), Mockito.any(ZonedDateTime.class), Mockito.anyString());
    }

    @Test
    void testMessageWithExtraBytes() throws Exception {
        final Streams type = Streams.STDOUT;
        final String message = "A man, a plan, a canal, Panama!";
        final ByteBuffer buf = messageToBuffer(type, message);

        // Set length too short so there is extra buffer left after reading.
        int l = buf.getInt(4);
        buf.putInt(4, l - 1);

        final ByteArrayInputStream inputStream = new ByteArrayInputStream(buf.array());
        setupMocks(inputStream);

        new LogRequestor(client, urlBuilder, containerId, callback).fetchLogs();

        Assertions.assertEquals(0, inputStream.available(), "Entire InputStream read.");

            // .log() is only called once. The one byte that is left off is lost and never recorded.
        Mockito.verify(callback)
            .log(Mockito.eq(type.type), Mockito.any(ZonedDateTime.class), Mockito.eq(message.substring(0, message.length() - 1)));
    }

    @Test
    void checkMutlilinePattern() {
        String line = "2016-07-15T20:34:06.024029849Z remote: Compressing objects:   4% (1/23)           \n" +
                      "remote: Compressing objects:   8% (2/23)           \n";
        String matched = "remote: Compressing objects:   4% (1/23)           \n" +
                      "remote: Compressing objects:   8% (2/23)";
        Matcher matcher = LogRequestor.LOG_LINE.matcher(line);
        Assertions.assertTrue(matcher.matches());
        Assertions.assertEquals(matched, matcher.group("entry"));
    }

    @Test
    void runCallsOpenAndCloseOnHandler() throws IOException {
        final Streams type = Streams.STDOUT;
        final String message = "";
        final ByteBuffer buf = messageToBuffer(type, message);
        final InputStream inputStream = new ByteArrayInputStream(buf.array());
        setupMocks(inputStream);

        new LogRequestor(client, urlBuilder, containerId, callback).run();

        Mockito.verify(callback).open();
        Mockito.verify(callback).close();
    }

    @Test
    void runCanConsumeEmptyStream() throws Exception {
        final Streams type = Streams.STDOUT;
        final String message = "";
        final ByteBuffer buf = messageToBuffer(type, message);
        final InputStream inputStream = new ByteArrayInputStream(buf.array());
        setupMocks(inputStream);

        LogRequestor logRequestor = new LogRequestor(client, urlBuilder, containerId, callback);
        Assertions.assertDoesNotThrow(()->logRequestor.run());
    }

    @Test
    void runCanConsumeSingleLineStream() throws Exception {
        final Streams type = Streams.STDOUT;
        final String message = "Hello, world!";
        final ByteBuffer buf = messageToBuffer(type, message);
        final InputStream inputStream = new ByteArrayInputStream(buf.array());
        setupMocks(inputStream);

        new LogRequestor(client, urlBuilder, containerId, callback).run();

        Mockito.verify(callback).log(Mockito.eq(type.type), Mockito.any(ZonedDateTime.class), Mockito.anyString());
    }

    @Test
    void testUnparseableTimestampFallsBackAndDoesNotKillStream() throws Exception {
        final Streams type = Streams.STDERR;
        final String entry = "the build failed";
        // Docker can emit a line whose timestamp position is not an ISO-8601 value (e.g. "Error" on stderr).
        // frameToBuffer injects the line verbatim, so the LOG_LINE timestamp group ("Error") cannot be parsed.
        final ByteBuffer buf = frameToBuffer(type, "Error " + entry);
        final InputStream inputStream = new ByteArrayInputStream(buf.array());
        setupMocks(inputStream);

        final LogRequestor logRequestor = new LogRequestor(client, urlBuilder, containerId, callback);

        // The unparseable timestamp must not kill the log-follow thread (#1428): run() completes normally and
        // the line is still delivered, with the current timestamp substituted for the one that failed to parse.
        Assertions.assertDoesNotThrow(logRequestor::run);

        Mockito.verify(callback).log(Mockito.eq(type.type), Mockito.any(ZonedDateTime.class), Mockito.eq(entry));
        Mockito.verify(callback, Mockito.never()).error(Mockito.anyString());
    }

    @Test
    void runCanHandleIOException() throws Exception {
        final IOExceptionStream stream = new IOExceptionStream();
        setupMocks(stream);

        // reconnectPolicy(0, ...) => give up immediately (keeps the test fast and asserts a single error).
        new LogRequestor(client, urlBuilder, containerId, callback).reconnectPolicy(0, 0).run();
        Mockito.verify(callback).error(Mockito.anyString());
    }

    @Test
    void runReconnectsAfterTransientIOException() throws Exception {
        final Streams type = Streams.STDOUT;
        final String message = "Hello, world!";
        final InputStream inputStream = new ByteArrayInputStream(messageToBuffer(type, message).array());

        // First connection attempt fails with a transient IO error; the reconnect then succeeds.
        Mockito.doThrow(new IOException("Bad file descriptor"))
               .doReturn(httpResponse)
               .when(client).execute(Mockito.any(HttpUriRequest.class));
        Mockito.doReturn(statusLine).when(httpResponse).getStatusLine();
        Mockito.doReturn(200).when(statusLine).getStatusCode();
        Mockito.doReturn(httpEntity).when(httpResponse).getEntity();
        Mockito.doReturn(inputStream).when(httpEntity).getContent();
        Mockito.doReturn("url").when(urlBuilder).containerLogs(Mockito.anyString(), Mockito.anyBoolean(), Mockito.any());

        new LogRequestor(client, urlBuilder, containerId, callback).reconnectPolicy(3, 0).run();

        Mockito.verify(callback).log(Mockito.eq(type.type), Mockito.any(ZonedDateTime.class), Mockito.eq(message));
        Mockito.verify(callback, Mockito.never()).error(Mockito.anyString());
        Mockito.verify(client, Mockito.times(2)).execute(Mockito.any(HttpUriRequest.class));
    }

    @Test
    void runGivesUpAfterMaxReconnectAttempts() throws Exception {
        Mockito.doThrow(new IOException("Bad file descriptor"))
               .when(client).execute(Mockito.any(HttpUriRequest.class));
        Mockito.doReturn("url").when(urlBuilder).containerLogs(Mockito.anyString(), Mockito.anyBoolean(), Mockito.any());

        new LogRequestor(client, urlBuilder, containerId, callback).reconnectPolicy(3, 0).run();

        // Initial attempt + 3 reconnect attempts, then a single error is reported.
        Mockito.verify(client, Mockito.times(4)).execute(Mockito.any(HttpUriRequest.class));
        Mockito.verify(callback).error(Mockito.anyString());
    }

    @Test
    void reconnectRequestResumesWithSince() throws Exception {
        final Streams type = Streams.STDOUT;
        final InputStream frameThenThrow = new FrameThenIOExceptionStream(messageToBuffer(type, "before disconnect").array());
        final InputStream empty = new ByteArrayInputStream(new byte[0]);

        Mockito.doReturn(httpResponse).when(client).execute(Mockito.any(HttpUriRequest.class));
        Mockito.doReturn(statusLine).when(httpResponse).getStatusLine();
        Mockito.doReturn(200).when(statusLine).getStatusCode();
        Mockito.doReturn(httpEntity).when(httpResponse).getEntity();
        Mockito.doReturn(frameThenThrow, empty).when(httpEntity).getContent();
        Mockito.doReturn("url").when(urlBuilder).containerLogs(Mockito.anyString(), Mockito.anyBoolean(), Mockito.any());

        new LogRequestor(client, urlBuilder, containerId, callback).reconnectPolicy(3, 0).run();

        // First attempt streams from the beginning (since == null); after receiving a line it reconnects with a since timestamp.
        Mockito.verify(urlBuilder).containerLogs(Mockito.eq(containerId), Mockito.eq(true), Mockito.isNull());
        Mockito.verify(urlBuilder).containerLogs(Mockito.eq(containerId), Mockito.eq(true), Mockito.isNotNull());
    }

    @Test
    void runGivesUpWhenReconnectsOnlyReplaySeenLines() throws Exception {
        final Streams type = Streams.STDOUT;
        // Every connection re-delivers the very same (already-seen) log line and then drops. Because the
        // stream never advances past the last received line, delivering a line must NOT refill the reconnect
        // budget, or a permanently broken socket would reconnect forever. The requestor has to give up.
        Mockito.doReturn(httpResponse).when(client).execute(Mockito.any(HttpUriRequest.class));
        Mockito.doReturn(statusLine).when(httpResponse).getStatusLine();
        Mockito.doReturn(200).when(statusLine).getStatusCode();
        Mockito.doReturn(httpEntity).when(httpResponse).getEntity();
        Mockito.doAnswer(invocation -> new FrameThenIOExceptionStream(messageToBuffer(type, "same line").array()))
               .when(httpEntity).getContent();
        Mockito.doReturn("url").when(urlBuilder).containerLogs(Mockito.anyString(), Mockito.anyBoolean(), Mockito.any());

        new LogRequestor(client, urlBuilder, containerId, callback).reconnectPolicy(3, 0).run();

        // Initial attempt + exactly 3 reconnects, then give up — bounded, not an unbounded loop.
        Mockito.verify(client, Mockito.times(4)).execute(Mockito.any(HttpUriRequest.class));
        Mockito.verify(callback).error(Mockito.anyString());
    }

    private void setupMocks(final InputStream inputStream) throws IOException {
        Mockito.doReturn(httpResponse).when(client).execute(Mockito.any(HttpUriRequest.class));
        Mockito.doReturn(statusLine).when(httpResponse).getStatusLine();
        Mockito.doReturn(200).when(statusLine).getStatusCode();
        Mockito.doReturn(httpEntity).when(httpResponse).getEntity();
        Mockito.doReturn(inputStream).when(httpEntity).getContent();

        Mockito.doReturn("url").when(urlBuilder).containerLogs(Mockito.anyString(), Mockito.anyBoolean(), Mockito.any());
    }

    private enum Streams {
        STDIN(0),
        STDOUT(1),
        STDERR(2);

        public final int type;

        Streams(int type) {
            this.type = type;
        }
    }

    /**
     * Create a bytebuffer with all the messages. Timestamps will be added to each one.
     */
    private static ByteBuffer responseContent(Streams stream, String... messages) throws Exception {
        List<ByteBuffer> buffers = new ArrayList<>(messages.length);
        for (String message : messages) {
            buffers.add(messageToBuffer(stream, message));
        }

        ByteBuffer[] bufArray = new ByteBuffer[buffers.size()];
        return combineBuffers(buffers.toArray(bufArray));
    }

    private static ByteBuffer combineBuffers(ByteBuffer... buffers) {
        int length = 0;
        for (ByteBuffer b : buffers) {
            length += b.limit();
        }

        ByteBuffer result = ByteBuffer.allocate(length);
        for (ByteBuffer b : buffers) {
            b.position(0);
            result = result.put(b);
        }

        return result;
    }

    /**
     * Create a bytebuffer for a single string message. A timestamp will be added.
     */
    private static ByteBuffer messageToBuffer(Streams stream, String message) throws IOException {
        return frameToBuffer(stream, logMessage(message));
    }

    /**
     * Frame an already-complete log line (timestamp prefix included) into a docker stream frame,
     * without prepending a timestamp. Used to inject a line whose timestamp cannot be parsed.
     */
    private static ByteBuffer frameToBuffer(Streams stream, String logLine) throws IOException {
        CharsetEncoder encoder = StandardCharsets.UTF_8.newEncoder();
        ByteBuffer payload = encoder.encode(CharBuffer.wrap(logLine.toCharArray()));
        assert payload.order() == ByteOrder.BIG_ENDIAN;
        int length = payload.limit();

        ByteBuffer result = ByteBuffer.allocate(length + 8);
        result.order(ByteOrder.BIG_ENDIAN);

        result.put((byte) stream.type);
        result.position(result.position() + 3);
        result.putInt(length);
        result.put(payload);

        return result;
    }

    /**
     * Create a new string from message that has a timestamp prefix.
     */
    private static String logMessage(String message) {
        return String.format("[2015-08-05T12:34:56Z] %s", message);
    }

    private class IOExceptionStream extends InputStream {
        public int read() throws IOException {
            throw new IOException("Something bad happened");
        }
    }

    /**
     * Delivers the given bytes (one complete log frame) and then throws an IOException on the next read,
     * simulating a mid-stream socket disconnect after some log output was already consumed.
     */
    private class FrameThenIOExceptionStream extends InputStream {
        private final ByteArrayInputStream delegate;

        FrameThenIOExceptionStream(byte[] frame) {
            this.delegate = new ByteArrayInputStream(frame);
        }

        public int read() throws IOException {
            int b = delegate.read();
            if (b == -1) {
                throw new IOException("Bad file descriptor");
            }
            return b;
        }
    }
}
