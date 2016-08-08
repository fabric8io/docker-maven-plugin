package io.fabric8.maven.docker.access.hc.win;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

import org.apache.http.HttpHost;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.protocol.HttpContext;

import io.fabric8.maven.docker.util.Logger;

final class WindowsConnectionSocketFactory implements ConnectionSocketFactory {

	// Logging
    private final Logger log;
    private final File windowsPipeFile;

    public WindowsConnectionSocketFactory(String unixSocketPath, Logger log) {
        this.windowsPipeFile = new File(unixSocketPath);
        this.log = log;
    }

    @Override
    public Socket createSocket(HttpContext context) throws IOException {
        return new NamedPipe(log);
    }

    @Override
    public Socket connectSocket(int connectTimeout, Socket sock, HttpHost host, InetSocketAddress remoteAddress,
            InetSocketAddress localAddress, HttpContext context)
            throws IOException {
        sock.connect(new NpipeSocketAddress(windowsPipeFile), connectTimeout);
        return sock;
    }
}
