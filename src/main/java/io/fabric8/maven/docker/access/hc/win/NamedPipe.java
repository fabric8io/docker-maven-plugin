package io.fabric8.maven.docker.access.hc.win;

import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;

import io.fabric8.maven.docker.util.Logger;

final class NamedPipe extends Socket {

	// Logging
    private final Logger log;
	//for development purposes
	private final static boolean DEBUG = false;

    private final Object connectLock = new Object();
    private volatile boolean inputShutdown, outputShutdown;

    private String socketPath;
    private volatile SocketAddress socketAddress;
    private RandomAccessFile namedPipe;

    private FileChannel channel;

    NamedPipe(Logger log) throws IOException {
    	this.log = log;
    }

    @Override
    public void connect(SocketAddress endpoint) throws IOException {
        connect(endpoint, 0);
    }

    @Override
    public void connect(SocketAddress endpoint, int timeout) throws IOException {
        if (timeout < 0) {
            throw new IllegalArgumentException("Timeout may not be negative: " + timeout);
        }

        if (!(endpoint instanceof NpipeSocketAddress)) {
            throw new IllegalArgumentException("Unsupported address type: " + endpoint.getClass().getName());
        }

        this.socketAddress = endpoint;
        this.socketPath = ((NpipeSocketAddress) endpoint).path();

        synchronized (connectLock) {
        	this.namedPipe = new RandomAccessFile(socketPath, "rw");
        	this.channel = this.namedPipe.getChannel();
        }
    }

    @Override
    public void bind(SocketAddress bindpoint) throws IOException {
        throw new SocketException("Bind is not supported");
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
    public SocketAddress getRemoteSocketAddress() {
        return socketAddress;
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
    public InputStream getInputStream() throws IOException {
        if (!channel.isOpen()) {
            throw new SocketException("Socket is closed");
        }

        if (inputShutdown) {
            throw new SocketException("Socket input is shutdown");
        }

        return new FilterInputStream(Channels.newInputStream(channel)) {

        	@Override
        	public int read(byte[] b, int off, int len) throws IOException {
        		int readed = super.read(b, off, len);
                log.debug("RESPONSE %s", new String(b, off, len, Charset.forName("UTF-8")));
                return readed;
        	}

            @Override
            public void close() throws IOException {
                shutdownInput();
            }
        };
    }

    @Override
    public OutputStream getOutputStream() throws IOException {
        if (!channel.isOpen()) {
            throw new SocketException("Socket is closed");
        }

        if (outputShutdown) {
            throw new SocketException("Socket output is shutdown");
        }

        return new FilterOutputStream(Channels.newOutputStream(channel)) {
            @Override
            public void write(byte[] b, int off, int len) throws IOException {
                log.debug("REQUEST %s", new String(b, off, len, Charset.forName("UTF-8")));
                out.write(b, off, len);
            }

            @Override
            public void close() throws IOException {
                shutdownOutput();
            }
        };
    }

    @Override
    public void sendUrgentData(int data) throws IOException {
        throw new SocketException("Urgent data not supported");
    }

    @Override
    public void setSoTimeout(int timeout) {
    }

    @Override
    public int getSoTimeout() throws SocketException {
    	return 0;
    }

    @Override
    public void setSendBufferSize(int size) throws SocketException {
        if (size <= 0) {
            throw new IllegalArgumentException("Send buffer size must be positive: " + size);
        }

        if (!channel.isOpen()) {
            throw new SocketException("Socket is closed");
        }

        // just ignore
    }

    @Override
    public synchronized int getSendBufferSize() throws SocketException {
        if (!channel.isOpen()) {
            throw new SocketException("Socket is closed");
        }

        throw new UnsupportedOperationException("Getting the send buffer size is not supported");
    }

    @Override
    public synchronized void setReceiveBufferSize(int size) throws SocketException {
        if (size <= 0) {
            throw new IllegalArgumentException("Receive buffer size must be positive: " + size);
        }

        if (!channel.isOpen()) {
            throw new SocketException("Socket is closed");
        }

        // just ignore
    }

    @Override
    public synchronized int getReceiveBufferSize() throws SocketException {
        if (!channel.isOpen()) {
            throw new SocketException("Socket is closed");
        }

        throw new UnsupportedOperationException("Getting the receive buffer size is not supported");
    }

    @Override
    public void setKeepAlive(boolean on) throws SocketException {
    }

    @Override
    public boolean getKeepAlive() throws SocketException {
    	return true;
    }

    @Override
    public void setTrafficClass(int tc) throws SocketException {
        if (tc < 0 || tc > 255) {
            throw new IllegalArgumentException("Traffic class is not in range 0 -- 255: " + tc);
        }

        if (isClosed()) {
            throw new SocketException("Socket is closed");
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
    public void close() throws IOException {
        if (isClosed()) {
            return;
        }
        if (channel != null) {
            channel.close();
        }
        inputShutdown = true;
        outputShutdown = true;
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
    public String toString() {
        if (isConnected()) {
            return "WindowsPipe[addr=" + this.socketPath + ']';
        }

        return "WindowsPipe[unconnected]";
    }

    @Override
    public boolean isConnected() {
        return !isClosed();
    }

    @Override
    public boolean isBound() {
        return false;
    }

    @Override
    public boolean isClosed() {
        return channel != null && !channel.isOpen();
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
    public void setPerformancePreferences(int connectionTime, int latency, int bandwidth) {
        // no-op
    }
}
