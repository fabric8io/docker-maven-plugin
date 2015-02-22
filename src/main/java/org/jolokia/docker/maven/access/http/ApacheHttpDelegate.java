package org.jolokia.docker.maven.access.http;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.security.GeneralSecurityException;
import java.security.KeyStore;

import javax.net.ssl.SSLContext;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLContexts;
import org.apache.http.entity.FileEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.util.EntityUtils;
import org.jolokia.docker.maven.access.KeyStoreUtil;

public class ApacheHttpDelegate {

    private static final String HEADER_ACCEPT = "Accept";
    private static final String HEADER_ACCEPT_ALL = "*/*";

    private final CloseableHttpClient httpClient;

    public ApacheHttpDelegate(String certPath) throws IOException {
        this.httpClient = createHttpClient(certPath);
    }

    public Result delete(String url, int statusCode, int... additional) throws IOException, HttpRequestException {
        return parseResponse(httpClient.execute(newDelete(url)), statusCode, additional);
    }

    public Result get(String url, int statusCode, int... additional) throws IOException, HttpRequestException {
        return parseResponse(httpClient.execute(newGet(url)), statusCode, additional);
    }
        
    public Result post(String url, String body, int statusCode, int... additional) throws IOException, HttpRequestException {
        return parseResponse(httpClient.execute(newPost(url, body)), statusCode, additional);
    }
    
    public CloseableHttpClient getHttpClient() {
        return httpClient;
    }
        
    private HttpUriRequest addDefaultHeaders(HttpUriRequest req) {
        req.addHeader(HEADER_ACCEPT, HEADER_ACCEPT_ALL);
        req.addHeader("Content-Type", "application/json");
        return req;
    }

    private CloseableHttpClient createHttpClient(String certPath) throws IOException {
        HttpClientBuilder builder = HttpClients.custom();
        PoolingHttpClientConnectionManager manager = getPoolingConnectionFactory(certPath);
        manager.setDefaultMaxPerRoute(10);
        builder.setConnectionManager(manager);
        // TODO: Tune client if needed (e.g. add pooling factoring .....
        // But I think, that's not really required.

        return builder.build();
    }

    private PoolingHttpClientConnectionManager getPoolingConnectionFactory(String certPath) throws IOException {
        if (certPath != null) {
            return new PoolingHttpClientConnectionManager(getSslFactoryRegistry(certPath));
        }
        return new PoolingHttpClientConnectionManager();
    }
    
    private Registry<ConnectionSocketFactory> getSslFactoryRegistry(String certPath) throws IOException {
        try
        {
            KeyStore keyStore = KeyStoreUtil.createDockerKeyStore(certPath);

            SSLContext sslContext =
                    SSLContexts.custom()
                            .useTLS()
                            .loadKeyMaterial(keyStore, "docker".toCharArray())
                            .loadTrustMaterial(keyStore)
                            .build();
            SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(sslContext);
            return RegistryBuilder.<ConnectionSocketFactory> create().register("https", sslsf).build();
        }
        catch (GeneralSecurityException e) {
            // this isn't ideal but the net effect is the same
            throw new IOException(e);
        }
    }

    private HttpUriRequest newDelete(String url) {
        return addDefaultHeaders(new HttpDelete(url));
    }

    private HttpUriRequest newGet(String url) {
        return addDefaultHeaders(new HttpGet(url));
    }

    private HttpUriRequest newPost(String url, Object body) {
        HttpPost post = new HttpPost(url);
        
        if (body != null) {
            if (body instanceof File) {
                post.setEntity(new FileEntity((File) body));
            } else {
                post.setEntity(new StringEntity((String) body, Charset.defaultCharset()));
            }
        }
        return addDefaultHeaders(post);
    }

    private Result parseResponse(HttpResponse response, int successCode, int... additional) throws IOException, HttpRequestException {
        HttpEntity entity = response.getEntity();
        String message = (entity == null) ? null : EntityUtils.toString(response.getEntity()).trim();

        StatusLine statusLine = response.getStatusLine();
        int statusCode = statusLine.getStatusCode();

        if (statusCode == successCode) {
            return new Result(statusCode, message);
        }

        for (int code : additional) {
            if (statusCode == code) {
                return new Result(code, message);
            }
        }

        throw new HttpRequestException(String.format("%s (%s: %d)", message, statusLine.getReasonPhrase().trim(), statusCode));
    }

    public static class Result
    {
        public final int code;
        public final String response;
        
        public Result(int code, String response)
        {
            this.code = code;
            this.response = response;
        }
    }
}
