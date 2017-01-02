package io.fabric8.maven.docker.access.hc;

import static java.net.HttpURLConnection.HTTP_CREATED;
import static java.net.HttpURLConnection.HTTP_INTERNAL_ERROR;
import static java.net.HttpURLConnection.HTTP_NOT_FOUND;
import static java.net.HttpURLConnection.HTTP_NOT_MODIFIED;
import static java.net.HttpURLConnection.HTTP_NO_CONTENT;
import static java.net.HttpURLConnection.HTTP_OK;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import io.fabric8.maven.docker.access.hc.util.ClientBuilder;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.json.JSONArray;
import org.json.JSONObject;

import io.fabric8.maven.docker.access.AuthConfig;
import io.fabric8.maven.docker.access.ContainerCreateConfig;
import io.fabric8.maven.docker.access.DockerAccess;
import io.fabric8.maven.docker.access.DockerAccessException;
import io.fabric8.maven.docker.access.NetworkCreateConfig;
import io.fabric8.maven.docker.access.UrlBuilder;
import io.fabric8.maven.docker.access.VolumeCreateConfig;
import io.fabric8.maven.docker.access.chunked.BuildJsonResponseHandler;
import io.fabric8.maven.docker.access.chunked.EntityStreamReaderUtil;
import io.fabric8.maven.docker.access.chunked.PullOrPushResponseJsonHandler;
import io.fabric8.maven.docker.access.hc.ApacheHttpClientDelegate.BodyAndStatusResponseHandler;
import io.fabric8.maven.docker.access.hc.ApacheHttpClientDelegate.HttpBodyAndStatus;
import io.fabric8.maven.docker.access.hc.http.HttpClientBuilder;
import io.fabric8.maven.docker.access.hc.unix.UnixSocketClientBuilder;
import io.fabric8.maven.docker.access.hc.win.NamedPipeClientBuilder;
import io.fabric8.maven.docker.access.log.LogCallback;
import io.fabric8.maven.docker.access.log.LogGetHandle;
import io.fabric8.maven.docker.access.log.LogRequestor;
import io.fabric8.maven.docker.config.Arguments;
import io.fabric8.maven.docker.log.DefaultLogCallback;
import io.fabric8.maven.docker.log.LogOutputSpec;
import io.fabric8.maven.docker.model.Container;
import io.fabric8.maven.docker.model.ContainerDetails;
import io.fabric8.maven.docker.model.ContainersListElement;
import io.fabric8.maven.docker.model.Network;
import io.fabric8.maven.docker.model.NetworksListElement;
import io.fabric8.maven.docker.util.EnvUtil;
import io.fabric8.maven.docker.util.ImageName;
import io.fabric8.maven.docker.util.Logger;
import io.fabric8.maven.docker.util.Timestamp;

/**
 * Implementation using <a href="http://hc.apache.org/">Apache HttpComponents</a>
 * for remotely accessing the docker host.
 * <p/>
 * The design goal here is to provide only the functionality required for this plugin in order to
 * make it as robust as possible against docker API changes (which happen quite frequently). That's
 * also the reason, why no framework like JAX-RS or docker-java is used so that the dependencies are
 * kept low.
 * <p/>
 * Of course, it's a bit more manual work, but it's worth the effort
 * (as long as the Docker API functionality required is not too much).
 *
 * @author roland
 * @since 26.03.14
 */
public class DockerAccessWithHcClient implements DockerAccess {

    // Base URL which is given through when using UnixSocket communication but is not really used
    private static final String UNIX_URL = "unix://127.0.0.1:1/";

    // Base URL which is given through when using NamedPipe communication but is not really used
    private static final String NPIPE_URL = "npipe://127.0.0.1:1/";

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
     * @param maxConnections maximum parallel connections allowed to docker daemon (if a pool is used)
     * @param log      a log handler for printing out logging information
     * @paran usePool  whether to use a connection bool or not
     */
    public DockerAccessWithHcClient(String apiVersion,
                                    String baseUrl,
                                    String certPath,
                                    int maxConnections,
                                    Logger log) throws IOException {
        this.log = log;
        URI uri = URI.create(baseUrl);
        if (uri.getScheme() == null) {
            throw new IllegalArgumentException("The docker access url '" + baseUrl + "' must contain a schema tcp://, unix:// or npipe://");
        }
        if (uri.getScheme().equalsIgnoreCase("unix")) {
            this.delegate = createHttpClient(new UnixSocketClientBuilder(uri.getPath(), maxConnections, log));
            this.urlBuilder = new UrlBuilder(UNIX_URL, apiVersion);
        } else if (uri.getScheme().equalsIgnoreCase("npipe")) {
        	this.delegate = createHttpClient(new NamedPipeClientBuilder(uri.getPath(), maxConnections, log), false);
            this.urlBuilder = new UrlBuilder(NPIPE_URL, apiVersion);
        } else {
            this.delegate = createHttpClient(new HttpClientBuilder(isSSL(baseUrl) ? certPath : null, maxConnections));
            this.urlBuilder = new UrlBuilder(baseUrl, apiVersion);
        }
    }

    /** {@inheritDoc} */
    @Override
    public String getServerApiVersion() throws DockerAccessException {
        try {
            String url = urlBuilder.version();
            String response = delegate.get(url, 200);
            JSONObject info = new JSONObject(response);
            return info.getString("ApiVersion");
        } catch (Exception e) {
            throw new DockerAccessException(e, "Cannot extract API version from server %s", urlBuilder.getBaseUrl());
        }
    }

    @Override
    public void startExecContainer(String containerId, LogOutputSpec outputSpec) throws DockerAccessException {
        try {
            String url = urlBuilder.startExecContainer(containerId);
            JSONObject request = new JSONObject();
            request.put("Detach", false);
            request.put("Tty", true);

            delegate.post(url, request.toString(), createExecResponseHandler(outputSpec), HTTP_OK);
        } catch (Exception e) {
            throw new DockerAccessException(e, "Unable to start container id [%s]", containerId);
        }
    }

    private ResponseHandler<Object> createExecResponseHandler(LogOutputSpec outputSpec) throws FileNotFoundException {
        final LogCallback callback = new DefaultLogCallback(outputSpec);
        return new ResponseHandler<Object>() {
            @Override
            public Object handleResponse(HttpResponse response) throws IOException {
                try (InputStream stream = response.getEntity().getContent()) {
                    LineNumberReader reader = new LineNumberReader(new InputStreamReader(stream));
                    String line;
                    try {
                        while ( (line = reader.readLine()) != null) {
                            callback.log(1, new Timestamp(), line);
                        }
                    } catch (LogCallback.DoneException e) {
                        // Ok, we stop here ...
                    }
                }
                return null;
            }
        };
    }

    @Override
    public String createExecContainer(String containerId, Arguments arguments) throws DockerAccessException {
        String url = urlBuilder.createExecContainer(containerId);
        JSONObject request = new JSONObject();
        request.put("Tty", true);
        request.put("AttachStdin", false);
        request.put("AttachStdout", true);
        request.put("AttachStderr", true);
        request.put("Cmd", arguments.getExec());

        String execJsonRequest = request.toString();
        try {
            String response = delegate.post(url, execJsonRequest, new ApacheHttpClientDelegate.BodyResponseHandler(), HTTP_CREATED);
            JSONObject json = new JSONObject(response);
            if (json.has("Warnings")) {
                logWarnings(json);
            }

            return json.getString("Id");
        } catch (IOException e) {
            throw new DockerAccessException(e, "Unable to exec [%s] on container [%s]", request.toString(),
                                            containerId);
        }

    }

    @Override
    public String createContainer(ContainerCreateConfig containerConfig, String containerName)
            throws DockerAccessException {
        String createJson = containerConfig.toJson();
        log.debug("Container create config: %s", createJson);

        try {
            String url = urlBuilder.createContainer(containerName);
            String response =
                    delegate.post(url, createJson, new ApacheHttpClientDelegate.BodyResponseHandler(), HTTP_CREATED);
            JSONObject json = new JSONObject(response);
            logWarnings(json);

            // only need first 12 to id a container
            return json.getString("Id").substring(0, 12);
        } catch (IOException e) {
            throw new DockerAccessException(e, "Unable to create container for [%s]",
                                            containerConfig.getImageName());
        }
    }

    @Override
    public void startContainer(String containerId) throws DockerAccessException {
        try {
            String url = urlBuilder.startContainer(containerId);
            delegate.post(url, HTTP_NO_CONTENT, HTTP_OK);
        } catch (IOException e) {
            throw new DockerAccessException(e, "Unable to start container id [%s]", containerId);
        }
    }

    @Override
    public void stopContainer(String containerId, int killWait) throws DockerAccessException {
        try {
            String url = urlBuilder.stopContainer(containerId, killWait);
            delegate.post(url, HTTP_NO_CONTENT, HTTP_NOT_MODIFIED);
        } catch (IOException e) {
            throw new DockerAccessException(e, "Unable to stop container id [%s]", containerId);
        }
    }

    @Override
    public void buildImage(String image, File dockerArchive, String dockerfileName, boolean forceRemove, boolean noCache,
            Map<String, String> buildArgs) throws DockerAccessException {
        try {
            String url = urlBuilder.buildImage(image, dockerfileName, forceRemove, noCache, buildArgs);
            delegate.post(url, dockerArchive, createBuildResponseHandler(), HTTP_OK);
        } catch (IOException e) {
            throw new DockerAccessException(e, "Unable to build image [%s]", image);
        }
    }

    @Override
    public void copyArchive(String containerId, File archive, String targetPath)
            throws DockerAccessException {
        try {
            String url = urlBuilder.copyArchive(containerId, targetPath);
            delegate.put(url, archive, HTTP_OK);
        } catch (IOException e) {
            throw new DockerAccessException(e, "Unable to copy archive %s to container [%s] with path %s",
                                            archive.toPath(), containerId, targetPath);
        }
    }

    @Override
    public void getLogSync(String containerId, LogCallback callback) {
        LogRequestor extractor = new LogRequestor(delegate.getHttpClient(), urlBuilder, containerId, callback);
        extractor.fetchLogs();
    }

    @Override
    public LogGetHandle getLogAsync(String containerId, LogCallback callback) {
        LogRequestor extractor = new LogRequestor(delegate.createBasicClient(), urlBuilder, containerId, callback);
        extractor.start();
        return extractor;
    }

    @Override
    public List<Container> getContainersForImage(String image) throws DockerAccessException {
        String url;
        String serverApiVersion = getServerApiVersion();
        if (EnvUtil.greaterOrEqualsVersion(serverApiVersion, "1.23")) {
            // For Docker >= 1.11 we can use a new filter when listing containers
            url = urlBuilder.listContainers("ancestor",image);
        } else {
            // For older versions (< Docker 1.11) we need to iterate over the containers.
            url = urlBuilder.listContainers();
        }

        try {
            String response = delegate.get(url, HTTP_OK);
            JSONArray array = new JSONArray(response);
            List<Container> containers = new ArrayList<>();

            for (int i = 0; i < array.length(); i++) {
                JSONObject element = array.getJSONObject(i);
                if (image.equals(element.getString("Image"))) {
                    containers.add(new ContainersListElement(element));
                }
            }
            return containers;
        } catch (IOException e) {
            throw new DockerAccessException(e.getMessage());
        }
    }

    @Override
    public Container getContainer(String containerIdOrName) throws DockerAccessException {
        HttpBodyAndStatus response = inspectContainer(containerIdOrName);
        if (response.getStatusCode() == HTTP_NOT_FOUND) {
            return null;
        } else {
            return new ContainerDetails(new JSONObject(response.getBody()));
        }
    }

    private HttpBodyAndStatus inspectContainer(String containerIdOrName) throws DockerAccessException {
        try {
            String url = urlBuilder.inspectContainer(containerIdOrName);
            return delegate.get(url, new BodyAndStatusResponseHandler(), HTTP_OK, HTTP_NOT_FOUND);
        } catch (IOException e) {
            throw new DockerAccessException(e, "Unable to retrieve container name for [%s]", containerIdOrName);
        }
    }

    @Override
    public boolean hasImage(String name) throws DockerAccessException {
        String url = urlBuilder.inspectImage(name);
        try {
            return delegate.get(url, new ApacheHttpClientDelegate.StatusCodeResponseHandler(), HTTP_OK, HTTP_NOT_FOUND) == HTTP_OK;
        } catch (IOException e) {
            throw new DockerAccessException(e, "Unable to check image [%s]", name);
        }
    }

    @Override
    public String getImageId(String name) throws DockerAccessException {
        HttpBodyAndStatus response = inspectImage(name);
        if (response.getStatusCode() == HTTP_NOT_FOUND) {
            return null;
        }
        JSONObject imageDetails = new JSONObject(response.getBody());
        return imageDetails.getString("Id").substring(0, 12);
    }

    private HttpBodyAndStatus inspectImage(String name) throws DockerAccessException {
        String url = urlBuilder.inspectImage(name);
        try {
            return delegate.get(url, new BodyAndStatusResponseHandler(), HTTP_OK, HTTP_NOT_FOUND);
        } catch (IOException e) {
            throw new DockerAccessException(e, "Unable to inspect image [%s]", name);
        }
    }

    @Override
    public void removeContainer(String containerId, boolean removeVolumes)
            throws DockerAccessException {
        try {
            String url = urlBuilder.removeContainer(containerId, removeVolumes);
            delegate.delete(url, HTTP_NO_CONTENT);
        } catch (IOException e) {
            throw new DockerAccessException(e, "Unable to remove container [%s]", containerId);
        }
    }

    @Override
    public void loadImage(String image, File tarArchive) throws DockerAccessException {
        String url = urlBuilder.loadImage();

        try {
            delegate.post(url, tarArchive, new BodyAndStatusResponseHandler(), HTTP_OK);
        } catch (IOException e) {
            throw new DockerAccessException(e, "Unable to load %s", tarArchive);
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
            throw new DockerAccessException(e, "Unable to pull '%s'%s", image, (registry != null) ? " from registry '" + registry + "'" : "");
        }
    }

    @Override
    public void pushImage(String image, AuthConfig authConfig, String registry, int retries)
            throws DockerAccessException {
        ImageName name = new ImageName(image);
        String pushUrl = urlBuilder.pushImage(name, registry);
        String temporaryImage = tagTemporaryImage(name, registry);
        try {
            doPushImage(pushUrl, createAuthHeader(authConfig), createPullOrPushResponseHandler(), HTTP_OK, retries);
        } catch (IOException e) {
            throw new DockerAccessException(e, "Unable to push '%s'%s", image, (registry != null) ? " from registry '" + registry + "'" : "");
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
            delegate.post(url, HTTP_CREATED);
        } catch (IOException e) {
            throw new DockerAccessException(e, "Unable to add tag [%s] to image [%s]", targetImage,
                    sourceImage, e);
        }
    }

    @Override
    public boolean removeImage(String image, boolean... forceOpt) throws DockerAccessException {
        boolean force = forceOpt != null && forceOpt.length > 0 && forceOpt[0];
        try {
            String url = urlBuilder.deleteImage(image, force);
            HttpBodyAndStatus response = delegate.delete(url, new BodyAndStatusResponseHandler(), HTTP_OK, HTTP_NOT_FOUND);
            if (log.isDebugEnabled()) {
                logRemoveResponse(new JSONArray(response.getBody()));
            }

            return response.getStatusCode() == HTTP_OK;
        } catch (IOException e) {
            throw new DockerAccessException(e, "Unable to remove image [%s]", image);
        }
    }

    @Override
    public List<Network> listNetworks() throws DockerAccessException {
        String url = urlBuilder.listNetworks();

        try {
            String response = delegate.get(url, HTTP_OK);
            JSONArray array = new JSONArray(response);
            List<Network> networks = new ArrayList<>(array.length());

            for (int i = 0; i < array.length(); i++) {
                networks.add(new NetworksListElement(array.getJSONObject(i)));
            }

            return networks;
        } catch (IOException e) {
            throw new DockerAccessException(e.getMessage());
        }
    }

    @Override
    public String createNetwork(NetworkCreateConfig networkConfig)
            throws DockerAccessException {
        String createJson = networkConfig.toJson();
        log.debug("Network create config: " + createJson);
        try {
            String url = urlBuilder.createNetwork();
            String response =
                    delegate.post(url, createJson, new ApacheHttpClientDelegate.BodyResponseHandler(), HTTP_CREATED);
            log.debug(response);
            JSONObject json = new JSONObject(response);
            if (json.has("Warnings")) {
                logWarnings(json);
            }

            // only need first 12 to id a container
            return json.getString("Id").substring(0, 12);
        } catch (IOException e) {
            throw new DockerAccessException(e, "Unable to create network for [%s]",
                    networkConfig.getName());
        }
    }

    @Override
    public boolean removeNetwork(String networkId)
            throws DockerAccessException {
        try {
            String url = urlBuilder.removeNetwork(networkId);
            int status = delegate.delete(url, HTTP_OK, HTTP_NO_CONTENT, HTTP_NOT_FOUND);
            return status == HTTP_OK || status == HTTP_NO_CONTENT;
        } catch (IOException e) {
            throw new DockerAccessException(e, "Unable to remove network [%s]", networkId);
        }
    }

    @Override
    public String createVolume(VolumeCreateConfig containerConfig)
           throws DockerAccessException
    {
        String createJson = containerConfig.toJson();
        log.debug("Volume create config: %s", createJson);

        try
        {
            String url = urlBuilder.createVolume();
            String response =
                    delegate.post(url,
                                  createJson,
                                  new ApacheHttpClientDelegate.BodyResponseHandler(),
                                  HTTP_CREATED);
            JSONObject json = new JSONObject(response);
            logWarnings(json);

            return json.getString("Name");
        }
        catch (IOException e)
        {
           throw new DockerAccessException(e, "Unable to create volume for [%s]",
                                           containerConfig.getName());
        }
    }

    @Override
    public void removeVolume(String name) throws DockerAccessException {
        try {
            String url = urlBuilder.removeVolume(name);
            delegate.delete(url, HTTP_NO_CONTENT);
        } catch (IOException e) {
            throw new DockerAccessException(e, "Unable to remove volume [%s]", name);
        }
    }


    // ---------------
    // Lifecycle methods not needed here
    @Override
    public void start() {
    }

    @Override
    public void shutdown() {
        try {
            delegate.close();
        } catch (IOException exp) {
            log.error("Error while closing HTTP client: " + exp,exp);
        }
    }

    ApacheHttpClientDelegate createHttpClient(ClientBuilder builder) throws IOException {
    	return createHttpClient(builder, true);
    }

    ApacheHttpClientDelegate createHttpClient(ClientBuilder builder, boolean pooled) throws IOException {
        return new ApacheHttpClientDelegate(builder, pooled);
    }

    // visible for testing?
    private HcChunkedResponseHandlerWrapper createBuildResponseHandler() {
        return new HcChunkedResponseHandlerWrapper(new BuildJsonResponseHandler(log));
    }

    // visible for testing?
    private HcChunkedResponseHandlerWrapper createPullOrPushResponseHandler() {
        return new HcChunkedResponseHandlerWrapper(new PullOrPushResponseJsonHandler(log));
    }

    private Map<String, String> createAuthHeader(AuthConfig authConfig) {
        if (authConfig == null) {
            authConfig = AuthConfig.EMPTY_AUTH_CONFIG;
        }
        return Collections.singletonMap("X-Registry-Auth", authConfig.toHeaderValue());
    }

    private boolean isRetryableErrorCode(int errorCode) {
        // there eventually could be more then one of this
        return errorCode == HTTP_INTERNAL_ERROR;
    }

    private void doPushImage(String url, Map<String, String> header, HcChunkedResponseHandlerWrapper handler, int status,
                             int retries) throws IOException {
        // 0: The original attemp, 1..retry: possible retries.
        for (int i = 0; i <= retries; i++) {
            try {
                delegate.post(url, null, header, handler, HTTP_OK);
                return;
            } catch (HttpResponseException e) {
                if (isRetryableErrorCode(e.getStatusCode()) && i != retries) {
                    log.warn("failed to push image to [{}], retrying...", url);
                } else {
                    throw e;
                }
            }
        }
    }

    private String tagTemporaryImage(ImageName name, String registry) throws DockerAccessException {
        String targetImage = name.getFullName(registry);
        if (!name.hasRegistry() && registry != null) {
            if (hasImage(targetImage)) {
                throw new DockerAccessException(
                    String.format("Cannot temporarily tag %s with %s because target image already exists. " +
                                  "Please remove this and retry.",
                                  name.getFullName(), targetImage));
            }
            tag(name.getFullName(), targetImage, false);
            return targetImage;
        }
        return null;
    }

    // ===========================================================================================================

    private void logWarnings(JSONObject body) {
        if (body.has("Warnings")) {
            Object warningsObj = body.get("Warnings");
            if (warningsObj != JSONObject.NULL) {
                JSONArray warnings = (JSONArray) warningsObj;
                for (int i = 0; i < warnings.length(); i++) {
                    log.warn(warnings.getString(i));
                }
            }
        }
    }

    // Callback for processing response chunks
    private void logRemoveResponse(JSONArray logElements) {
        for (int i = 0; i < logElements.length(); i++) {
            JSONObject entry = logElements.getJSONObject(i);
            for (Object key : entry.keySet()) {
                log.debug("%s: %s", key, entry.get(key.toString()));
            }
        }
    }

    private boolean isSSL(String url) {
        return url != null && url.toLowerCase().startsWith("https");
    }

    // Preparation for performing requests
    private static class HcChunkedResponseHandlerWrapper implements ResponseHandler<Object> {

        private EntityStreamReaderUtil.JsonEntityResponseHandler handler;

        HcChunkedResponseHandlerWrapper(EntityStreamReaderUtil.JsonEntityResponseHandler handler) {
            this.handler = handler;
        }

        @Override
        public Object handleResponse(HttpResponse response) throws IOException {
            try (InputStream stream = response.getEntity().getContent()) {
                // Parse text as json
                EntityStreamReaderUtil.processJsonStream(handler, stream);
            }
            return null;
        }
    }
}
