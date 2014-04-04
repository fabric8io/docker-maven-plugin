package org.jolokia.maven.docker.util;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Properties;

import org.apache.maven.plugin.MojoExecutionException;

/**
 * @author roland
 * @since 04.04.14
 */
public class EnvUtil {

    // how long to wait at max when doing a http ping
    private static final long HTTP_PING_MAX_WAIT = 10 * 1000;

    // How long to wait between pings
    private static final long HTTP_PING_RETRY_WAIT = 300;

    // Timeout for ping
    private static final int PING_TIMEOUT = 500;


    /**
     * Wait on a given URL that an HTTP head requests succeeds, but not longer than
     * the amount given.
     *
     * @param waitUrl HTTP url to wait for
     * @param maxWait how long to wait at maximum
     * @return the time in milliseconds for how long it was waited for the URL
     */
    public static long httpPingWait(String waitUrl, int maxWait) {
        long max = maxWait > 0 ? maxWait : HTTP_PING_MAX_WAIT;
        long now = System.currentTimeMillis();
        do {
            if (httpPing(waitUrl)) {
                return delta(now);
            }
            sleep(HTTP_PING_RETRY_WAIT);
        } while (delta(now) < max);
        return delta(now);
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

    /**
     * Write out a property file
     *
     * @param props properties to write
     * @param portPropertyFile file name
     * @throws MojoExecutionException
     */
    public static void writePortProperties(Properties props,String portPropertyFile) throws MojoExecutionException {
        File propFile = new File(portPropertyFile);
        OutputStream os = null;
        try {
            os = new FileOutputStream(propFile);
            props.store(os,"Docker ports");
        } catch (IOException e) {
            throw new MojoExecutionException("Cannot write properties to " + portPropertyFile + ": " + e,e);
        } finally {
            if (os != null) {
                try {
                    os.close();
                } catch (IOException e) {
                    // best try ...
                }
            }
        }
    }

    // ====================================================================================================

    private static long delta(long now) {
        return System.currentTimeMillis() - now;
    }

    private static boolean httpPing(String url) {
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(PING_TIMEOUT);
            connection.setReadTimeout(PING_TIMEOUT);
            connection.setRequestMethod("HEAD");
            int responseCode = connection.getResponseCode();
            return (200 <= responseCode && responseCode <= 399);
        } catch (IOException exception) {
            return false;
        }
    }
}
