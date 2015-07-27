package org.jolokia.docker.maven.access.hc;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.Map.Entry;

import com.google.common.net.MediaType;
import org.apache.http.*;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.*;
import org.apache.http.entity.FileEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.jolokia.docker.maven.access.hc.http.HttpRequestException;

public class ApacheHttpClientDelegate {

  private final CloseableHttpClient httpClient;

  public ApacheHttpClientDelegate(CloseableHttpClient httpClient) {
    this.httpClient = httpClient;
  }

  public int delete(String url, int... statusCodes) throws IOException {
    return delete(url, new StatusCodeResponseHandler(), statusCodes);
  }

  public static class StatusCodeResponseHandler implements ResponseHandler<Integer> {

    @Override
    public Integer handleResponse(HttpResponse response)
        throws IOException {
      return response.getStatusLine().getStatusCode();
    }
  }

  public <T> T delete(String url, ResponseHandler<T> responseHandler, int... statusCodes)
      throws IOException {
    return httpClient.execute(newDelete(url),
                              new StatusCodeCheckerResponseHandler<>(responseHandler,
                                                                     statusCodes));
  }

  public String get(String url, int... statusCodes) throws IOException {
    return httpClient.execute(newGet(url), new StatusCodeCheckerResponseHandler<>(
        new BodyResponseHandler(), statusCodes));
  }

  public <T> T get(String url, ResponseHandler<T> responseHandler, int... statusCodes)
      throws IOException {
    return httpClient
        .execute(newGet(url), new StatusCodeCheckerResponseHandler<>(responseHandler, statusCodes));
  }

  public static class BodyResponseHandler implements ResponseHandler<String> {

    @Override
    public String handleResponse(HttpResponse response)
        throws IOException {
      return getResponseMessage(response);
    }
  }

  private static String getResponseMessage(HttpResponse response) throws IOException {
    return (response.getEntity() == null) ? null
                                          : EntityUtils.toString(response.getEntity()).trim();
  }

  public <T> T post(String url, Object body, Map<String, String> headers,
                    ResponseHandler<T> responseHandler, int... statusCodes) throws IOException {
    HttpUriRequest request = newPost(url, body);
    for (Entry<String, String> entry : headers.entrySet()) {
      request.addHeader(entry.getKey(), entry.getValue());
    }

    return httpClient.execute(request, new StatusCodeCheckerResponseHandler<>(responseHandler,
                                                                              statusCodes));
  }

  public <T> T post(String url, Object body, ResponseHandler<T> responseHandler,
                    int... statusCodes) throws IOException {
    return httpClient.execute(newPost(url, body),
                              new StatusCodeCheckerResponseHandler<>(responseHandler,
                                                                     statusCodes));
  }

  public int post(String url, int... statusCodes) throws IOException {
    return post(url, null, new StatusCodeResponseHandler(), statusCodes);
  }

  public CloseableHttpClient getHttpClient() {
    return httpClient;
  }

  // =========================================================================================

  private HttpUriRequest addDefaultHeaders(HttpUriRequest req) {
    req.addHeader(HttpHeaders.ACCEPT, "*/*");
    req.addHeader(HttpHeaders.CONTENT_TYPE, MediaType.JSON_UTF_8.toString());
    return req;
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

  public static class StatusCodeCheckerResponseHandler<T> implements ResponseHandler<T> {

    private int[] statusCodes;
    private ResponseHandler<T> delegate;

    public StatusCodeCheckerResponseHandler(ResponseHandler<T> delegate, int... statusCodes) {
      this.statusCodes = statusCodes;
      this.delegate = delegate;
    }

    @Override
    public T handleResponse(HttpResponse response) throws IOException {
      StatusLine statusLine = response.getStatusLine();
      int statusCode = statusLine.getStatusCode();
      for (int code : statusCodes) {
        if (statusCode == code) {
          return delegate.handleResponse(response);
        }
      }

      String reason = statusLine.getReasonPhrase().trim();
      throw new HttpRequestException(String.format("%s (%s: %d)", getResponseMessage(response),
                                                   reason, statusCode));
    }

  }

  public static class BodyAndStatusResponseHandler implements ResponseHandler<HttpBodyAndStatus> {

    @Override
    public HttpBodyAndStatus handleResponse(HttpResponse response)
        throws IOException {
      return new HttpBodyAndStatus(response.getStatusLine().getStatusCode(),
                                   getResponseMessage(response));
    }
  }

  public static class HttpBodyAndStatus {

    private final int statusCode;
    private final String body;

    public HttpBodyAndStatus(int statusCode, String body) {
      this.statusCode = statusCode;
      this.body = body;
    }

    public int getStatusCode() {
      return statusCode;
    }

    public String getBody() {
      return body;
    }
  }
}
