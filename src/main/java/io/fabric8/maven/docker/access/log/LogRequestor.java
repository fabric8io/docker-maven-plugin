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

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.base.Charsets;
import com.google.common.io.ByteStreams;
import io.fabric8.maven.docker.access.DockerAccessException;
import io.fabric8.maven.docker.access.UrlBuilder;
import io.fabric8.maven.docker.access.util.RequestUtil;
import io.fabric8.maven.docker.util.Timestamp;
import org.apache.commons.codec.binary.Hex;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.CloseableHttpClient;

/**
 * Extractor for parsing the response of a log request
 *
 * @author roland
 * @since 28/11/14
 */
public class LogRequestor extends Thread implements LogGetHandle {

    // Patter for matching log entries
    static final Pattern LOG_LINE = Pattern.compile("^\\[?(?<timestamp>[^\\s\\]]*)]?\\s+(?<entry>.*?)\\s*$", Pattern.DOTALL);
    private final CloseableHttpClient client;

    private final String containerId;

    // callback called for each line extracted
    private LogCallback callback;

    private DockerAccessException exception;

    // Remember for asynchronous handling so that the request can be aborted from the outside
    private HttpUriRequest request;

    private final UrlBuilder urlBuilder;

    // Lock for synchronizing closing of requests
    private final Object lock = new Object();

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
        this.setDaemon(true);
    }

    /**
     * Get logs and feed a callback with the content
     */
    public void fetchLogs() {
        try {
            callback.open();
            HttpResponse resp = client.execute(getLogRequest(false));
            parseResponse(resp);
        } catch (IOException exp) {
            callback.error(exp.getMessage());
        } finally {
            callback.close();
        }
    }

    // Fetch log asynchronously as stream and follow stream
    public void run() {
        // Response to extract from

        try {
            callback.open();
            request = getLogRequest(true);
            HttpResponse response = client.execute(request);
            parseResponse(response);
        } catch (IOException exp) {
            callback.error("IO Error while requesting logs: " + exp);
        } finally {
            callback.close();
            try {
                synchronized (lock) {
                    client.close();
                    request = null;
                }
            } catch (IOException exp) {
                callback.error("Error while closing client: " + exp);
            }
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

        String message = Charsets.UTF_8.newDecoder().decode(payload).toString();
        callLogCallback(type, message);
        return true;
    }

    private void parseResponse(HttpResponse response) {
        StatusLine status = response.getStatusLine();
        if (status.getStatusCode() != 200) {
            exception = new DockerAccessException("Error while reading logs (" + status + ")");
        }
        try (InputStream is = response.getEntity().getContent()) {
            while (true) {
                if (!readStreamFrame(is)) {
                    return;
                }
            }
        } catch (IOException e) {
            callback.error("Cannot process chunk response: " + e);
            finish();
        } catch (LogCallback.DoneException e) {
            // Can be thrown by a log callback which indicates that we are done.
            finish();
        }
    }

    private void callLogCallback(int type, String txt) throws LogCallback.DoneException {
        Matcher matcher = LOG_LINE.matcher(txt);
        if (!matcher.matches()) {
            callback.error(String.format("Invalid log format for '%s' (expected: \"<timestamp> <txt>\") [%04x %04x]",
                                         txt,(int) (txt.toCharArray())[0],(int) (txt.toCharArray())[1]));
            throw new LogCallback.DoneException();
        }
        Timestamp ts = new Timestamp(matcher.group("timestamp"));
        String logTxt = matcher.group("entry");
        callback.log(type, ts, logTxt);
    }

    private HttpUriRequest getLogRequest(boolean follow) {
        return RequestUtil.newGet(urlBuilder.containerLogs(containerId, follow));
    }

    @Override
    public void finish() {
        if (request != null) {
            synchronized (lock) {
                if (request != null) {
                    request.abort();
                }
            }
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
