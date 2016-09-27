package io.fabric8.maven.docker.access.hc.util;
/*
 *
 * Copyright 2016 Roland Huss
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;

import io.fabric8.maven.docker.util.Logger;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.DnsResolver;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.BasicHttpClientConnectionManager;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;

/**
 * Base class for all clients which access Docker natively
 *
 * @author roland
 * @since 08/08/16
 */
abstract public class AbstractNativeClientBuilder implements ClientBuilder {

    protected final Registry<ConnectionSocketFactory> registry;
    protected final String path;
    protected final Logger log;

    private final DnsResolver dnsResolver;
    private final int maxConnections;

    public AbstractNativeClientBuilder(String path, int maxConnections, Logger logger) {
        this.maxConnections = maxConnections;
        this.log = logger;
        this.path = path;
        dnsResolver = nullDnsResolver();
        registry = buildRegistry(path);
    }

    protected abstract ConnectionSocketFactory getConnectionSocketFactory();
    protected abstract String getProtocol();

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

    // =========================================================================================================

    private Registry<ConnectionSocketFactory> buildRegistry(String path) {
        final RegistryBuilder<ConnectionSocketFactory> registryBuilder = RegistryBuilder.create();
        registryBuilder.register(getProtocol(), getConnectionSocketFactory());
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
