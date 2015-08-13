package org.jolokia.docker.maven.util;

import java.io.IOException;
import java.net.*;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;

import com.sun.net.httpserver.*;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author roland
 * @since 18.10.14
 */
public class WaitUtilTest {

    static HttpServer server;
    static int port;
    static String httpPingUrl;
    private static String serverMethodToAssert;


    @Test(expected = TimeoutException.class)
    public void httpFail() throws TimeoutException {
        WaitUtil.HttpPingChecker checker = new WaitUtil.HttpPingChecker("http://127.0.0.1:" + port + "/fake-context/", null);
        long waited = WaitUtil.wait(500,checker);
    }

    @Test
    public void httpSuccess() throws TimeoutException {
        System.out.println("Check URL " + httpPingUrl);

        // preload - first time use almost always lasts much longer (i'm assuming its http client initialization behavior)
        WaitUtil.wait(700,new WaitUtil.HttpPingChecker(httpPingUrl, null));

        WaitUtil.HttpPingChecker checker = new WaitUtil.HttpPingChecker(httpPingUrl, null);
        long waited = WaitUtil.wait(700,checker);
        assertTrue("Waited longer than 500ms: " + waited, waited < 700);
    }

    @Test
    public void httpSuccessWithGetMethod() throws Exception {
        serverMethodToAssert = "GET";
        System.out.println("Check URL " + httpPingUrl);

        // preload - first time use almost always lasts much longer (i'm assuming its http client initialization behavior)
        WaitUtil.wait(700,new WaitUtil.HttpPingChecker(httpPingUrl, "GET"));

        WaitUtil.HttpPingChecker checker = new WaitUtil.HttpPingChecker(httpPingUrl, "GET");
        long waited = WaitUtil.wait(700,checker);
        assertTrue("Waited longer than 500ms: " + waited,waited < 700);
    }

    @Before
    public void setupDefaultServerAssertion() {
        serverMethodToAssert = "HEAD";
    }

    @BeforeClass
    public static void createServer() throws IOException {
        port = getRandomPort();
        System.out.println("Created .... " + port);
        InetAddress address = InetAddress.getLoopbackAddress();
        InetSocketAddress socketAddress = new InetSocketAddress(address,port);
        server = HttpServer.create(socketAddress, 10);

        // Prepare executor pool
        server.setExecutor(Executors.newSingleThreadExecutor());
        server.createContext("/test/",new HttpHandler() {
            @Override
            public void handle(HttpExchange httpExchange) throws IOException {
                String method = httpExchange.getRequestMethod();
                assertEquals(serverMethodToAssert, method);
                httpExchange.sendResponseHeaders(200, -1);
            }
        });
        httpPingUrl = "http://127.0.0.1:" + port + "/test/";
        server.start();
    }

    @AfterClass
    public static void cleanupServer() {
        server.stop(10);
    }

    private static int getRandomPort() throws IOException {
        for (int port = 22332; port < 22500;port++) {
            if (trySocket(port)) {
                return port;
            }
        }
        throw new IllegalStateException("Cannot find a single free port");
    }

    private static boolean trySocket(int port) throws IOException {
        InetAddress address = Inet4Address.getByName("localhost");
        ServerSocket s = null;
        try {
            s = new ServerSocket();
            s.bind(new InetSocketAddress(address,port));
            return true;
        } catch (IOException exp) {
            System.err.println("Port " + port + " already in use, tying next ...");
            // exp.printStackTrace();
            // next try ....
        } finally {
            if (s != null) {
                s.close();
            }
        }
        return false;
    }
}
