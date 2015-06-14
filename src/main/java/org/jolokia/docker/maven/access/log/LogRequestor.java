package org.jolokia.docker.maven.access.log;/*
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

import java.io.IOException;
import java.io.InputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpUriRequest;
import org.jolokia.docker.maven.access.DockerAccessException;
import org.jolokia.docker.maven.access.UrlBuilder;
import org.jolokia.docker.maven.util.Timestamp;

import static org.jolokia.docker.maven.access.util.RequestUtil.newGet;

/**
 * Extractor for parsing the response of a log request
 *
 * @author roland
 * @since 28/11/14
 */
public class LogRequestor extends Thread implements LogGetHandle {

    // Patter for matching log entries
    private static final Pattern LOG_LINE = Pattern.compile("^\\[?([^\\s\\]]*)]?\\s+(.*)\\s*$");
    private final HttpClient client;

    private final String containerId;

    // callback called for each line extracted
    private LogCallback callback;

    private DockerAccessException exception;

    // Remember for asynchronous handling
    private HttpUriRequest request;

    private final UrlBuilder urlBuilder;
    
    /**
     * Create a helper object for requesting log entries synchronously ({@link #fetchLogs()}) or asynchronously ({@link #start()}.
     *
     * @param client HTTP client to use for requesting the docker host
     * @param urlBuilder builder that creates docker urls
     * @param containerId container for which to fetch the host
     * @param callback callback to call for each line received
     */
    public LogRequestor(HttpClient client, UrlBuilder urlBuilder, String containerId, LogCallback callback) {
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
            HttpResponse resp = client.execute(getLogRequest(false));
            parseResponse(resp);
        } catch (IOException exp) {
            callback.error(exp.getMessage());
        }
    }

    // Fetch log asynchronously as stream and follow stream
    public void run() {
        // Response to extract from

        try {
            request = getLogRequest(true);
            HttpResponse response = client.execute(request);
            parseResponse(response);
        } catch (IOException exp) {
            callback.error("IO Error while requesting logs: " + exp);
        }
    }

    private void parseResponse(HttpResponse response) {
        try (InputStream is = response.getEntity().getContent()) {
            byte[] headBuf = new byte[8];
            byte[] buf = new byte[8129];
            while (IOUtils.read(is, headBuf, 0, 8) > 0) {
                int type = headBuf[0];
                int declaredLength = extractLength(headBuf);
                int len = IOUtils.read(is, buf, 0, declaredLength);
                if (len < 1) {
                    callback.error("Invalid log format: Couldn't read " + declaredLength + " bytes from stream");
                    finish();
                    return;
                }
                String txt = new String(buf, 0, len, "UTF-8");

                callLogCallback(type, txt);
            }
            StatusLine status = response.getStatusLine();
            if (status.getStatusCode() != 200) {
                exception = new DockerAccessException("Error while reading logs (" + status + ")");
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
            callback.error("Invalid log format for '" + txt + "' (expected: \"<timestamp> <txt>\")");
            throw new LogCallback.DoneException();
        }
        Timestamp ts = new Timestamp(matcher.group(1));
        String logTxt = matcher.group(2);
        callback.log(type, ts, logTxt);
    }

    private int extractLength(byte[] b) {
        return b[7] & 0xFF |
               (b[6] & 0xFF) << 8 |
               (b[5] & 0xFF) << 16 |
               (b[4] & 0xFF) << 24;
    }


    private HttpUriRequest getLogRequest(boolean follow) {
        return newGet(urlBuilder.containerLogs(containerId, follow));
    }

    @Override
    public void finish() {
        if (request != null) {
            request.abort();
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
