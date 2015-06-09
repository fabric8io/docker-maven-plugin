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
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLContexts;
import org.apache.http.impl.client.*;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.jolokia.docker.maven.access.KeyStoreUtil;

/**
 * @author roland
 * @since 05/06/15
 */
public class ApacheHttpClientBuilder {

    private final String certPath;

    public ApacheHttpClientBuilder(String certPath) {
        this.certPath = certPath;
    }

    public CloseableHttpClient build() throws IOException {
        HttpClientBuilder builder = HttpClients.custom();
        PoolingHttpClientConnectionManager manager = getPoolingConnectionFactory(certPath);
        manager.setDefaultMaxPerRoute(10);
        builder.setConnectionManager(manager);
        // TODO: Tune client if needed (e.g. add pooling factoring .....
        // But I think, that's not really required.

        return builder.build();
    }

    private static PoolingHttpClientConnectionManager getPoolingConnectionFactory(String certPath) throws IOException {
        return certPath != null ?
                new PoolingHttpClientConnectionManager(getSslFactoryRegistry(certPath)) :
                new PoolingHttpClientConnectionManager();
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
            //SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(sslContext, SSLConnectionSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);
            SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(sslContext);
            return RegistryBuilder.<ConnectionSocketFactory> create().register("https", sslsf).build();
        }
        catch (GeneralSecurityException e) {
            // this isn't ideal but the net effect is the same
            throw new IOException(e);
        }
    }
}
