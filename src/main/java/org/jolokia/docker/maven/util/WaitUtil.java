package org.jolokia.docker.maven.util;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;

/**
 * @author roland
 * @since 18.10.14
 */
public class WaitUtil {

     // how long to wait at max when doing a http ping
    private static final long HARD_MAX_WAIT = 10 * 1000;

    // How long to wait between pings
    private static final long WAIT_RETRY_WAIT = 500;

    // Timeout for ping
    private static final int HTTP_PING_TIMEOUT = 500;

    private WaitUtil() {}

    public static long wait(int maxWait, WaitChecker ... checkers) throws TimeoutException {
        long max = maxWait > 0 ? maxWait : HARD_MAX_WAIT;
        long now = System.currentTimeMillis();
        do {
            for (WaitChecker checker : checkers) {
                if (checker.check()) {
                    cleanup(checkers);
                    return delta(now);
                }
            }
            sleep(WAIT_RETRY_WAIT);
        } while (delta(now) < max);
        if (checkers.length > 0) {
            // There has been several checks, but none has matched. So we ware throwing an exception and break
            // the build
            cleanup(checkers);
            throw new TimeoutException("No checker finished successfully");
        }
        return delta(now);
    }

    // Give checkers a possibility to clean up
    private static void cleanup(WaitChecker[] checkers) {
        for (WaitChecker checker : checkers) {
            checker.cleanUp();
        }
    }

    /**
     * Sleep a bit
     *
     * @param millis how long to sleep in milliseconds
     */
    public static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            // ...
        }
    }

    private static long delta(long now) {
        return System.currentTimeMillis() - now;
    }

    // ====================================================================================================

    /**
     * Check whether a given URL is available
     *
     */
    public static class HttpPingChecker implements WaitChecker {

        private String url;

        /**
         * Ping the given URL
         *
         * @param url URL to check
         */
        public HttpPingChecker(String url) {
            this.url = url;
        }

        @Override
        public boolean check() {
            try {
                return ping();
            } catch (IOException exception) {
                return false;
            }
        }

        private boolean ping() throws IOException {
            RequestConfig requestConfig =
                    RequestConfig.custom()
                                 .setSocketTimeout(HTTP_PING_TIMEOUT)
                                 .setConnectTimeout(HTTP_PING_TIMEOUT)
                                 .setConnectionRequestTimeout(HTTP_PING_TIMEOUT)
                                 .build();
            CloseableHttpClient httpClient = HttpClientBuilder.create()
                    .setDefaultRequestConfig(requestConfig)
                    .build();
            try {
                CloseableHttpResponse response = httpClient.execute(new HttpHead(url));
                try {
                    int responseCode = response.getStatusLine().getStatusCode();
                    return (responseCode >= 200 && responseCode <= 399);
                } finally {
                    response.close();
                }
            } finally {
                httpClient.close();
            }
        }

        @Override
        public void cleanUp() { }
    }

    // ====================================================================================================

    public interface WaitChecker {
        boolean check();
        void cleanUp();
    }
}
