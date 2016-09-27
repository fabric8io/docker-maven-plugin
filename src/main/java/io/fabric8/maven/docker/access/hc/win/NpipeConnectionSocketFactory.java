package io.fabric8.maven.docker.access.hc.win;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;

import io.fabric8.maven.docker.access.hc.util.AbstractNativeSocketFactory;
import org.apache.http.HttpHost;
import org.apache.http.protocol.HttpContext;

import io.fabric8.maven.docker.util.Logger;

final class NpipeConnectionSocketFactory extends AbstractNativeSocketFactory {

	// Logging
    private final Logger log;

    NpipeConnectionSocketFactory(String npipePath, Logger log) {
        super(npipePath);
        this.log = log;
    }

    @Override
    public Socket createSocket(HttpContext context) throws IOException {
        return new NamedPipe(log);
    }

    @Override
    protected SocketAddress createSocketAddress(String path) {
        return new NpipeSocketAddress(new File(path));
    }
}
