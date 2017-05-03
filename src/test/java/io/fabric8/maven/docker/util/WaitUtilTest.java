package io.fabric8.maven.docker.util;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.Collections;
import java.util.concurrent.*;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import io.fabric8.maven.docker.config.WaitConfiguration;
import io.fabric8.maven.docker.wait.*;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.assertj.core.api.Assertions.fail;
import static org.junit.Assert.*;

/**
 * @author roland
 * @since 18.10.14
 */
@SuppressWarnings("restriction")
public class WaitUtilTest {

    static HttpServer server;
    static int port;
    static String httpPingUrl;
    private static String serverMethodToAssert;

    @Test(expected = TimeoutException.class)
    public void httpFail() throws TimeoutException, PreconditionFailedException {

        HttpPingChecker checker = new HttpPingChecker("http://127.0.0.1:" + port + "/fake-context/");
        wait(500, checker);
    }

    private static long wait(int wait, WaitChecker checker) throws WaitTimeoutException, PreconditionFailedException {
        return wait(-1, wait, checker);
    }

    private static long wait(int failAfter, int wait, WaitChecker checker) throws WaitTimeoutException, PreconditionFailedException {
        return WaitUtil.wait(new TestWaitPrecondition(failAfter), wait, checker);
    }

    @Test
    public void httpSuccess() throws TimeoutException, PreconditionFailedException {
        HttpPingChecker checker = new HttpPingChecker(httpPingUrl);
        long waited = wait(700, checker);
        assertTrue("Waited less than 700ms: " + waited, waited < 700);
    }

    @Test
    public void containerNotRunningButWaitConditionOk() throws TimeoutException, PreconditionFailedException {
        HttpPingChecker checker = new HttpPingChecker(httpPingUrl);
        long waited = wait(1,700, checker);
        assertTrue("Waited less than 700ms: " + waited, waited < 700);
    }

    @Test(expected = PreconditionFailedException.class)
    public void containerNotRunningAndWaitConditionNok() throws TimeoutException, PreconditionFailedException {
        HttpPingChecker checker = new HttpPingChecker("http://127.0.0.1:" + port + "/fake-context/");
        wait(0, 700, checker);
    }

    @Test
    public void httpSuccessWithStatus() throws TimeoutException, PreconditionFailedException {
        for (String status : new String[] { "200", "200 ... 300", "200..250" }) {
            long waited = wait(700, new HttpPingChecker(httpPingUrl, WaitConfiguration.DEFAULT_HTTP_METHOD, status));
            assertTrue("Waited less than  700ms: " + waited, waited < 700);
        }
    }

    @Test(expected = TimeoutException.class)
    public void httpFailWithStatus() throws TimeoutException, PreconditionFailedException {
        wait(700, new HttpPingChecker(httpPingUrl, WaitConfiguration.DEFAULT_HTTP_METHOD, "500"));
    }

    @Test
    public void httpSuccessWithGetMethod() throws Exception {
        serverMethodToAssert = "GET";
        try {
            HttpPingChecker checker = new HttpPingChecker(httpPingUrl, "GET", WaitConfiguration.DEFAULT_STATUS_RANGE);
            long waited = wait(700, checker);
            assertTrue("Waited less than 500ms: " + waited, waited < 700);
        } finally {
            serverMethodToAssert = "HEAD";
        }
    }

    @Test
    public void tcpSuccess() throws TimeoutException, PreconditionFailedException {
        TcpPortChecker checker = new TcpPortChecker("localhost", Collections.singletonList(port));
        long waited = wait(700, checker);
        assertTrue("Waited less than 700ms: " + waited, waited < 700);
    }

    @Test
    public void cleanupShouldBeCalledAfterMatchedException() throws WaitTimeoutException, PreconditionFailedException {
        StubWaitChecker checker = new StubWaitChecker(true);
        wait(0, checker);
        assertTrue(checker.isCleaned());
    }

    @Test
    public void cleanupShouldBeCalledAfterFailedException() throws PreconditionFailedException {
        StubWaitChecker checker = new StubWaitChecker(false);
        try {
            wait(0, checker);
            fail("Failed expectation expected");
        } catch (WaitTimeoutException e) {
            assertTrue(checker.isCleaned());
        }
    }

    @Test
    public void waitOnCallable() throws Exception {
        long waited = waitOnCallable(1, 500);

        assertTrue(500 <= waited);
        assertTrue(1000 > waited);
    }

    @Test
    public void waitOnCallableFullWait() throws Exception {
        long waited = waitOnCallable(1, 1000);
        assertTrue(1000 <= waited);
    }

    private long waitOnCallable(long wait, final long sleep) throws WaitTimeoutException, ExecutionException {
        return WaitUtil.wait(5, new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                Thread.sleep(sleep);
                return null;
            }
        });
    }

    private static class StubWaitChecker implements WaitChecker {

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

        @Override
        public String getLogLabel() {
            return "";
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
        InetSocketAddress socketAddress = new InetSocketAddress(address, port);
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
            wait(700, new HttpPingChecker(httpPingUrl));
        } catch (TimeoutException | PreconditionFailedException exp) {

        }
    }

    @AfterClass
    public static void cleanupServer() {
        server.stop(1);
    }

    private static int getRandomPort() throws IOException {
        for (int port = 22332; port < 22500; port++) {
            if (trySocket(port)) {
                return port;
            }
        }
        throw new IllegalStateException("Cannot find a single free port");
    }

    private static boolean trySocket(int port) throws IOException {
        InetAddress address = InetAddress.getByName("localhost");
        try (ServerSocket s = new ServerSocket()) {
            s.bind(new InetSocketAddress(address, port));
            return true;
        } catch (IOException exp) {
            System.err.println("Port " + port + " already in use, tying next ...");
            // exp.printStackTrace();
            // next try ....
        }
        return false;
    }

    private static class TestWaitPrecondition implements WaitUtil.Precondition {
        private int nrFailAfter;

        public TestWaitPrecondition(int nrFailAfter) {
            this.nrFailAfter = nrFailAfter;
        }

        @Override
        public boolean isOk() {
            return nrFailAfter == -1 || nrFailAfter-- > 0;
        }

        @Override
        public void cleanup() {}

    }
}
