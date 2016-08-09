package io.fabric8.maven.docker.access.hc.win;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;

import io.fabric8.maven.docker.access.hc.util.AbstractNativeClientBuilder;
import io.fabric8.maven.docker.access.hc.util.ClientBuilder;
import io.fabric8.maven.docker.util.Logger;

import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.DnsResolver;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.impl.client.*;
import org.apache.http.impl.conn.BasicHttpClientConnectionManager;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;

public class NamedPipeClientBuilder extends AbstractNativeClientBuilder {
    public NamedPipeClientBuilder(String namedPipePath, int maxConnections, Logger log) {
        super(namedPipePath, maxConnections, log);
    }

    @Override
    protected ConnectionSocketFactory getConnectionSocketFactory() {
        return new WindowsConnectionSocketFactory(path, log);
    }

    @Override
    protected String getProtocol() {
        return "npipe";
    }
}
