package io.fabric8.maven.docker.util;

import java.io.IOException;
import java.net.*;
import java.util.Collections;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;

import com.sun.net.httpserver.*;
import org.junit.*;

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
        WaitUtil.HttpPingChecker checker = new WaitUtil.HttpPingChecker("http://127.0.0.1:" + port + "/fake-context/");
        long waited = WaitUtil.wait(500,checker);
    }

    @Test
    public void httpSuccess() throws TimeoutException {
        WaitUtil.HttpPingChecker checker = new WaitUtil.HttpPingChecker(httpPingUrl);
        long waited = WaitUtil.wait(700,checker);
        assertTrue("Waited less than 700ms: " + waited, waited < 700);
    }

    @Test
    public void httpSuccessWithStatus() throws TimeoutException {
        for (String status : new String[] { "200", "200 ... 300", "200..250"}) {
            long waited = WaitUtil.wait(700,new WaitUtil.HttpPingChecker(httpPingUrl,null,status));
            assertTrue("Waited less than  700ms: " + waited, waited < 700);
        }
    }

    @Test(expected = TimeoutException.class)
    public void httpFailWithStatus() throws TimeoutException {
        WaitUtil.wait(700, new WaitUtil.HttpPingChecker(httpPingUrl, null, "500"));
    }

    @Test
    public void httpSuccessWithGetMethod() throws Exception {
        serverMethodToAssert = "GET";
        try {
            WaitUtil.HttpPingChecker checker = new WaitUtil.HttpPingChecker(httpPingUrl, "GET", null);
            long waited = WaitUtil.wait(700, checker);
            assertTrue("Waited less than 500ms: " + waited, waited < 700);
        } finally {
            serverMethodToAssert = "HEAD";
        }
    }

    @Test
    public void tcpSuccess() throws TimeoutException {
        WaitUtil.TcpPortChecker checker = new WaitUtil.TcpPortChecker("localhost", Collections.singletonList(port));
        long waited = WaitUtil.wait(700,checker);
        assertTrue("Waited less than 700ms: " + waited, waited < 700);
    }

    @Test
    public void cleanupShouldBeCalledAfterMatchedExceptation() throws WaitUtil.WaitTimeoutException {
        StubWaitChecker checker = new StubWaitChecker(true);
        WaitUtil.wait(0, checker);
        assertTrue(checker.isCleaned());
    }

    @Test
    public void cleanupShouldBeCalledAfterFailedExceptation() {
        StubWaitChecker checker = new StubWaitChecker(false);
        try {
            WaitUtil.wait(0, checker);
            fail("Failed expectation expected");
        } catch (WaitUtil.WaitTimeoutException e) {
            assertTrue(checker.isCleaned());
        }
    }

    private static class StubWaitChecker implements WaitUtil.WaitChecker {

        private final boolean checkResult;
        private boolean cleaned = false;

        public StubWaitChecker(boolean checkResult) {
            this.checkResult = checkResult;
        }

        @Override
        public boolean check() {
            return checkResult;
        }

        @Override
        public void cleanUp() {
            cleaned = true;
        }

        public boolean isCleaned() {
            return cleaned;
        }
    }

    @BeforeClass
    public static void createServer() throws IOException {
        port = getRandomPort();
        serverMethodToAssert = "HEAD";
        System.out.println("Created HTTP server at port " + port);
        InetAddress address = InetAddress.getLoopbackAddress();
        InetSocketAddress socketAddress = new InetSocketAddress(address,port);
        server = HttpServer.create(socketAddress, 10);

        // Prepare executor pool
        server.setExecutor(Executors.newSingleThreadExecutor());
        server.createContext("/test/", new HttpHandler() {
            @Override
            public void handle(HttpExchange httpExchange) throws IOException {
                String method = httpExchange.getRequestMethod();
                assertEquals(serverMethodToAssert, method);
                httpExchange.sendResponseHeaders(200, -1);
            }
        });
        httpPingUrl = "http://127.0.0.1:" + port + "/test/";
        server.start();

        // preload - first time use almost always lasts much longer (i'm assuming its http client initialization behavior)
        try {
            WaitUtil.wait(700, new WaitUtil.HttpPingChecker(httpPingUrl));
        } catch (TimeoutException exp) {

        }
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
