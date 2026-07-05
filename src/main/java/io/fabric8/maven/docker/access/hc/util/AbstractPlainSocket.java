package io.fabric8.maven.docker.access.hc.util;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.channels.SocketChannel;

/**
 * Base class for {@link Socket} transports that wrap a raw byte stream rather than a real TCP socket
 * (for example the wslc:// bridge). It provides the boilerplate handling of the {@link Socket} API
 * surface that does not apply to such a transport: address/port accessors return empty values, socket
 * options are no-ops or unsupported, {@code bind}/urgent-data are rejected, and the input/output
 * shutdown state is tracked here.
 * <p>
 * Subclasses implement the transport itself &mdash; {@link #connect(SocketAddress, int)},
 * {@link #getInputStream()}, {@link #getOutputStream()}, {@link #close()}, {@link #isConnected()} and
 * {@link #isClosed()} &mdash; and use {@link #validateEndpoint(SocketAddress, int, Class)} to validate
 * their connect arguments. Any option method below may be overridden by a subclass that has genuine
 * behaviour for it &mdash; the channel-backed unix:// and npipe:// transports, for example, override
 * the buffer-size, traffic-class and stream methods while inheriting the rest of this boilerplate.
 */
public abstract class AbstractPlainSocket extends Socket {

    /** Remote address, set once on connect by {@link #validateEndpoint(SocketAddress, int, Class)}
     * (or directly by a subclass' connect) before the socket is handed to the HTTP client. */
    protected SocketAddress socketAddress;

    protected volatile boolean inputShutdown;

    protected volatile boolean outputShutdown;

    /**
     * Validate {@link #connect(SocketAddress, int)} arguments: reject a negative timeout and an
     * endpoint that is not of the expected type, remember the endpoint as the remote address, and
     * return it cast to that type.
     */
    protected final <T extends SocketAddress> T validateEndpoint(SocketAddress endpoint, int timeout, Class<T> type) {
        if (timeout < 0) {
            throw new IllegalArgumentException("Timeout may not be negative: " + timeout);
        }
        if (!type.isInstance(endpoint)) {
            throw new IllegalArgumentException("Unsupported address type: "
                + (endpoint == null ? "null" : endpoint.getClass().getName()));
        }
        this.socketAddress = endpoint;
        return type.cast(endpoint);
    }

    @Override
    public void connect(SocketAddress endpoint) throws IOException {
        connect(endpoint, 0);
    }

    @Override
    public SocketAddress getRemoteSocketAddress() {
        return socketAddress;
    }

    @Override
    public void shutdownInput() throws IOException {
        inputShutdown = true;
    }

    @Override
    public void shutdownOutput() throws IOException {
        outputShutdown = true;
    }

    @Override
    public boolean isInputShutdown() {
        return inputShutdown;
    }

    @Override
    public boolean isOutputShutdown() {
        return outputShutdown;
    }

    @Override
    public void bind(SocketAddress bindpoint) throws IOException {
        throw new SocketException("Bind is not supported");
    }

    @Override
    public void sendUrgentData(int data) throws IOException {
        throw new SocketException("Urgent data not supported");
    }

    @Override
    public InetAddress getInetAddress() {
        return null;
    }

    @Override
    public InetAddress getLocalAddress() {
        return null;
    }

    @Override
    public int getPort() {
        return -1;
    }

    @Override
    public int getLocalPort() {
        return -1;
    }

    @Override
    public SocketAddress getLocalSocketAddress() {
        return null;
    }

    @Override
    public SocketChannel getChannel() {
        return null;
    }

    @Override
    public synchronized void setSoTimeout(int timeout) throws SocketException {
        // no-op: a stream/pipe transport has no socket-level read timeout
    }

    @Override
    public synchronized int getSoTimeout() throws SocketException {
        return 0;
    }

    @Override
    public void setKeepAlive(boolean on) throws SocketException {
        // just ignore
    }

    @Override
    public boolean getKeepAlive() throws SocketException {
        return true;
    }

    @Override
    public synchronized void setSendBufferSize(int size) throws SocketException {
        if (size <= 0) {
            throw new IllegalArgumentException("Send buffer size must be positive: " + size);
        }
        // just ignore
    }

    @Override
    public synchronized int getSendBufferSize() throws SocketException {
        throw new UnsupportedOperationException("Getting the send buffer size is not supported");
    }

    @Override
    public synchronized void setReceiveBufferSize(int size) throws SocketException {
        if (size <= 0) {
            throw new IllegalArgumentException("Receive buffer size must be positive: " + size);
        }
        // just ignore
    }

    @Override
    public synchronized int getReceiveBufferSize() throws SocketException {
        throw new UnsupportedOperationException("Getting the receive buffer size is not supported");
    }

    @Override
    public void setTrafficClass(int tc) throws SocketException {
        if (tc < 0 || tc > 255) {
            throw new IllegalArgumentException("Traffic class is not in range 0 -- 255: " + tc);
        }
        // just ignore
    }

    @Override
    public int getTrafficClass() throws SocketException {
        throw new UnsupportedOperationException("Getting the traffic class is not supported");
    }

    @Override
    public void setReuseAddress(boolean on) throws SocketException {
        // just ignore
    }

    @Override
    public boolean getReuseAddress() throws SocketException {
        throw new UnsupportedOperationException("Getting the SO_REUSEADDR option is not supported");
    }

    @Override
    public boolean isBound() {
        return false;
    }

    @Override
    public void setPerformancePreferences(int connectionTime, int latency, int bandwidth) {
        // no-op
    }
}
