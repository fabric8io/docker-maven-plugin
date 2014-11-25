package org.jolokia.docker.maven.util;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

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

    public static long wait(int maxWait,WaitChecker ... checkers) {
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
                HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
                connection.setConnectTimeout(HTTP_PING_TIMEOUT);
                connection.setReadTimeout(HTTP_PING_TIMEOUT);
                connection.setRequestMethod("HEAD");
                int responseCode = connection.getResponseCode();
                return (200 <= responseCode && responseCode <= 399);
            } catch (IOException exception) {
                return false;
            }
        }

        @Override
        public void cleanUp() { }
    }

    // ====================================================================================================

    public static interface WaitChecker {
        boolean check();
        void cleanUp();
    }
}
