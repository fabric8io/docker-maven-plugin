package org.jolokia.docker.maven.access.hc;

import java.io.*;
import java.net.URI;
import java.util.*;

import org.apache.http.HttpResponse;
import org.apache.http.client.ResponseHandler;
import org.jolokia.docker.maven.access.*;
import org.jolokia.docker.maven.access.chunked.*;
import org.jolokia.docker.maven.access.hc.http.HttpClientBuilder;
import org.jolokia.docker.maven.access.hc.unix.UnixSocketClientBuilder;
import org.jolokia.docker.maven.access.log.*;
import org.jolokia.docker.maven.config.Arguments;
import org.jolokia.docker.maven.log.DefaultLogCallback;
import org.jolokia.docker.maven.log.LogOutputSpec;
import org.jolokia.docker.maven.model.*;
import org.jolokia.docker.maven.util.*;
import org.json.JSONArray;
import org.json.JSONObject;

import static java.net.HttpURLConnection.*;
import static org.jolokia.docker.maven.access.hc.ApacheHttpClientDelegate.*;

/**
 * Implementation using <a href="http://hc.apache.org/">Apache HttpComponents</a> for accessing
 * remotely the docker host.
 * <p/>
 * The design goal here is to provide only the functionality required for this plugin in order to
 * make it as robust as possible agains docker API changes (which happen quite frequently). That's
 * also the reason, why no framework like JAX-RS or docker-java is used so that the dependencies are
 * kept low.
 * <p/>
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
     * @param maxConnections maximum parallel connections allowed to docker daemon (if a pool is used)
     * @param log      a log handler for printing out logging information
     * @paran usePool  whether to use a connection bool or not
     */
    public DockerAccessWithHcClient(String apiVersion, String baseUrl, String certPath, int maxConnections, Logger log)
            throws IOException {
        this.log = log;
        URI uri = URI.create(baseUrl);
        if (uri.getScheme() == null) {
            throw new IllegalArgumentException("The docker access url '" + baseUrl + "' must contain a schema tcp:// or unix://");
        }
        if (uri.getScheme().equalsIgnoreCase("unix")) {
            this.delegate =
                    new ApacheHttpClientDelegate(new UnixSocketClientBuilder().build(uri.getPath(), maxConnections));
            this.urlBuilder = new UrlBuilder(DUMMY_BASE_URL, apiVersion);
        } else {
            HttpClientBuilder builder = new HttpClientBuilder();
            if (isSSL(baseUrl)) {
                builder.certPath(certPath);
            }
            builder.maxConnections(maxConnections);
            this.delegate = new ApacheHttpClientDelegate(builder.build());
            this.urlBuilder = new UrlBuilder(baseUrl, apiVersion);
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
            log.error(e.getMessage());
            throw new DockerAccessException("Unable to start container id [%s]", containerId);
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
            String response = delegate.post(url, execJsonRequest, new BodyResponseHandler(), HTTP_CREATED);
            JSONObject json = new JSONObject(response);
            if (json.has("Warnings")) {
                logWarnings(json);
            }

            return json.getString("Id");
        } catch (IOException e) {
            log.error(e.getMessage());
            throw new DockerAccessException("Unable to exec [%s] on container [%s]", request.toString(),
                                            containerId);
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
            delegate.post(url, HTTP_NO_CONTENT, HTTP_OK);
        } catch (IOException e) {
            log.error(e.getMessage());
            throw new DockerAccessException("Unable to start container id [%s]", containerId);
        }
    }

    @Override
    public void stopContainer(String containerId, int killWait) throws DockerAccessException {
        try {
            String url = urlBuilder.stopContainer(containerId, killWait);
            delegate.post(url, HTTP_NO_CONTENT, HTTP_NOT_MODIFIED);
        } catch (IOException e) {
            log.error(e.getMessage());
            throw new DockerAccessException("Unable to stop container id [%s]", containerId);
        }
    }

    @Override
    public void buildImage(String image, File dockerArchive, boolean forceRemove, boolean noCache)
            throws DockerAccessException {
        try {
            String url = urlBuilder.buildImage(image, forceRemove, noCache);
            delegate.post(url, dockerArchive, createBuildResponseHandler(), HTTP_OK);
        } catch (IOException e) {
            log.error(e.getMessage());
            throw new DockerAccessException("Unable to build image [%s]", image);
        }
    }

    @Override
    public void copyArchive(String containerId,File archive, String targetPath)
            throws DockerAccessException {
        try {
            String url = urlBuilder.copyArchive(containerId, targetPath);
            delegate.put(url, archive, HTTP_OK);
        } catch (IOException e) {
            log.error(e.getMessage());
            throw new DockerAccessException("Unable to copy archive %s to container [%s] with path %s",
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
        LogRequestor extractor = new LogRequestor(delegate.getHttpClient(), urlBuilder, containerId, callback);
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

    @Override
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
        return imageDetails.getString("Id").substring(0, 12);
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
            throw new DockerAccessException("Unable to pull '%s'%s", image, (registry != null) ? " from registry '" + registry + "'" : "");
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
            throw new DockerAccessException("Unable to push '%s'%s", image, (registry != null) ? " from registry '" + registry + "'" : "");
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
            HttpBodyAndStatus response = delegate.delete(url, new BodyAndStatusResponseHandler(),HTTP_OK, HTTP_NOT_FOUND);
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
        return new HcChunckedResponseHandlerWrapper(log, new BuildJsonResponseHandler(log));
    }

    // visible for testing?
    private HcChunckedResponseHandlerWrapper createPullOrPushResponseHandler() {
        return new HcChunckedResponseHandlerWrapper(log, new PullOrPushResponseJsonHandler(log));
    }

    private Map<String, String> createAuthHeader(AuthConfig authConfig) {
        if (authConfig == null) {
            authConfig = AuthConfig.EMPTY_AUTH_CONFIG;
        }
        return Collections.singletonMap("X-Registry-Auth", authConfig.toHeaderValue());
    }

    private String tagTemporaryImage(ImageName name, String registry) throws DockerAccessException {
        String targetImage = name.getFullName(registry);
        if (!name.hasRegistry() && registry != null && !hasImage(targetImage)) {
            tag(name.getFullName(null), targetImage, false);
            return targetImage;
        }
        return null;
    }

    // ===========================================================================================================

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

    // Preparation for performing requests
    private static class HcChunckedResponseHandlerWrapper implements ResponseHandler<Object> {

        private EntityStreamReaderUtil.JsonEntityResponseHandler handler;
        private Logger log;

        public HcChunckedResponseHandlerWrapper(Logger log,
                                                EntityStreamReaderUtil.JsonEntityResponseHandler handler) {
            this.log = log;
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
