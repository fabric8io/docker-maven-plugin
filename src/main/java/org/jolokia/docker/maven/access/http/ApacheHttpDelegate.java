package org.jolokia.docker.maven.access.http;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.Map;
import java.util.Map.Entry;

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

    public Result post(String url, Object body, Map<String, String> headers, int statusCode, int... additional) throws IOException,
        HttpRequestException {

        HttpUriRequest request = newPost(url, body);
        for (Entry<String, String> entry : headers.entrySet()) {
            request.addHeader(entry.getKey(), entry.getValue());
        }

        return parseResponse(httpClient.execute(request), statusCode, additional);
    }

    public Result post(String url, Object body, int statusCode, int... additional) throws IOException, HttpRequestException {
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
            }
            else {
                post.setEntity(new StringEntity((String) body, Charset.defaultCharset()));
            }
        }
        return addDefaultHeaders(post);
    }

    private Result parseResponse(HttpResponse response, int successCode, int... additional) throws HttpRequestException {
        HttpEntity entity = response.getEntity();

        StatusLine statusLine = response.getStatusLine();
        int statusCode = statusLine.getStatusCode();
        String reason = statusLine.getReasonPhrase().trim();

        if (statusCode == successCode) {
            return new Result(statusCode, entity);
        }

        for (int code : additional) {
            if (statusCode == code) {
                return new Result(code, entity);
            }
        }

        Result result = new Result(statusCode, entity);
        throw new HttpRequestException(String.format("%s (%s: %d)", result.getMessage(), reason, statusCode));
    }

    public static class Result
    {
        private final int code;
        private final HttpEntity entity;

        public Result(int code, HttpEntity entity)
        {
            this.code = code;
            this.entity = entity;
        }

        public int getCode() {
            return code;
        }

        public InputStream getInputStream() throws IOException {
            return entity.getContent();
        }

        public String getMessage() {
            try {
                return (entity == null) ? null : EntityUtils.toString(entity).trim();
            }
            catch (IOException e) {
                return "Unknown error - failed to read response content";
            }
        }
    }
}
