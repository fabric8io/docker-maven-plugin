package org.jolokia.docker.maven.access.hc.http;/*
 * 
 * Copyright 2014 Roland Huss
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
import java.security.GeneralSecurityException;
import java.security.KeyStore;

import javax.net.ssl.SSLContext;

import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLContexts;
import org.apache.http.impl.client.*;
import org.apache.http.impl.conn.BasicHttpClientConnectionManager;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.jolokia.docker.maven.access.KeyStoreUtil;

/**
 * @author roland
 * @since 05/06/15
 */
public class HttpClientBuilder {

    private String certPath = null;
    private int maxConnections = 100;

    public HttpClientBuilder certPath(String certPath) {
        this.certPath = certPath;
        return this;
    }

    public HttpClientBuilder maxConnections(int maxConnections) {
        this.maxConnections = maxConnections;
        return this;
    }

    public CloseableHttpClient build() throws IOException {
        org.apache.http.impl.client.HttpClientBuilder builder = HttpClients.custom();
        HttpClientConnectionManager manager = getConnectionFactory(certPath, maxConnections);
        builder.setConnectionManager(manager);
        // TODO: For push-redirects working for 301, the redirect strategy should be relaxed (see #351)
        // However not sure whether we should do it right now and whether this is correct, since normally
        // a 301 should only occur when the image name is invalid (e.g. containing "//" in which case a redirect
        // happens to the URL with a single "/")
        // builder.setRedirectStrategy(new LaxRedirectStrategy());

        // TODO: Tune client if needed (e.g. add pooling factoring .....
        // But I think, that's not really required.

        return builder.build();
    }

    private static HttpClientConnectionManager getConnectionFactory(String certPath, int maxConnections) throws IOException {
        PoolingHttpClientConnectionManager ret =  certPath != null ?
                new PoolingHttpClientConnectionManager(getSslFactoryRegistry(certPath)) :
                new PoolingHttpClientConnectionManager();
        ret.setDefaultMaxPerRoute(maxConnections);
        return ret;
    }

    private static Registry<ConnectionSocketFactory> getSslFactoryRegistry(String certPath) throws IOException {
        try
        {
            KeyStore keyStore = KeyStoreUtil.createDockerKeyStore(certPath);

            SSLContext sslContext =
                    SSLContexts.custom()
                            .useTLS()
                            .loadKeyMaterial(keyStore, "docker".toCharArray())
                            .loadTrustMaterial(keyStore)
                            .build();
            String tlsVerify = System.getenv("DOCKER_TLS_VERIFY");
            SSLConnectionSocketFactory sslsf =
                    tlsVerify != null && !tlsVerify.equals("0") && !tlsVerify.equals("false") ?
                            new SSLConnectionSocketFactory(sslContext) :
                            new SSLConnectionSocketFactory(sslContext,
                                                           SSLConnectionSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);
            return RegistryBuilder.<ConnectionSocketFactory> create().register("https", sslsf).build();
        }
        catch (GeneralSecurityException e) {
            // this isn't ideal but the net effect is the same
            throw new IOException(e);
        }
    }
}
