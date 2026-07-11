package io.fabric8.maven.docker.access.log;/*
 *
 * Copyright 2014 Roland Huss
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import com.google.common.io.ByteStreams;
import io.fabric8.maven.docker.access.DockerAccessException;
import io.fabric8.maven.docker.access.UrlBuilder;
import io.fabric8.maven.docker.access.util.RequestUtil;
import io.fabric8.maven.docker.util.TimestampFactory;
import org.apache.commons.codec.binary.Hex;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.CloseableHttpClient;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extractor for parsing the response of a log request
 *
 * @author roland
 * @since 28/11/14
 */
public class LogRequestor extends Thread implements LogGetHandle {
    // Patter for matching log entries
    static final Pattern LOG_LINE = Pattern.compile("^\\[?(?<timestamp>[^\\s\\]]*)]? (?<entry>.*?)\\s*$", Pattern.DOTALL);
    private final CloseableHttpClient client;

    private final String containerId;

    // callback called for each line extracted
    private LogCallback callback;

    private DockerAccessException exception;

    // Remember for asynchronous handling so that the request can be aborted from the outside.
    // Volatile because finish() reads/aborts it from another thread while run() reassigns it on each reconnect.
    private volatile HttpUriRequest request;

    private final UrlBuilder urlBuilder;

    // Set to true once finish() has been called so that an aborted request is not treated as a retryable error.
    private volatile boolean stopped;

    // Timestamp of the last received log line; used to resume the stream (docker "since") after a reconnect.
    private ZonedDateTime lastTimestamp;

    // Reconnect policy for the asynchronous follow stream (run()): retry transient IO errors instead of aborting.
    private int maxReconnectAttempts = 5;
    private long reconnectBackoffMillis = 1000L;

    /**
     * Create a helper object for requesting log entries synchronously ({@link #fetchLogs()}) or asynchronously ({@link #start()}.
     *
     * @param client HTTP client to use for requesting the docker host
     * @param urlBuilder builder that creates docker urls
     * @param containerId container for which to fetch the host
     * @param callback callback to call for each line received
     */
    public LogRequestor(CloseableHttpClient client, UrlBuilder urlBuilder, String containerId, LogCallback callback) {
        this.client = client;
        this.containerId = containerId;

        this.urlBuilder = urlBuilder;

        this.callback = callback;
        this.exception = null;
    }

    /**
     * Get logs and feed a callback with the content
     */
    public void fetchLogs() {
        try {
            callback.open();
            this.request = getLogRequest(false, null);
            final HttpResponse response = client.execute(request);
            parseResponse(response);
        } catch (LogCallback.DoneException e) {
            // Signifies we're finished with the log stream.
        } catch (IOException exp) {
            callback.error(exp.getMessage());
        } finally {
            callback.close();
        }
    }

    // Visible for testing: tune the reconnect behaviour of the asynchronous follow stream.
    LogRequestor reconnectPolicy(int maxAttempts, long backoffMillis) {
        this.maxReconnectAttempts = maxAttempts;
        this.reconnectBackoffMillis = backoffMillis;
        return this;
    }

    // Fetch log asynchronously as stream and follow stream. Transient stream errors (e.g. a dropped
    // Docker socket connection) are retried by reconnecting rather than aborting the wait; the stream
    // is resumed from the last received timestamp so already-consumed lines are (largely) not repeated.
    public void run() {
        try {
            callback.open();
            int attempt = 0;
            // lastTimestamp as observed at the previous failure; used to tell genuine forward progress
            // from a connection that merely replayed already-seen lines out of the "since" window.
            ZonedDateTime progressMark = null;
            while (!stopped) {
                try {
                    this.request = getLogRequest(true, resumeSince());
                    // finish() may have run between the while-check and here; don't open a fresh
                    // connection that nobody will abort.
                    if (stopped) {
                        return;
                    }
                    parseResponse(client.execute(request));
                    return;
                } catch (IOException e) {
                    if (stopped) {
                        // finish() aborted the request on purpose; not an error.
                        return;
                    }
                    // Reset the reconnect budget only when the stream actually advanced past the point
                    // of the previous failure. A socket that keeps replaying the same boundary line (or a
                    // persistently malformed frame) makes no progress and must not refill the budget, or a
                    // permanently broken stream would reconnect forever on the untimed log-following path.
                    boolean advanced = lastTimestamp != null && (progressMark == null || lastTimestamp.isAfter(progressMark));
                    if (advanced) {
                        attempt = 0;
                    }
                    progressMark = lastTimestamp;
                    if (attempt++ >= maxReconnectAttempts) {
                        callback.error("IO Error while requesting logs: " + e + " " + Thread.currentThread().getName());
                        return;
                    }
                    sleepBeforeReconnect(reconnectBackoffMillis);
                }
            }
        } catch (LogCallback.DoneException e) {
            // Signifies we're finished with the log stream.
        } catch (IOException e) {
            // Only reached if callback.open() itself fails; stream errors are handled inside the loop above.
            callback.error("IO Error while requesting logs: " + e + " " + Thread.currentThread().getName());
        } finally {
            callback.close();
        }
    }

    // Docker "since" value for resuming the follow stream after a reconnect, at nanosecond precision so
    // that at most the single line at the exact resume instant can be re-delivered (docker's "since" is an
    // inclusive lower bound) rather than every line sharing the same whole second. Returns null before any
    // line has been received, which streams from the beginning.
    private String resumeSince() {
        if (lastTimestamp == null) {
            return null;
        }
        return lastTimestamp.toEpochSecond() + "." + String.format("%09d", lastTimestamp.getNano());
    }

    private void sleepBeforeReconnect(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static class NoBytesReadException extends IOException {
        public NoBytesReadException() {
        }
    }

    /**
     * This is a copy of ByteStreams.readFully(), with the slight change that it throws
     * NoBytesReadException if zero bytes are read. Otherwise it is identical.
     *
     * @param in
     * @param bytes
     * @throws IOException
     */
    private void readFully(InputStream in, byte[] bytes) throws IOException {
        int read = ByteStreams.read(in, bytes, 0, bytes.length);
        if (read == 0) {
            throw new NoBytesReadException();
        } else if (read != bytes.length) {
            throw new EOFException("reached end of stream after reading "
                    + read + " bytes; " + bytes.length + " bytes expected");
        }
    }

    private boolean readStreamFrame(InputStream is) throws IOException, LogCallback.DoneException {
        // Read the header, which is composed of eight bytes. The first byte is an integer
        // indicating the stream type (0 = stdin, 1 = stdout, 2 = stderr), the next three are thrown
        // out, and the final four are the size of the remaining stream as an integer.
        ByteBuffer headerBuffer = ByteBuffer.allocate(8);
        headerBuffer.order(ByteOrder.BIG_ENDIAN);
        try {
            this.readFully(is, headerBuffer.array());
        } catch (NoBytesReadException e) {
            // Not bytes read for stream. Return false to stop consuming stream.
            return false;
        } catch (EOFException e) {
            throw new IOException("Failed to read log header. Could not read all 8 bytes. " + e.getMessage(), e);
        }

        // Grab the stream type (stdout, stderr, stdin) from first byte and throw away other 3 bytes.
        int type = headerBuffer.get();

        // Skip three bytes, then read size from remaining four bytes.
        int size = headerBuffer.getInt(4);

        // Ignore empty messages and keep reading.
        if (size <= 0) {
            return true;
        }

        // Read the actual message
        ByteBuffer payload = ByteBuffer.allocate(size);
        try {
            ByteStreams.readFully(is, payload.array());
        } catch (EOFException e) {
            throw new IOException("Failed to read log message. Could not read all " + size + " bytes. " + e.getMessage() +
                                  " [ Header: " + Hex.encodeHexString(headerBuffer.array()) + "]", e);
        }

        String message = StandardCharsets.UTF_8.decode(payload).toString();
        callLogCallback(type, message);
        return true;
    }

    private void parseResponse(HttpResponse response) throws LogCallback.DoneException, IOException {
        final StatusLine status = response.getStatusLine();
        if (status.getStatusCode() != 200) {
            exception = new DockerAccessException("Error while reading logs (" + status + ")");
            throw new LogCallback.DoneException();
        }

        final InputStream is = response.getEntity().getContent();
       
        while (true) {
            if (!readStreamFrame(is)) {
                return;
            }
        }
    }

    private void callLogCallback(int type, String txt) throws LogCallback.DoneException {
        Matcher matcher = LOG_LINE.matcher(txt);
        if (!matcher.matches()) {
            callback.error(String.format("Invalid log format for '%s' (expected: \"<timestamp> <txt>\") [%04x %04x]",
                                         txt,(int) (txt.toCharArray())[0],(int) (txt.toCharArray())[1]));
            throw new LogCallback.DoneException();
        }
        ZonedDateTime ts;
        try {
            ts = TimestampFactory.createTimestamp(matcher.group("timestamp"));
        } catch (DateTimeParseException ex) {
            ts = TimestampFactory.createTimestamp();
        }
        String logTxt = matcher.group("entry");
        this.lastTimestamp = ts;
        callback.log(type, ts, logTxt);
    }

    private HttpUriRequest getLogRequest(boolean follow, String since) {
        return RequestUtil.newGet(urlBuilder.containerLogs(containerId, follow, since));
    }

    @Override
    public void finish() {
        this.stopped = true;
        if (request != null) {
            request.abort();
            request = null;
        }
    }

    @Override
    public boolean isError() {
        return exception != null;
    }

    @Override
    public DockerAccessException getException() {
        return exception;
    }
}
