package org.jolokia.docker.maven.access.hc.unix;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

import jnr.unixsocket.UnixSocketAddress;

import org.apache.http.HttpHost;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.protocol.HttpContext;

final class UnixConnectionSocketFactory implements ConnectionSocketFactory {

    @Override
    public Socket createSocket(HttpContext context) throws IOException {
        return new UnixSocket();
    }

    @Override
    public Socket connectSocket(int connectTimeout, Socket sock, HttpHost host, InetSocketAddress remoteAddress,
            InetSocketAddress localAddress, HttpContext context)
            throws IOException {
        sock.connect(new UnixSocketAddress(new File(host.getHostName())), connectTimeout);
        return sock;
    }
}
