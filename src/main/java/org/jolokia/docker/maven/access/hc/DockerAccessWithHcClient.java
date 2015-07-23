package org.jolokia.docker.maven.access.hc;

import static java.net.HttpURLConnection.HTTP_CREATED;
import static java.net.HttpURLConnection.HTTP_NOT_FOUND;
import static java.net.HttpURLConnection.HTTP_NOT_MODIFIED;
import static java.net.HttpURLConnection.HTTP_NO_CONTENT;
import static java.net.HttpURLConnection.HTTP_OK;
import static org.jolokia.docker.maven.access.hc.ApacheHttpClientDelegate.BodyAndStatusResponseHandler;
import static org.jolokia.docker.maven.access.hc.ApacheHttpClientDelegate.BodyResponseHandler;
import static org.jolokia.docker.maven.access.hc.ApacheHttpClientDelegate.HttpBodyAndStatus;
import static org.jolokia.docker.maven.access.hc.ApacheHttpClientDelegate.StatusCodeResponseHandler;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.http.HttpResponse;
import org.apache.http.client.ResponseHandler;
import org.jolokia.docker.maven.access.AuthConfig;
import org.jolokia.docker.maven.access.ContainerCreateConfig;
import org.jolokia.docker.maven.access.DockerAccess;
import org.jolokia.docker.maven.access.DockerAccessException;
import org.jolokia.docker.maven.access.UrlBuilder;
import org.jolokia.docker.maven.access.chunked.BuildResponseHandler;
import org.jolokia.docker.maven.access.chunked.ChunkedResponseHandler;
import org.jolokia.docker.maven.access.chunked.ChunkedResponseReader;
import org.jolokia.docker.maven.access.chunked.PullOrPushResponseHandler;
import org.jolokia.docker.maven.access.chunked.TextToJsonBridgeCallback;
import org.jolokia.docker.maven.access.hc.http.HttpClientBuilder;
import org.jolokia.docker.maven.access.hc.unix.UnixSocketClientBuilder;
import org.jolokia.docker.maven.access.log.LogCallback;
import org.jolokia.docker.maven.access.log.LogGetHandle;
import org.jolokia.docker.maven.access.log.LogRequestor;
import org.jolokia.docker.maven.model.Container;
import org.jolokia.docker.maven.model.ContainerDetails;
import org.jolokia.docker.maven.model.ContainersListElement;
import org.jolokia.docker.maven.util.ImageName;
import org.jolokia.docker.maven.util.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Implementation using <a href="http://hc.apache.org/">Apache HttpComponents</a> for accessing
 * remotely the docker host.
 *
 * The design goal here is to provide only the functionality required for this plugin in order to
 * make it as robust as possible agains docker API changes (which happen quite frequently). That's
 * also the reason, why no framework like JAX-RS or docker-java is used so that the dependencies are
 * kept low.
 *
 * Of course, it's a bit more manual work, but it's worth the effort (as long as the Docker API
 * functionality required is not to much).
 *
 * @author roland
 * @since 26.03.14
 */
public class DockerAccessWithHcClient implements DockerAccess {

  // Base URL which is given through when using UnixSocket communication but is not really used
  private static final String DUMMY_BASE_URL = "unix://127.0.0.1:1/";

  // Logging
  private final Logger log;

  private final ApacheHttpClientDelegate delegate;
  private final UrlBuilder urlBuilder;

  /**
   * Create a new access for the given URL
   *
   * @param baseUrl  base URL for accessing the docker Daemon
   * @param certPath used to build up a keystore with the given keys and certificates found in this
   *                 directory
   * @param log      a log handler for printing out logging information
   */
  public DockerAccessWithHcClient(String apiVersion, String baseUrl, String certPath, Logger log)
      throws IOException {
    this.log = log;
    URI uri = URI.create(baseUrl);
    if (uri.getScheme().equalsIgnoreCase("unix")) {
      this.delegate =
          new ApacheHttpClientDelegate(new UnixSocketClientBuilder().build(uri.getPath()));
      this.urlBuilder = new UrlBuilder(DUMMY_BASE_URL, apiVersion);
    } else {
      this.delegate =
          new ApacheHttpClientDelegate(
              new HttpClientBuilder(isSSL(baseUrl) ? certPath : null).build());
      this.urlBuilder = new UrlBuilder(baseUrl, apiVersion);
    }
  }

  @Override
  public String createContainer(ContainerCreateConfig containerConfig, String containerName)
      throws DockerAccessException {
    String createJson = containerConfig.toJson();
    log.debug("Container create config: " + createJson);

    try {
      String url = urlBuilder.createContainer(containerName);
      String response =
          delegate.post(url, createJson, new BodyResponseHandler(), HTTP_CREATED);
      JSONObject json = new JSONObject(response);
      logWarnings(json);

      // only need first 12 to id a container
      return json.getString("Id").substring(0, 12);
    } catch (IOException e) {
      log.error(e.getMessage());
      throw new DockerAccessException("Unable to create container for [%s]",
                                      containerConfig.getImageName());
    }
  }

  @Override
  public void startContainer(String containerId) throws DockerAccessException {
    try {
      String url = urlBuilder.startContainer(containerId);
      delegate.post(url, null, HTTP_NO_CONTENT, HTTP_OK);
    } catch (IOException e) {
      log.error(e.getMessage());
      throw new DockerAccessException("Unable to start container id [%s]", containerId);
    }
  }

  @Override
  public void stopContainer(String containerId) throws DockerAccessException {
    try {
      String url = urlBuilder.stopContainer(containerId);
      delegate.post(url, HTTP_NO_CONTENT, HTTP_NOT_MODIFIED);
    } catch (IOException e) {
      log.error(e.getMessage());
      throw new DockerAccessException("Unable to stop container id [%s]", containerId);
    }
  }

  @Override
  public void buildImage(String image, File dockerArchive, boolean forceRemove)
      throws DockerAccessException {
    try {
      String url = urlBuilder.buildImage(image, forceRemove);
      delegate.post(url, dockerArchive, createBuildResponseHandler(), HTTP_OK);
    } catch (IOException e) {
      log.error(e.getMessage());
      throw new DockerAccessException("Unable to build image [%s]", image);
    }
  }

  @Override
  public Map<String, Integer> queryContainerPortMapping(String containerId)
      throws DockerAccessException {
    try {
      String url = urlBuilder.inspectContainer(containerId);
      String response = delegate.get(url, HTTP_OK);
      JSONObject json = new JSONObject(response);

      return extractPorts(json);
    } catch (IOException e) {
      throw new DockerAccessException("Unable to query port mappings for container [%s]",
                                      containerId);
    }
  }

  @Override
  public void getLogSync(String containerId, LogCallback callback) {
    LogRequestor
        extractor =
        new LogRequestor(delegate.getHttpClient(), urlBuilder, containerId, callback);
    extractor.fetchLogs();
  }

  @Override
  public LogGetHandle getLogAsync(String containerId, LogCallback callback) {
    LogRequestor
        extractor =
        new LogRequestor(delegate.getHttpClient(), urlBuilder, containerId, callback);
    extractor.start();
    return extractor;
  }

  @Override
  public Container inspectContainer(String containerId) throws DockerAccessException {
    try {
      String url = urlBuilder.inspectContainer(containerId);
      String response = delegate.get(url, HTTP_OK);
      return new ContainerDetails(new JSONObject(response));
    } catch (IOException e) {
      log.error(e.getMessage());
      throw new DockerAccessException("Unable to retrieve container name for [%s]", containerId);
    }
  }

  @Override
  public List<Container> listContainers(int limit) throws DockerAccessException {
    String url = urlBuilder.listContainers(limit);

    try {
      String response = delegate.get(url, HTTP_OK);
      JSONArray array = new JSONArray(response);
      List<Container> containers = new ArrayList<>(array.length());

      for (int i = 0; i < array.length(); i++) {
        containers.add(new ContainersListElement(array.getJSONObject(i)));
      }

      return containers;
    } catch (IOException e) {
      throw new DockerAccessException(e.getMessage());
    }
  }

  public boolean hasImage(String name) throws DockerAccessException {
    String url = urlBuilder.inspectImage(name);
    try {
      return delegate.get(url, new StatusCodeResponseHandler(), HTTP_OK, HTTP_NOT_FOUND) == HTTP_OK;
    } catch (IOException e) {
      log.error(e.getMessage());
      throw new DockerAccessException("Unable to check image [%s]", name);
    }
  }

  @Override
  public String getImageId(String name) throws DockerAccessException {
    HttpBodyAndStatus response = inspectImage(name);
    if (response.getStatusCode() == HTTP_NOT_FOUND) {
      return null;
    }
    JSONObject imageDetails = new JSONObject(response.getBody());
    return imageDetails.getString("Id");
  }

  private HttpBodyAndStatus inspectImage(String name) throws DockerAccessException {
    String url = urlBuilder.inspectImage(name);
    try {
      return delegate.get(url, new BodyAndStatusResponseHandler(), HTTP_OK, HTTP_NOT_FOUND);
    } catch (IOException e) {
      log.error(e.getMessage());
      throw new DockerAccessException("Unable to inspect image [%s]", name);
    }
  }

  @Override
  public void removeContainer(String containerId, boolean removeVolumes)
      throws DockerAccessException {
    try {
      String url = urlBuilder.removeContainer(containerId, removeVolumes);
      delegate.delete(url, HTTP_NO_CONTENT);
    } catch (IOException e) {
      log.error(e.getMessage());
      throw new DockerAccessException("Unable to remove container [%s]", containerId);
    }
  }

  @Override
  public void pullImage(String image, AuthConfig authConfig, String registry)
      throws DockerAccessException {
    ImageName name = new ImageName(image);
    String pullUrl = urlBuilder.pullImage(name, registry);

    try {
      delegate.post(pullUrl, null, createAuthHeader(authConfig),
                    createPullOrPushResponseHandler(), HTTP_OK);
    } catch (IOException e) {
      log.error(e.getMessage());
      throw new DockerAccessException("Unable to pull \"" + image + "\"" +
                                      (registry != null ? " from registry \"" + registry + "\""
                                                        : "") +
                                      " : " + e);
    }
  }

  @Override
  public void pushImage(String image, AuthConfig authConfig, String registry)
      throws DockerAccessException {
    ImageName name = new ImageName(image);
    String pushUrl = urlBuilder.pushImage(name, registry);
    String temporaryImage = tagTemporaryImage(name, registry);
    try {
      delegate.post(pushUrl, null, createAuthHeader(authConfig),
                    createPullOrPushResponseHandler(), HTTP_OK);
    } catch (IOException e) {
      log.error(e.getMessage());
      throw new DockerAccessException(e, "Unable to push \"" + image + "\"" +
                                         (registry != null ? " to registry \"" + registry + "\""
                                                           : "") +
                                         " : " + e);
    } finally {
      if (temporaryImage != null) {
        removeImage(temporaryImage);
      }
    }
  }

  @Override
  public void tag(String sourceImage, String targetImage, boolean force)
      throws DockerAccessException {
    ImageName source = new ImageName(sourceImage);
    ImageName target = new ImageName(targetImage);
    try {
      String url = urlBuilder.tagContainer(source, target, force);
      delegate.post(url, null, HTTP_CREATED);
    } catch (IOException e) {
      log.error(e.getMessage());
      throw new DockerAccessException("Unable to add tag [%s] to image [%s]", targetImage,
                                      sourceImage);
    }
  }

  @Override
  public boolean removeImage(String image, boolean... forceOpt) throws DockerAccessException {
    boolean force = forceOpt != null && forceOpt.length > 0 && forceOpt[0];
    try {
      String url = urlBuilder.deleteImage(image, force);
      HttpBodyAndStatus response = delegate.delete(url, new BodyAndStatusResponseHandler(),
                                                   HTTP_OK, HTTP_NOT_FOUND);
      if (log.isDebugEnabled()) {
        logRemoveResponse(new JSONArray(response.getBody()));
      }

      return response.getStatusCode() == HTTP_OK;
    } catch (IOException e) {
      log.error(e.getMessage());
      throw new DockerAccessException("Unable to remove image [%s]", image);
    }
  }

  // ---------------
  // Lifecycle methods not needed here
  @Override
  public void start() {
  }

  @Override
  public void shutdown() {
  }

  // visible for testing?
  private HcChunckedResponseHandlerWrapper createBuildResponseHandler() {
    return new HcChunckedResponseHandlerWrapper(log, new BuildResponseHandler(log));
  }

  private static class HcChunckedResponseHandlerWrapper implements ResponseHandler<Object> {

    private ChunkedResponseHandler<JSONObject> handler;
    private Logger log;

    public HcChunckedResponseHandlerWrapper(Logger log,
                                            ChunkedResponseHandler<JSONObject> handler) {
      this.log = log;
      this.handler = handler;
    }

    @Override
    public Object handleResponse(HttpResponse response) throws IOException {
      try (InputStream stream = response.getEntity().getContent()) {
        // Parse text as json
        new ChunkedResponseReader(stream, new TextToJsonBridgeCallback(log, handler)).process();
      }
      return null;
    }
  }

  // visible for testing?
  private HcChunckedResponseHandlerWrapper createPullOrPushResponseHandler() {
    return new HcChunckedResponseHandlerWrapper(log, new PullOrPushResponseHandler(log));
  }

  private Map<String, String> createAuthHeader(AuthConfig authConfig) {
    if (authConfig == null) {
      authConfig = AuthConfig.EMPTY_AUTH_CONFIG;
    }
    return Collections.singletonMap("X-Registry-Auth", authConfig.toHeaderValue());
  }

  private String tagTemporaryImage(ImageName name, String registry) throws DockerAccessException {
    String targetImage = name.getFullName(registry);
    if (!name.hasRegistry() && registry != null && !isDefaultRegistry(registry) && !hasImage(
        targetImage)) {
      tag(name.getFullName(null), targetImage, false);
      return targetImage;
    }
    return null;
  }

  private boolean isDefaultRegistry(String registry) {
    return "index.docker.io".equalsIgnoreCase(registry) ||
           "docker.io".equalsIgnoreCase(registry) ||
           "registry.hub.docker.com".equalsIgnoreCase(registry);
  }

  // ===========================================================================================================
  // Preparation for performing requests

  private Map<String, Integer> extractPorts(JSONObject info) {
    JSONObject networkSettings = info.getJSONObject("NetworkSettings");
    if (networkSettings != null) {
      JSONObject ports = networkSettings.getJSONObject("Ports");
      if (ports != null) {
        return createPortMapping(ports);
      }
    }
    return Collections.emptyMap();
  }

  private Map<String, Integer> createPortMapping(JSONObject ports) {
    Map<String, Integer> portMapping = new HashMap<>();
    for (Object portSpecO : ports.keySet()) {
      String portSpec = portSpecO.toString();
      if (!ports.isNull(portSpec)) {
        JSONArray hostSpecs = ports.getJSONArray(portSpec);
        parseHostSpecsAndUpdateMapping(portMapping, hostSpecs, portSpec);
      }
    }
    return portMapping;
  }

  private void parseHostSpecsAndUpdateMapping(Map<String, Integer> portMapping, JSONArray hostSpecs,
                                              String portSpec) {
    if (hostSpecs != null && hostSpecs.length() > 0) {
      // We take only the first
      JSONObject hostSpec = hostSpecs.getJSONObject(0);
      Object hostPortO = hostSpec.get("HostPort");
      if (hostPortO != null) {
        parsePortSpecAndUpdateMapping(portMapping, hostPortO, portSpec);
      }
    }
  }

  private void parsePortSpecAndUpdateMapping(Map<String, Integer> portMapping, Object hostPort,
                                             String portSpec) {
    try {
      Integer hostP = (Integer.parseInt(hostPort.toString()));
      int idx = portSpec.indexOf('/');
      if (idx > 0) {
        int port = Integer.parseInt(portSpec.substring(0, idx));
        String prot = portSpec.substring(idx + 1);
        if (!prot.equals("tcp") && !prot.equals("udp")) {
          prot = "tcp";
          log.warn("Invalid protocol '" + prot + "' in port spec " + portSpec + ". Assuming tcp");
        }
        portMapping.put(port + "/" + prot, hostP);
      } else {
        portMapping.put(Integer.parseInt(portSpec) + "/tcp", hostP);
      }
    } catch (NumberFormatException exp) {
      log.warn("Cannot parse " + hostPort + " or " + portSpec
               + " as a port number. Ignoring in mapping");
    }
  }

  private void logWarnings(JSONObject body) {
    Object warningsObj = body.get("Warnings");
    if (warningsObj != JSONObject.NULL) {
      JSONArray warnings = (JSONArray) warningsObj;
      for (int i = 0; i < warnings.length(); i++) {
        log.warn(warnings.getString(i));
      }
    }
  }

  // Callback for processing response chunks
  private void logRemoveResponse(JSONArray logElements) {
    for (int i = 0; i < logElements.length(); i++) {
      JSONObject entry = logElements.getJSONObject(i);
      for (Object key : entry.keySet()) {
        log.debug(key + ": " + entry.get(key.toString()));
      }
    }
  }

  private boolean isSSL(String url) {
    return url != null && url.toLowerCase().startsWith("https");
  }

}
