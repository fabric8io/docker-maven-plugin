package io.fabric8.maven.docker.access.hc.wslc;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;

import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import io.fabric8.maven.docker.util.Logger;

class WslcClientBuilderTest {

    @Test
    void protocolIsWslc() {
        Assertions.assertEquals("wslc", new WslcClientBuilder("wslc.exe", 1, null).getProtocol());
    }

    @Test
    void buildsDialStdioCommandFromExecutable() {
        WslcConnectionSocketFactory factory =
            (WslcConnectionSocketFactory) new WslcClientBuilder("wslc.exe", 1, null).getConnectionSocketFactory();
        SocketAddress address = factory.createSocketAddress("wslc.exe");

        Assertions.assertTrue(address instanceof WslcSocketAddress);
        Assertions.assertEquals(
            Arrays.asList("wslc.exe", "system", "session", "run", "docker", "system", "dial-stdio"),
            ((WslcSocketAddress) address).command());
    }

    @Test
    void resolvesEmptyOrRootUrlPathConsistently() {
        // wslc:// -> null/empty path, wslc:/// -> "/" path: all resolve identically (to WSLC_EXECUTABLE
        // if set, otherwise the default). Assert consistency unconditionally, and the default only when
        // the environment does not override it, so the test does not depend on WSLC_EXECUTABLE.
        String fromNull = WslcClientBuilder.resolveExecutable(null);
        Assertions.assertEquals(fromNull, WslcClientBuilder.resolveExecutable(""));
        Assertions.assertEquals(fromNull, WslcClientBuilder.resolveExecutable("/"));
        if (System.getenv("WSLC_EXECUTABLE") == null) {
            Assertions.assertEquals(WslcClientBuilder.DEFAULT_WSLC_EXECUTABLE, fromNull);
        }
    }

    @Test
    void stripsLeadingSlashFromUrlPath() {
        // wslc:///wslc.exe -> path "/wslc.exe"
        Assertions.assertEquals("wslc.exe", WslcClientBuilder.resolveExecutable("/wslc.exe"));
    }

    @Test
    void keepsAbsoluteWindowsExecutablePath() {
        Assertions.assertEquals("C:\\Program Files\\WSL\\wslc.exe",
            WslcClientBuilder.resolveExecutable("C:\\Program Files\\WSL\\wslc.exe"));
    }

    @Test
    void createSocketReturnsUnconnectedProcessSocket() throws Exception {
        ConnectionSocketFactory factory = new WslcClientBuilder("wslc.exe", 1, null).getConnectionSocketFactory();
        Socket socket = factory.createSocket(null);
        Assertions.assertTrue(socket instanceof WslcProcessSocket);
        Assertions.assertFalse(socket.isConnected());
        socket.close();
    }

    @Test
    void autoDetectUrlIsValidUriResolvingToDefaultExecutable() {
        // The auto-detected URL must parse as a URI (DockerAccessWithHcClient calls URI.create on it)
        // and must NOT embed an executable in its path (that is resolved from WSLC_EXECUTABLE instead,
        // so paths with spaces/backslashes never reach URI parsing).
        URI uri = URI.create(WslcClientBuilder.AUTO_DETECT_URL);
        Assertions.assertEquals("wslc", uri.getScheme());
        String path = uri.getPath();
        Assertions.assertTrue(path == null || path.isEmpty() || "/".equals(path),
            "auto-detect URL must not embed an executable in its path, was: " + path);
    }

    @Test
    void connectWithMissingExecutableThrowsIoException() {
        WslcProcessSocket socket = new WslcProcessSocket(null);
        IOException ex = Assertions.assertThrows(IOException.class, () ->
            socket.connect(new WslcSocketAddress(Collections.singletonList("no-such-wslc-binary-xyz"))));
        Assertions.assertTrue(ex.getMessage().contains("no-such-wslc-binary-xyz"), ex.getMessage());
    }

    @Test
    void unconnectedSocketStateAndNoOpSetters() throws Exception {
        WslcProcessSocket s = new WslcProcessSocket(null);

        // Unconnected-state accessors
        Assertions.assertFalse(s.isConnected());
        Assertions.assertFalse(s.isClosed());
        Assertions.assertFalse(s.isBound());
        Assertions.assertFalse(s.isInputShutdown());
        Assertions.assertFalse(s.isOutputShutdown());
        Assertions.assertEquals(-1, s.getPort());
        Assertions.assertEquals(-1, s.getLocalPort());
        Assertions.assertNull(s.getInetAddress());
        Assertions.assertNull(s.getLocalAddress());
        Assertions.assertNull(s.getLocalSocketAddress());
        Assertions.assertNull(s.getRemoteSocketAddress());
        Assertions.assertNull(s.getChannel());
        Assertions.assertEquals(0, s.getSoTimeout());
        Assertions.assertTrue(s.getTcpNoDelay());
        Assertions.assertTrue(s.getKeepAlive());
        Assertions.assertTrue(s.toString().contains("unconnected"));

        // Streams before connect
        Assertions.assertThrows(SocketException.class, s::getInputStream);
        Assertions.assertThrows(SocketException.class, s::getOutputStream);

        // No-op setters must not throw
        s.setSoTimeout(1000);
        s.setTcpNoDelay(true);
        s.setKeepAlive(true);
        s.setReuseAddress(true);
        s.setTrafficClass(10);
        s.setSendBufferSize(1024);
        s.setReceiveBufferSize(1024);
        s.setPerformancePreferences(1, 2, 3);
        s.shutdownInput();
        s.shutdownOutput();
        Assertions.assertTrue(s.isInputShutdown());
        Assertions.assertTrue(s.isOutputShutdown());

        // close() on an unconnected socket is safe and flips isClosed()
        s.close();
        Assertions.assertTrue(s.isClosed());
    }

    @Test
    void socketRejectsUnsupportedAndInvalidOperations() {
        WslcProcessSocket s = new WslcProcessSocket(null);
        WslcSocketAddress negativeTimeoutAddress = new WslcSocketAddress(Collections.singletonList("x"));
        InetSocketAddress wrongAddressType = new InetSocketAddress("localhost", 1);

        Assertions.assertThrows(SocketException.class, () -> s.bind(null));
        Assertions.assertThrows(SocketException.class, () -> s.sendUrgentData(1));
        Assertions.assertThrows(UnsupportedOperationException.class, s::getSendBufferSize);
        Assertions.assertThrows(UnsupportedOperationException.class, s::getReceiveBufferSize);
        Assertions.assertThrows(UnsupportedOperationException.class, s::getTrafficClass);
        Assertions.assertThrows(UnsupportedOperationException.class, s::getReuseAddress);
        Assertions.assertThrows(IllegalArgumentException.class, () -> s.setSendBufferSize(0));
        Assertions.assertThrows(IllegalArgumentException.class, () -> s.setReceiveBufferSize(0));
        Assertions.assertThrows(IllegalArgumentException.class, () -> s.setTrafficClass(-1));
        Assertions.assertThrows(IllegalArgumentException.class, () -> s.setTrafficClass(256));

        // connect() argument validation: negative timeout and wrong address type are both rejected.
        Assertions.assertThrows(IllegalArgumentException.class, () -> s.connect(negativeTimeoutAddress, -1));
        Assertions.assertThrows(IllegalArgumentException.class, () -> s.connect(wrongAddressType, 0));
    }

    @Test
    void socketAddressEqualityHashCodeAndToString() {
        WslcSocketAddress a = new WslcSocketAddress(Arrays.asList("wslc.exe", "dial-stdio"));
        WslcSocketAddress b = new WslcSocketAddress(Arrays.asList("wslc.exe", "dial-stdio"));
        WslcSocketAddress c = new WslcSocketAddress(Collections.singletonList("other"));

        Assertions.assertEquals(a, b);
        Assertions.assertEquals(a.hashCode(), b.hashCode());
        Assertions.assertNotEquals(a, c);
        Assertions.assertNotEquals(a, new Object());
        Assertions.assertTrue(a.toString().contains("dial-stdio"));
    }

    @Test
    void connectWithLoggerLogsBridgeAndReportsAddress() throws Exception {
        // A logger present exercises the debug-log branches and the connected toString().
        RecordingLogger logger = new RecordingLogger();
        WslcProcessSocket socket = new WslcProcessSocket(logger);
        WslcSocketAddress address = new WslcSocketAddress(Collections.singletonList("sort"));
        try {
            socket.connect(address);
        } catch (IOException e) {
            Assumptions.abort("'sort' not available on this host: " + e.getMessage());
        }
        try {
            Assertions.assertTrue(socket.isConnected());
            Assertions.assertTrue(socket.toString().contains("sort"), socket.toString());
            Assertions.assertTrue(logger.debugged, "expected a debug log for the opened bridge");
        } finally {
            socket.close();
        }
    }

    @Test
    void roundTripsBytesThroughBridgeProcess() throws Exception {
        // 'sort' (present on Windows and Unix) echoes a single stdin line back on stdout once stdin
        // is closed, so it exercises the whole process-backed socket: connect, write, read, close.
        WslcProcessSocket socket = new WslcProcessSocket(null);
        try {
            socket.connect(new WslcSocketAddress(Collections.singletonList("sort")));
        } catch (IOException e) {
            // 'sort' is present on standard Windows and Linux hosts; skip rather than fail if absent.
            Assumptions.abort("'sort' not available on this host: " + e.getMessage());
        }
        Assertions.assertTrue(socket.isConnected());

        try (OutputStream out = socket.getOutputStream()) {
            out.write("ping\n".getBytes(StandardCharsets.US_ASCII));
        } // closing the stream half-closes stdin so 'sort' flushes and exits

        String echoed;
        try (InputStream in = socket.getInputStream()) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[256];
            int n;
            while ((n = in.read(buf)) != -1) {
                bos.write(buf, 0, n);
            }
            echoed = new String(bos.toByteArray(), StandardCharsets.US_ASCII);
        }
        Assertions.assertTrue(echoed.contains("ping"), "echoed=" + echoed);

        socket.close();
        Assertions.assertTrue(socket.isClosed());
    }

    /** Minimal {@link Logger} that only records whether debug() was called. */
    private static final class RecordingLogger implements Logger {
        volatile boolean debugged;

        @Override public void debug(String format, Object... params) { debugged = true; }
        @Override public void info(String format, Object... params) { /* no-op stub */ }
        @Override public void verbose(LogVerboseCategory category, String format, Object... params) { /* no-op stub */ }
        @Override public void warn(String format, Object... params) { /* no-op stub */ }
        @Override public void error(String format, Object... params) { /* no-op stub */ }
        @Override public String errorMessage(String message) { return message; }
        @Override public boolean isDebugEnabled() { return true; }
        @Override public boolean isVerboseEnabled() { return false; }
        @Override public void progressStart() { /* no-op stub */ }
        @Override public void progressUpdate(String layerId, String status, String progressMessage) { /* no-op stub */ }
        @Override public void progressFinished() { /* no-op stub */ }
    }
}
