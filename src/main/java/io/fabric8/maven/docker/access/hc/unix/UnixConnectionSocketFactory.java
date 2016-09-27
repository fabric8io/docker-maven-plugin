package io.fabric8.maven.docker.access.hc.unix;

import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketAddress;

import io.fabric8.maven.docker.access.hc.util.AbstractNativeSocketFactory;
import jnr.unixsocket.UnixSocketAddress;
import org.apache.http.protocol.HttpContext;

final class UnixConnectionSocketFactory extends AbstractNativeSocketFactory {

    UnixConnectionSocketFactory(String unixSocketPath) {
        super(unixSocketPath);
    }

    @Override
    public Socket createSocket(HttpContext context) throws IOException {
        return new UnixSocket();
    }

    @Override
    protected SocketAddress createSocketAddress(String path) {
        return new UnixSocketAddress(new File(path));
    }

}
