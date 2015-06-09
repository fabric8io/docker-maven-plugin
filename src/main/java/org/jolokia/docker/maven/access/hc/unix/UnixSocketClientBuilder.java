package org.jolokia.docker.maven.access.hc.unix;

import java.net.*;

import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.DnsResolver;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.impl.client.*;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;

public class UnixSocketClientBuilder {

    public CloseableHttpClient build() {
        final HttpClientBuilder httpBuilder = HttpClients.custom();
        final Registry<ConnectionSocketFactory> registry = buildRegistry();
        final DnsResolver dnsResolver = nullDnsResolver();
        final HttpClientConnectionManager manager = new PoolingHttpClientConnectionManager(registry, dnsResolver);
        httpBuilder.setConnectionManager(manager);
        return httpBuilder.build();
    }

    private Registry<ConnectionSocketFactory> buildRegistry() {
        final RegistryBuilder<ConnectionSocketFactory> registryBuilder = RegistryBuilder.create();
        registryBuilder.register("unix", new UnixConnectionSocketFactory());
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
