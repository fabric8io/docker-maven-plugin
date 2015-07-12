package org.jolokia.docker.maven.access.hc;

import java.io.*;
import java.net.URI;
import java.util.*;

import org.jolokia.docker.maven.access.*;
import org.jolokia.docker.maven.access.UrlBuilder.DockerUrl;
import org.jolokia.docker.maven.access.chunked.*;
import org.jolokia.docker.maven.access.hc.http.*;
import org.jolokia.docker.maven.access.hc.unix.UnixSocketClientBuilder;
import org.jolokia.docker.maven.access.hc.ApacheHttpClientDelegate.Result;
import org.jolokia.docker.maven.access.log.*;
import org.jolokia.docker.maven.model.*;
import org.jolokia.docker.maven.util.ImageName;
import org.jolokia.docker.maven.util.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import static java.net.HttpURLConnection.*;

/**
 * Implementation using <a href="http://hc.apache.org/">Apache HttpComponents</a> for accessing remotely
 * the docker host.
 *
 * The design goal here is to provide only the functionality required for this plugin in order to make
 * it as robust as possible agains docker API changes (which happen quite frequently). That's also
 * the reason, why no framework like JAX-RS or docker-java is used so that the dependencies are kept low.
 *
 * Of course, it's a bit more manual work, but it's worth the effort (as long as the Docker API functionality
 * required is not to much).
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
     * @param baseUrl base URL for accessing the docker Daemon
     * @param certPath used to build up a keystore with the given keys and certificates found in this directory
     * @param log a log handler for printing out logging information
     */
    public DockerAccessWithHcClient(String apiVersion, String baseUrl, String certPath, Logger log) throws IOException {
        this.log = log;
        URI uri = URI.create(baseUrl);
        if (uri.getScheme().equalsIgnoreCase("unix")) {
            this.delegate = new ApacheHttpClientDelegate(new UnixSocketClientBuilder().build(uri.getPath()));
            this.urlBuilder = new UrlBuilder(DUMMY_BASE_URL,apiVersion);
        } else {
            this.delegate = new ApacheHttpClientDelegate(new HttpClientBuilder(isSSL(baseUrl) ? certPath : null).build());
            this.urlBuilder = new UrlBuilder(baseUrl, apiVersion);
        }
    }

    @Override
    public String createContainer(ContainerCreateConfig containerConfig, String containerName) throws DockerAccessException {
        String createJson = containerConfig.toJson();
        log.debug("Container create config: " + createJson);

        try {
            String response = post(urlBuilder.createContainer(containerName), createJson, HTTP_CREATED).getMessage();
            JSONObject json = new JSONObject(response);
            logWarnings(json);

            // only need first 12 to id a container
            return json.getString("Id").substring(0, 12);
        }
        catch (HttpRequestException e) {
            log.error(e.getMessage());
            throw new DockerAccessException("Unable to create container for [%s]", containerConfig.getImageName());
        }
    }

    @Override
    public void startContainer(String containerId) throws DockerAccessException {
        try {
            post(urlBuilder.startContainer(containerId), null, HTTP_NO_CONTENT);
        } catch (HttpRequestException e) {
            log.error(e.getMessage());
            throw new DockerAccessException(String.format("Unable to start container id [%s]", containerId));
        }
    }

    @Override
    public void stopContainer(String containerId) throws DockerAccessException {
        try {
            post(urlBuilder.stopContainer(containerId), null, HTTP_NO_CONTENT, HTTP_NOT_MODIFIED);
        } catch (HttpRequestException e) {
            log.error(e.getMessage());
            throw new DockerAccessException(String.format("Unable to stop container id [%s]", containerId));
        }
    }

    @Override
    public void buildImage(String image, File dockerArchive) throws DockerAccessException {
        // auto-pull not supported in v1.15, which is currently the default
        String buildUrl = urlBuilder.buildImage(image, false, false);

        try {
            Result result = post(buildUrl, dockerArchive, HTTP_OK);
            processChunkedResponse(result, createBuildResponseHandler());
        } catch (HttpRequestException e) {
            log.error(e.getMessage());
            throw new DockerAccessException(String.format("Unable to build image [%s]", image));
        }
    }

    @Override
    public Map<String, Integer> queryContainerPortMapping(String containerId) throws DockerAccessException {
        try {
            String response = get(urlBuilder.inspectContainer(containerId), HTTP_OK).getMessage();
            JSONObject json = new JSONObject(response);

            return extractPorts(json);
        } catch (HttpRequestException e) {
            throw new DockerAccessException("Unable to query port mappings for container [%s]", containerId);
        }
    }

    @Override
    public void getLogSync(String containerId, LogCallback callback) {
        LogRequestor extractor = new LogRequestor(delegate.getHttpClient(), urlBuilder, containerId, callback);
        extractor.fetchLogs();
    }

    @Override
    public LogGetHandle getLogAsync(String containerId, LogCallback callback) {
        LogRequestor extractor = new LogRequestor(delegate.getHttpClient(), urlBuilder, containerId, callback);
        extractor.start();
        return extractor;
    }
    
    @Override
    public ContainerDetails inspectContainer(String containerId) throws DockerAccessException {
        try {
            String response = get(urlBuilder.inspectContainer(containerId), HTTP_OK).getMessage();
            return new ContainerDetails(response);
        } catch (HttpRequestException e) {
            log.error(e.getMessage());
            throw new DockerAccessException("Unable to retrieve container name for [%s]", containerId);
        }
    } 
    
    @Override
    public List<Container> listContainers(ListArg... args) throws DockerAccessException {
        DockerUrl url = buildDockerUrl(urlBuilder.listContainers(), args);

        try {
            String response = get(url, HTTP_OK).getMessage();
            JSONArray array = new JSONArray(response);
            List<Container> containers = new ArrayList<>(array.length());
            
            for (int i = 0; i < array.length(); i++) {
                containers.add(new Container(array.getJSONObject(i)));
            }
            
            return containers;
        }
        catch (HttpRequestException e) {
            throw new DockerAccessException(e.getMessage());
        }
    }
    
    @Override
    public List<Image> listImages(ListArg... args) throws DockerAccessException {
        DockerUrl url = buildDockerUrl(urlBuilder.listImages(), args);

        try {
            String response = get(url, HTTP_OK).getMessage();
            JSONArray array = new JSONArray(response);
            List<Image> images = new ArrayList<>(array.length());
        
            for (int i = 0; i < array.length(); i++) {
                images.add(new Image(array.getJSONObject(i)));
            }
            
            return images;
        }
        catch (HttpRequestException e) {
            log.error(e.getMessage());
            throw new DockerAccessException("Unable to list images");
        }
    }    

    @Override
    public void removeContainer(String containerId, boolean removeVolumes) throws DockerAccessException {
        try {
            delete(urlBuilder.removeContainer(containerId, removeVolumes), HTTP_NO_CONTENT);
        } catch (HttpRequestException e) {
            log.error(e.getMessage());
            throw new DockerAccessException("Unable to remove container [%s]", containerId);
        }
    }

    @Override
    public void pullImage(String image, AuthConfig authConfig, String registry) throws DockerAccessException {
        ImageName name = new ImageName(image);
        String pullUrl = urlBuilder.pullImage(name, registry);

        try {
            Result result = post(pullUrl, null, authConfig, HTTP_OK);
            processChunkedResponse(result, createPullOrPushResponseHandler());
        } catch (HttpRequestException e) {
            log.error(e.getMessage());
            throw new DockerAccessException("Unable to pull \"" + image + "\"" +
                                              (registry != null ? " from registry \"" + registry + "\"" : "") +
                                              " : " + e);
        }
    }

    @Override
    public void pushImage(String image, AuthConfig authConfig, String registry) throws DockerAccessException {
        ImageName name = new ImageName(image);
        String pushUrl = urlBuilder.pushImage(name, registry);
        String temporaryImage = tagTemporaryImage(name, registry);
        try {
            Result result = post(pushUrl, null, authConfig, HTTP_OK);
            processChunkedResponse(result, createPullOrPushResponseHandler());
        } catch (HttpRequestException e) {
            log.error(e.getMessage());
            throw new DockerAccessException(e,"Unable to push \"" + image + "\"" +
                                              (registry != null ? " to registry \"" + registry + "\"" : "") +
                                              " : " + e);
        } finally {
            if (temporaryImage != null) {
                removeImage(temporaryImage);
            }
        }
    }

    @Override
    public void tag(String sourceImage, String targetImage, boolean force) throws DockerAccessException {
        ImageName source = new ImageName(sourceImage);
        ImageName target = new ImageName(targetImage);
        try {
            post(urlBuilder.tagContainer(source, target, force), null, HTTP_CREATED);
        } catch (HttpRequestException e) {
            log.error(e.getMessage());
            throw new DockerAccessException("Unable to add tag [%s] to image [%s]", targetImage, sourceImage);
        }
    }

    @Override
    public boolean removeImage(String image, boolean ... forceOpt) throws DockerAccessException {
        boolean force = forceOpt != null && forceOpt.length > 0 && forceOpt[0];
        try {
            Result result = delete(urlBuilder.deleteImage(image, force), HTTP_OK, HTTP_NOT_FOUND);
            if (log.isDebugEnabled()) {
                logRemoveResponse(new JSONArray(result.getMessage()));
            }

            return result.getCode() == HTTP_OK;
        } catch (HttpRequestException e) {
            log.error(e.getMessage());
            throw new DockerAccessException("Unable to remove image [%s]", image);
        }
    }

    // ---------------
    // Lifecycle methods not needed here
    @Override
    public void start() {}

    @Override
    public void shutdown() {}

    // visible for testing?
    private BuildResponseHandler createBuildResponseHandler() {
        return new BuildResponseHandler(log);
    }

    // visible for testing?
    private PullOrPushResponseHandler createPullOrPushResponseHandler() {
        return new PullOrPushResponseHandler(log);
    }

    private Map<String, String> createAuthHeader(AuthConfig authConfig) {
        if (authConfig == null) {
            authConfig = AuthConfig.EMPTY_AUTH_CONFIG;
        }
        return Collections.singletonMap("X-Registry-Auth", authConfig.toHeaderValue());
    }

    private String tagTemporaryImage(ImageName name, String registry) throws DockerAccessException {
        String targetImage = name.getFullName(registry);
        if (!name.hasRegistry() && registry != null && !isDefaultRegistry(registry) && !hasImage(targetImage)) {
            tag(name.getFullName(null), targetImage,false);
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

    private Result delete(String url, int statusCode, int... additional) throws HttpRequestException, DockerAccessException {
        try {
            return delegate.delete(url, statusCode, additional);
        } catch (IOException e) {
            throw new DockerAccessException(e, "Communication error with the docker daemon");
        }
    }

    private Result get(DockerUrl url, int statusCode, int... additional) throws HttpRequestException, DockerAccessException {
        try {
            return delegate.get(url.toString(), statusCode, additional);
        } catch (IOException e) {
            throw new DockerAccessException(e, "Communication error with the docker daemon");
        }
    }

    private Result post(String url, Object body, AuthConfig authConfig, int statusCode) throws HttpRequestException, DockerAccessException {
       try {
           return delegate.post(url, body, createAuthHeader(authConfig), statusCode);
       } catch (IOException e) {
           throw new DockerAccessException(e, "communication error occurred with the docker daemon");
       }
    }

    private Result post(String url, Object body, int statusCode, int... additional) throws HttpRequestException, DockerAccessException {
        try {
            return delegate.post(url, body, statusCode, additional);
        } catch (IOException e) {
            throw new DockerAccessException(e, "communication error occurred with the docker daemon");
        }
    }

    // ===========================================================================================================
    // Preparation for performing requests

    private DockerUrl buildDockerUrl(DockerUrl url, ListArg... args) {
        for (ListArg arg : args) {
            url.addQueryParam(arg.getKey(), arg.getValue());
        }

        return url;
    }
    
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

    private void parseHostSpecsAndUpdateMapping(Map<String, Integer> portMapping, JSONArray hostSpecs, String portSpec) {
        if (hostSpecs != null && hostSpecs.length() > 0) {
            // We take only the first
            JSONObject hostSpec = hostSpecs.getJSONObject(0);
            Object hostPortO = hostSpec.get("HostPort");
            if (hostPortO != null) {
                parsePortSpecAndUpdateMapping(portMapping, hostPortO, portSpec);
            }
        }
    }

    private void parsePortSpecAndUpdateMapping(Map<String, Integer> portMapping, Object hostPort, String portSpec) {
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
            log.warn("Cannot parse " + hostPort + " or " + portSpec + " as a port number. Ignoring in mapping");
        }
    }

    private void processChunkedResponse(Result result, ChunkedResponseHandler<JSONObject> handler) throws DockerAccessException {
        try (InputStream stream = result.getInputStream()) {
            // Parse text as json
            new ChunkedResponseReader(stream, new TextToJsonBridgeCallback(log, handler)).process();
        }
        catch (IOException e) {
            throw new DockerAccessException(e, "Cannot process chunk response: " + e);
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

    private boolean isDockerIndexIo(String registry) {
        return (registry != null && registry.equals("index.docker.io"));
    }

    private boolean isSSL(String url) {
        return url != null && url.toLowerCase().startsWith("https");
    } 
    
    private boolean hasImage(String image) throws DockerAccessException {
        return !listImages(ListArg.filter(image)).isEmpty();
    }
}
