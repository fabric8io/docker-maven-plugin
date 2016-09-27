package io.fabric8.maven.docker.access.hc.win;

import io.fabric8.maven.docker.access.hc.util.AbstractNativeClientBuilder;
import io.fabric8.maven.docker.util.Logger;

import org.apache.http.conn.socket.ConnectionSocketFactory;

public class NamedPipeClientBuilder extends AbstractNativeClientBuilder {
    public NamedPipeClientBuilder(String namedPipePath, int maxConnections, Logger log) {
        super(namedPipePath, maxConnections, log);
    }

    @Override
    protected ConnectionSocketFactory getConnectionSocketFactory() {
        return new NpipeConnectionSocketFactory(path, log);
    }

    @Override
    protected String getProtocol() {
        return "npipe";
    }
}
