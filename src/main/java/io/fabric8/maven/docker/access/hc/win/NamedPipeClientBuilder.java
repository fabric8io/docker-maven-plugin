package io.fabric8.maven.docker.access.hc.win;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;

import io.fabric8.maven.docker.access.hc.ClientBuilder;
import io.fabric8.maven.docker.util.Logger;

import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.DnsResolver;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.impl.client.*;
import org.apache.http.impl.conn.BasicHttpClientConnectionManager;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;

public class NamedPipeClientBuilder implements ClientBuilder {

	// Logging
    private final Logger log;
    
    private final int maxConnections;
    private final Registry<ConnectionSocketFactory> registry;
    private final DnsResolver dnsResolver;

    public NamedPipeClientBuilder(String namedPipePath, int maxConnections, Logger log) {
        this.maxConnections = maxConnections;
        this.log = log;
        registry = buildRegistry(namedPipePath);
        dnsResolver = nullDnsResolver();        
    }

    @Override
    public CloseableHttpClient buildPooledClient() {
        final HttpClientBuilder httpBuilder = HttpClients.custom();
        final PoolingHttpClientConnectionManager manager = new PoolingHttpClientConnectionManager(registry, dnsResolver);
        manager.setDefaultMaxPerRoute(maxConnections);
        httpBuilder.setConnectionManager(manager);
        return httpBuilder.build();
    }

    @Override
    public CloseableHttpClient buildBasicClient() throws IOException {
        BasicHttpClientConnectionManager manager = new BasicHttpClientConnectionManager(registry, null, null, dnsResolver);
        return HttpClients.custom().setConnectionManager(manager).build();
    }

    private Registry<ConnectionSocketFactory> buildRegistry(String path) {
        final RegistryBuilder<ConnectionSocketFactory> registryBuilder = RegistryBuilder.create();
        registryBuilder.register("npipe", new WindowsConnectionSocketFactory(path, log));
        return registryBuilder.build();
    }

    private  DnsResolver nullDnsResolver() {
        return new DnsResolver() {
            @Override
            public InetAddress[] resolve(final String host) throws UnknownHostException {
                return new InetAddress[] {null};
            }
        };
    }
}
