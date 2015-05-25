package org.jolokia.docker.maven.access;

import java.io.*;
import java.util.*;

import org.jolokia.docker.maven.access.chunked.*;
import org.jolokia.docker.maven.access.http.ApacheHttpDelegate;
import org.jolokia.docker.maven.access.http.ApacheHttpDelegate.Result;
import org.jolokia.docker.maven.access.http.HttpRequestException;
import org.jolokia.docker.maven.access.log.*;
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
public class DockerAccessWithHttpClient implements DockerAccess {

    // Logging
    private final Logger log;

    private final ApacheHttpDelegate delegate;
    private final UrlBuilder urlBuilder;

    /**
     * Create a new access for the given URL
     * @param baseUrl base URL for accessing the docker Daemon
     * @param certPath used to build up a keystore with the given keys and certificates found in this directory
     * @param log a log handler for printing out logging information
     */
    public DockerAccessWithHttpClient(String apiVersion, String baseUrl, String certPath, Logger log) throws IOException {
        this.log = log;
        this.delegate = ApacheHttpDelegate.create(baseUrl, isSSL(baseUrl) ? certPath : null);
        this.urlBuilder = delegate.createUrlBuilder(baseUrl, apiVersion);
    }

    @Override
    public boolean hasImage(String image) throws DockerAccessException {
        ImageName name = new ImageName(image);
        try {
            String response = get(urlBuilder.listImages(name), HTTP_OK).getMessage();
            JSONArray array = new JSONArray(response);

            return containsImage(name, array);
        } catch (HttpRequestException e) {
            log.error(e.getMessage());
            throw new DockerAccessException("Unable to check for image [%s]", image);
        }
    }

    @Override
    public String getImageId(String image) throws DockerAccessException {
        try {
            String response = get(urlBuilder.inspectImage(image), HTTP_OK).getMessage();
            JSONObject json = new JSONObject(response);
            return json.getString("Id");
        } catch (HttpRequestException e) {
            log.error(e.getMessage());
            throw new DockerAccessException("Unable to find Id for image [%s]", image);
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
    public String getContainerName(String containerId) throws DockerAccessException {
        try {
            String response = get(urlBuilder.inspectContainer(containerId), HTTP_OK).getMessage();
            JSONObject json = new JSONObject(response);

            String name =  json.getString("Name");
            if (name.startsWith("/")) {
                name = name.substring(1);
            }

            return name;
        } catch (HttpRequestException e) {
            log.error(e.getMessage());
            throw new DockerAccessException("Unable to retrieve container name for [%s]", containerId);
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
    public List<String> getContainersForImage(String image) throws DockerAccessException {
        return getContainerIds(image, false);
    }

    @Override
    public String getNewestImageForContainer(String image) throws DockerAccessException {
        List<String> newestContainer = getContainerIds(image, true);
        assert newestContainer.size() == 0 || newestContainer.size() == 1;
        return newestContainer.size() == 0 ? null : newestContainer.get(0);
    }

    @Override
    public boolean isContainerRunning(String containerId) throws DockerAccessException {
        try {
            String response = get(urlBuilder.inspectContainer(containerId), HTTP_OK).getMessage();
            JSONObject json = new JSONObject(response);

            return json.getJSONObject("State").getBoolean("Running");
        } catch (HttpRequestException e) {
            log.error(e.getMessage());
            throw new DockerAccessException("Unable to determine state of container [%s]", containerId);
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
            throw new DockerAccessException("Unable to pull image [%s] from registry [%s]", image, registry);
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
            throw new DockerAccessException(e,"Unable to push image [%s] to registry [%s] : %s", image, registry, e.getMessage());
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

    private boolean containsImage(ImageName name, JSONArray array) {
        if (array.length() > 0) {
            for (int i = 0; i < array.length(); i++) {
                JSONObject imageObject = array.getJSONObject(i);
                JSONArray repoTags = imageObject.getJSONArray("RepoTags");
                for (int j = 0; j < repoTags.length(); j++) {
                    if (name.getFullName().equals(repoTags.getString(j))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private List<String> extractMatchingContainers(boolean onlyLatest, String imageFullName, JSONArray configs) {
        long newest = 0;
        List<String> ret = new ArrayList<>();
        for (int i = 0; i < configs.length(); i ++) {
            JSONObject config = configs.getJSONObject(i);

            String id = config.getString("Id");
            String containerImage = config.getString("Image");

            if (!imageFullName.equals(containerImage)) {
                continue;
            }

            if (!onlyLatest) {
                ret.add(id);
                continue;
            }

            int timestamp = config.getInt("Created");
            if (timestamp > newest) {
                newest = timestamp;
                if (ret.size() == 0) {
                    ret.add(id);
                } else {
                    ret.set(0, id);
                }
            }
        }
        return ret;
    }

    private List<String> getContainerIds(String image, boolean onlyLatest) throws DockerAccessException {
        ImageName imageName = new ImageName(image);
        String imageFullName = imageName.getFullName();

        try {
            String response = get(urlBuilder.listContainers(100), HTTP_OK).getMessage();
            JSONArray array = new JSONArray(response);

            return extractMatchingContainers(onlyLatest, imageFullName, array);
        } catch (HttpRequestException e) {
            throw new DockerAccessException(e.getMessage());
        }
    }

    private String tagTemporaryImage(ImageName name, String registry) throws DockerAccessException {
        String targetImage = name.getFullName(registry);

        if (!name.hasRegistry() && registry != null && !registry.equals("index.docker.io") && !hasImage(targetImage)) {
            tag(name.getFullName(null), targetImage,false);
            return targetImage;
        }
        return null;
    }


    // ===========================================================================================================

    private Result delete(String url, int statusCode, int... additional) throws HttpRequestException, DockerAccessException {
        try {
            return delegate.delete(url, statusCode, additional);
        } catch (IOException e) {
            throw new DockerAccessException(e, "Communication error with the docker daemon");
        }
    }

    private Result get(String url, int statusCode, int... additional) throws HttpRequestException, DockerAccessException {
        try {
            return delegate.get(url, statusCode, additional);
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

    private boolean isSSL(String url) {
        return url != null && url.toLowerCase().startsWith("https");
    }
}
