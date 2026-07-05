package io.fabric8.maven.docker.access.hc.wslc;

import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import io.fabric8.maven.docker.access.hc.util.AbstractPlainSocket;
import io.fabric8.maven.docker.util.Logger;

/**
 * A {@link java.net.Socket} whose input/output streams are backed by a child process that bridges
 * stdin/stdout to the Docker daemon socket inside the wslc virtual machine
 * (typically {@code wslc.exe system session run docker system dial-stdio}).
 * <p>
 * This mirrors how {@code DOCKER_HOST=ssh://} works: the transport is a command producing a
 * raw byte stream to the remote daemon socket, which Apache HttpClient then drives as if it
 * were a normal socket. wslc does not expose a Windows named pipe or TCP port, so this stdio
 * bridge is the only host-visible channel to the daemon. All the socket-API boilerplate lives in
 * {@link AbstractPlainSocket}; this class holds only the process-bridge specifics.
 */
final class WslcProcessSocket extends AbstractPlainSocket {

    private final Logger log;

    private Process process;
    private volatile boolean closed;

    WslcProcessSocket(Logger log) {
        this.log = log;
    }

    @Override
    public void connect(SocketAddress endpoint, int timeout) throws IOException {
        List<String> command = validateEndpoint(endpoint, timeout, WslcSocketAddress.class).command();

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(false);
        try {
            this.process = pb.start();
        } catch (IOException e) {
            throw new IOException("Failed to start wslc Docker bridge process " + command + ": " + e.getMessage(), e);
        }
        if (log != null) {
            // One bridge process per HTTP connection; HTTP keep-alive reuses it across requests.
            log.debug("Opened wslc Docker bridge: %s", command);
        }

        // Drain the bridge's stderr so a full pipe buffer can never block the daemon stream,
        // and so connection failures (e.g. dial-stdio cannot reach the daemon) surface in the log.
        final Process p = this.process;
        Thread errorDrain = new Thread(() -> {
            byte[] buf = new byte[1024];
            try (InputStream err = p.getErrorStream()) {
                int n;
                while ((n = err.read(buf)) != -1) {
                    if (n > 0 && log != null) {
                        log.debug("wslc bridge stderr: %s", new String(buf, 0, n, StandardCharsets.UTF_8).trim());
                    }
                }
            } catch (IOException ignore) {
                // process gone
            }
        }, "wslc-bridge-stderr");
        errorDrain.setDaemon(true);
        errorDrain.start();
    }

    @Override
    public InputStream getInputStream() throws IOException {
        ensureConnected();
        if (inputShutdown) {
            throw new SocketException("Socket input is shutdown");
        }
        // stdout of the bridge process = bytes coming back from the Docker daemon socket
        return new FilterInputStream(process.getInputStream()) {
            @Override
            public void close() throws IOException {
                shutdownInput();
            }
        };
    }

    @Override
    public OutputStream getOutputStream() throws IOException {
        ensureConnected();
        if (outputShutdown) {
            throw new SocketException("Socket output is shutdown");
        }
        // stdin of the bridge process = bytes written to the Docker daemon socket
        return new FilterOutputStream(process.getOutputStream()) {
            @Override
            public void write(byte[] b, int off, int len) throws IOException {
                out.write(b, off, len);
            }

            @Override
            public void close() throws IOException {
                shutdownOutput();
            }
        };
    }

    private void ensureConnected() throws SocketException {
        if (process == null) {
            throw new SocketException("Socket is not connected");
        }
    }

    @Override
    public void setTcpNoDelay(boolean on) {
        // No-op: HttpClient sets this on the still-unconnected socket before the connect call, and a
        // process-backed socket has no TCP layer. Overriding avoids the inherited close-state guard.
    }

    @Override
    public boolean getTcpNoDelay() {
        return true;
    }

    @Override
    public synchronized void close() throws IOException {
        closed = true;
        inputShutdown = true;
        outputShutdown = true;
        if (process != null) {
            process.destroyForcibly();
        }
    }

    @Override
    public void shutdownOutput() throws IOException {
        super.shutdownOutput();
        // Closing the bridge's stdin signals EOF to the daemon socket (half-close).
        if (process != null) {
            process.getOutputStream().close();
        }
    }

    @Override
    public boolean isConnected() {
        return process != null && !isClosed();
    }

    @Override
    public boolean isClosed() {
        // Not closed before connect (process == null), so HttpClient can set socket options on the
        // fresh socket. Once connected, a dead bridge process counts as closed.
        return closed || (process != null && !process.isAlive());
    }

    @Override
    public String toString() {
        return isConnected() ? "WslcProcessSocket[" + socketAddress + "]" : "WslcProcessSocket[unconnected]";
    }
}
