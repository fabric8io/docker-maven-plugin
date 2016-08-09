package io.fabric8.maven.docker.access.hc.unix;

import io.fabric8.maven.docker.access.hc.util.AbstractNativeClientBuilder;
import io.fabric8.maven.docker.util.Logger;
import org.apache.http.conn.socket.ConnectionSocketFactory;

public class UnixSocketClientBuilder extends AbstractNativeClientBuilder {

    public UnixSocketClientBuilder(String unixSocketPath, int maxConnections, Logger log) {
        super(unixSocketPath, maxConnections, log);
    }

    @Override
    protected ConnectionSocketFactory getConnectionSocketFactory() {
        return new UnixConnectionSocketFactory(path);
    }

    @Override
    protected String getProtocol() {
        return "unix";
    }
}
