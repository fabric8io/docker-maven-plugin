package io.fabric8.maven.docker.access.hc;

import static java.net.HttpURLConnection.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.OutputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import io.fabric8.maven.docker.access.CreateImageOptions;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.ResponseHandler;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import io.fabric8.maven.docker.access.AuthConfig;
import io.fabric8.maven.docker.access.BuildOptions;
import io.fabric8.maven.docker.access.ContainerCreateConfig;
import io.fabric8.maven.docker.access.DockerAccess;
import io.fabric8.maven.docker.access.DockerAccessException;
import io.fabric8.maven.docker.access.NetworkCreateConfig;
import io.fabric8.maven.docker.access.UrlBuilder;
import io.fabric8.maven.docker.access.VolumeCreateConfig;
import io.fabric8.maven.docker.access.chunked.BuildJsonResponseHandler;
import io.fabric8.maven.docker.access.chunked.PullOrPushResponseJsonHandler;
import io.fabric8.maven.docker.access.hc.ApacheHttpClientDelegate.BodyAndStatusResponseHandler;
import io.fabric8.maven.docker.access.hc.ApacheHttpClientDelegate.HttpBodyAndStatus;
import io.fabric8.maven.docker.access.hc.http.HttpClientBuilder;
import io.fabric8.maven.docker.access.hc.unix.UnixSocketClientBuilder;
import io.fabric8.maven.docker.access.hc.util.ClientBuilder;
import io.fabric8.maven.docker.access.hc.win.NamedPipeClientBuilder;
import io.fabric8.maven.docker.access.log.LogCallback;
import io.fabric8.maven.docker.access.log.LogGetHandle;
import io.fabric8.maven.docker.access.log.LogRequestor;
import io.fabric8.maven.docker.config.ArchiveCompression;
import io.fabric8.maven.docker.config.Arguments;
import io.fabric8.maven.docker.log.DefaultLogCallback;
import io.fabric8.maven.docker.log.LogOutputSpec;
import io.fabric8.maven.docker.model.Container;
import io.fabric8.maven.docker.model.ContainerDetails;
import io.fabric8.maven.docker.model.ContainersListElement;
import io.fabric8.maven.docker.model.ExecDetails;
import io.fabric8.maven.docker.model.Image;
import io.fabric8.maven.docker.model.ImageDetails;
import io.fabric8.maven.docker.model.Network;
import io.fabric8.maven.docker.model.NetworksListElement;
import io.fabric8.maven.docker.util.EnvUtil;
import io.fabric8.maven.docker.util.ImageName;
import io.fabric8.maven.docker.util.JsonFactory;
import io.fabric8.maven.docker.util.Logger;
import io.fabric8.maven.docker.util.TimestampFactory;

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

    // Minimal API version, independent of any feature used
    public static final String API_VERSION = "1.18";

    // Copy buffer size when saving images or copying files from containers
    private static final int COPY_BUFFER_SIZE = 65536;

    private static final String API_LOG_FORMAT_GET = "GET %s";
    private static final String API_LOG_FORMAT_POST = "POST %s";
    private static final String API_LOG_FORMAT_DELETE = "DELETE %s";
    private static final String API_LOG_FORMAT_POST_WITH_REQUEST = "POST to %s with %s";
    private static final String API_LOG_FORMAT_POST_FILE = "POST to %s with contents of file %s";
    private static final String API_LOG_FORMAT_PUT_FILE = "PUT to %s with contents of file %s";

    // Logging
    private final Logger log;

    private final ApacheHttpClientDelegate delegate;
    private final String apiVersion;
    private final String nativePlatform;
    private final UrlBuilder urlBuilder;

    /**
     * Create a new access for the given URL
     *
     * @param baseUrl  base URL for accessing the docker Daemon
     * @param certPath used to build up a keystore with the given keys and certificates found in this
     *                 directory
     * @param maxConnections maximum parallel connections allowed to docker daemon (if a pool is used)
     * @param log      a log handler for printing out logging information
     */
    public DockerAccessWithHcClient(@Nonnull String baseUrl,
                                    String certPath,
                                    int maxConnections,
                                    Logger log) throws IOException {
        URI uri = URI.create(baseUrl);
        if (uri.getScheme() == null) {
            throw new IllegalArgumentException("The docker access url '" + baseUrl + "' must contain a schema tcp://, unix:// or npipe://");
        }
        if (uri.getScheme().equalsIgnoreCase("unix")) {
            this.delegate = createHttpClient(new UnixSocketClientBuilder(uri.getPath(), maxConnections, log));
            baseUrl = UNIX_URL;
        } else if (uri.getScheme().equalsIgnoreCase("npipe")) {
            this.delegate = createHttpClient(new NamedPipeClientBuilder(uri.getPath(), maxConnections, log), false);
            baseUrl = NPIPE_URL;
        } else {
            this.delegate = createHttpClient(new HttpClientBuilder(isSSL(baseUrl) ? certPath : null, maxConnections));
        }

        baseUrl = stripTrailingSlash(baseUrl);
        String url = baseUrl + "/version";
        log.verbose(Logger.LogVerboseCategory.API, API_LOG_FORMAT_GET, url);
        JsonObject info = JsonFactory.newJsonObject(delegate.get(url, HTTP_OK));
        this.apiVersion = info.get("ApiVersion").getAsString();
        this.nativePlatform = info.get("Os").getAsString() + "/" + info.get("Arch").getAsString();
        this.urlBuilder = new UrlBuilder(baseUrl, "v" + apiVersion);
        this.log = log;
    }

    static String stripTrailingSlash(String url) {
        int last = url.length();
        while (url.charAt(last - 1) == '/') {
            --last;
        }
        return url.substring(0, last);
    }

    /** {@inheritDoc} */
    @Override
    public String getServerApiVersion() {
        return apiVersion;
    }

    @Override
    public void startExecContainer(String containerId, LogOutputSpec outputSpec) throws DockerAccessException {
        String url = urlBuilder.startExecContainer(containerId);
        JsonObject request = new JsonObject();
        request.addProperty("Detach", false);
        request.addProperty("Tty", true);
        log.verbose(Logger.LogVerboseCategory.API, API_LOG_FORMAT_POST_WITH_REQUEST, url, request);
        try {
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
                        callback.open();
                        while ( (line = reader.readLine()) != null) {
                            callback.log(1, TimestampFactory.createTimestamp(), line);
                        }
                    } catch (LogCallback.DoneException e) {
                        // Ok, we stop here ...
                    } finally {
                        callback.close();
                    }
                }
                return null;
            }
        };
    }

    @Override
    public String createExecContainer(String containerId, Arguments arguments) throws DockerAccessException {
        String url = urlBuilder.createExecContainer(containerId);
        JsonObject request = new JsonObject();
        request.addProperty("Tty", true);
        request.addProperty("AttachStdin", false);
        request.addProperty("AttachStdout", true);
        request.addProperty("AttachStderr", true);
        request.add("Cmd", JsonFactory.newJsonArray(arguments.getExec()));

        String execJsonRequest = request.toString();
        log.verbose(Logger.LogVerboseCategory.API, API_LOG_FORMAT_POST_WITH_REQUEST, url, execJsonRequest);
        try {
            String response = delegate.post(url, execJsonRequest, new ApacheHttpClientDelegate.BodyResponseHandler(), HTTP_CREATED);
            JsonObject json = JsonFactory.newJsonObject(response);
            if (json.has("Warnings")) {
                logWarnings(json);
            }

            return json.get("Id").getAsString();
        } catch (IOException e) {
            throw new DockerAccessException(e, "Unable to exec [%s] on container [%s]", request.toString(),
                                            containerId);
        }

    }

    @Override
    public String createContainer(ContainerCreateConfig containerConfig, String containerName)
            throws DockerAccessException {
        String createJson = containerConfig.toJson();
        String url = urlBuilder.createContainer(containerName);
        log.verbose(Logger.LogVerboseCategory.API, API_LOG_FORMAT_POST_WITH_REQUEST, url, createJson);
        try {
            String response =
                    delegate.post(url, createJson, new ApacheHttpClientDelegate.BodyResponseHandler(), HTTP_CREATED);
            JsonObject json = JsonFactory.newJsonObject(response);
            logWarnings(json);

            // only need first 12 to id a container
            return json.get("Id").getAsString().substring(0, 12);
        } catch (IOException e) {
            throw new DockerAccessException(e, "Unable to create container for [%s]",
                                            containerConfig.getImageName());
        }
    }

    @Override
    public void startContainer(String containerId) throws DockerAccessException {
        String url = urlBuilder.startContainer(containerId);
        log.verbose(Logger.LogVerboseCategory.API, API_LOG_FORMAT_POST, url);
        try {
            delegate.post(url, HTTP_NO_CONTENT, HTTP_OK);
        } catch (IOException e) {
            throw new DockerAccessException(e, "Unable to start container id [%s]", containerId);
        }
    }

    @Override
    public void stopContainer(String containerId, int killWait) throws DockerAccessException {
        String url = urlBuilder.stopContainer(containerId, killWait);
        log.verbose(Logger.LogVerboseCategory.API, API_LOG_FORMAT_POST, url);
        try {
            delegate.post(url, HTTP_NO_CONTENT, HTTP_NOT_MODIFIED);
        } catch (IOException e) {
            throw new DockerAccessException(e, "Unable to stop container id [%s]", containerId);
        }
    }

    @Override
    public void killContainer(String containerId) throws DockerAccessException {
        String url = urlBuilder.killContainer(containerId);
        log.verbose(Logger.LogVerboseCategory.API, API_LOG_FORMAT_POST, url);
        try {
            delegate.post(url, HTTP_NO_CONTENT, HTTP_NOT_MODIFIED);
        } catch (IOException ie) {
            throw new DockerAccessException(ie, "Unable to kill container id [%s]", containerId);
        }
    }

    @Override
    public void buildImage(String image, File dockerArchive, BuildOptions options) throws DockerAccessException {
        String url = urlBuilder.buildImage(image, options);
        log.verbose(Logger.LogVerboseCategory.API, API_LOG_FORMAT_POST_FILE, url, dockerArchive);
        try {
            delegate.post(url, dockerArchive, createBuildResponseHandler(), HTTP_OK);
        } catch (IOException e) {
            throw new DockerAccessException(e, "Unable to build image [%s]", image);
        }
    }

    @Override
    public void copyArchiveToContainer(String containerId, File archive, String targetPath)
            throws DockerAccessException {
        String url = urlBuilder.copyArchive(containerId, targetPath);
        log.verbose(Logger.LogVerboseCategory.API, API_LOG_FORMAT_PUT_FILE, url, archive);
        try {
            delegate.put(url, archive, HTTP_OK);
        } catch (IOException e) {
            throw new DockerAccessException(e, "Unable to copy archive %s to container [%s] with path %s",
                                            archive.toPath(), containerId, targetPath);
        }
    }

    @Override
    public void copyArchiveFromContainer(String containerId, String containerPath, File archive)
            throws DockerAccessException {
        String url = urlBuilder.copyArchive(containerId, containerPath);
        log.verbose(Logger.LogVerboseCategory.API, API_LOG_FORMAT_GET, url);
        try {
            delegate.get(url, getContainerFileHandler(archive), HTTP_OK);
        } catch (IOException e) {
            throw new DockerAccessException(e, "Unable to copy archived path %s from container [%s] into file %s",
                                            containerPath, containerId, archive.toPath());
        }
    }

    private ResponseHandler<Object> getContainerFileHandler(final File file) {
        return new ResponseHandler<Object>() {
            @Override
            public Object handleResponse(HttpResponse response) throws IOException {
                try (InputStream stream = response.getEntity().getContent();
                    OutputStream out = new FileOutputStream(file)) {
                    IOUtils.copy(stream, out, COPY_BUFFER_SIZE);
                }
                return null;
            }
        };
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
    public List<Container> getContainersForImage(String image, boolean all) throws DockerAccessException {
        String url;
        String serverApiVersion = getServerApiVersion();
        if (EnvUtil.greaterOrEqualsVersion(serverApiVersion, "1.23")) {
            // For Docker >= 1.11 we can use a new filter when listing containers
            url = urlBuilder.listContainers(all, "ancestor",image);
        } else {
            // For older versions (< Docker 1.11) we need to iterate over the containers.
            url = urlBuilder.listContainers(all);
        }

        log.verbose(Logger.LogVerboseCategory.API, API_LOG_FORMAT_GET, url);
        try {
            String response = delegate.get(url, HTTP_OK);
            JsonArray array = JsonFactory.newJsonArray(response);
            List<Container> containers = new ArrayList<>();

            for (int i = 0; i < array.size(); i++) {
                JsonObject element = array.get(i).getAsJsonObject();
                if (image.equals(element.get("Image").getAsString())) {
                    containers.add(new ContainersListElement(element));
                }
            }
            return containers;
        } catch (IOException e) {
            throw new DockerAccessException(e.getMessage());
        }
    }

    @Override
    public List<Container> listContainers(boolean all) throws DockerAccessException {
        String url = urlBuilder.listContainers(all);
        log.verbose(Logger.LogVerboseCategory.API, API_LOG_FORMAT_GET, url);
        try {
            String response = delegate.get(url, HTTP_OK);
            JsonArray array = JsonFactory.newJsonArray(response);
            List<Container> containers = new ArrayList<>();

            for (JsonElement element : array) {
                containers.add(new ContainersListElement(element.getAsJsonObject()));
            }

            return containers;
        } catch (IOException e) {
            throw new DockerAccessException(e.getMessage());
        }
    }

    @Override
    public ContainerDetails getContainer(String containerIdOrName) throws DockerAccessException {
        HttpBodyAndStatus response = inspectContainer(containerIdOrName);
        if (response.getStatusCode() == HTTP_NOT_FOUND) {
            return null;
        } else {
            return new ContainerDetails(JsonFactory.newJsonObject(response.getBody()));
        }
    }

    @Override
    public ExecDetails getExecContainer(String containerIdOrName) throws DockerAccessException {
        HttpBodyAndStatus response = inspectExecContainer(containerIdOrName);
        if (response.getStatusCode() == HTTP_NOT_FOUND) {
            return null;
        } else {
            return new ExecDetails(JsonFactory.newJsonObject(response.getBody()));
        }
    }

    private HttpBodyAndStatus inspectContainer(String containerIdOrName) throws DockerAccessException {
        String url = urlBuilder.inspectContainer(containerIdOrName);
        log.verbose(Logger.LogVerboseCategory.API, API_LOG_FORMAT_GET, url);
        try {
            return delegate.get(url, new BodyAndStatusResponseHandler(), HTTP_OK, HTTP_NOT_FOUND);
        } catch (IOException e) {
            throw new DockerAccessException(e, "Unable to retrieve container name for [%s]", containerIdOrName);
        }
    }

    private HttpBodyAndStatus inspectExecContainer(String containerIdOrName) throws DockerAccessException {
        String url = urlBuilder.inspectExecContainer(containerIdOrName);
        log.verbose(Logger.LogVerboseCategory.API, API_LOG_FORMAT_GET, url);
        try {
            return delegate.get(url, new BodyAndStatusResponseHandler(), HTTP_OK, HTTP_NOT_FOUND);
        } catch (IOException e) {
            throw new DockerAccessException(e, "Unable to retrieve container name for [%s]", containerIdOrName);
        }
    }

    @Override
    public List<Image> listImages(boolean all) throws DockerAccessException {
        String url = urlBuilder.listImages(all);
        log.verbose(Logger.LogVerboseCategory.API, API_LOG_FORMAT_GET, url);
        try {
            String response = delegate.get(url, HTTP_OK);
            JsonArray array = JsonFactory.newJsonArray(response);
            List<Image> images = new ArrayList<>(array.size());

            for (int i = 0; i < array.size(); i++) {
                images.add(new ImageDetails(array.get(i).getAsJsonObject()));
            }

            return images;
        } catch(IOException e) {
            throw new DockerAccessException(e.getMessage());
        }
    }

    @Override
    public boolean hasImage(String name) throws DockerAccessException {
        String url = urlBuilder.inspectImage(name);
        log.verbose(Logger.LogVerboseCategory.API, API_LOG_FORMAT_GET, url);
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
        JsonObject imageDetails = JsonFactory.newJsonObject(response.getBody());
        return imageDetails.get("Id").getAsString().substring(0, 12);
    }

    @Override
    public List<String> getImageTags(String name) throws DockerAccessException {
        HttpBodyAndStatus response = inspectImage(name);
        if (response.getStatusCode() == HTTP_NOT_FOUND) {
            return null;
        }
        JsonObject imageDetails = JsonFactory.newJsonObject(response.getBody());
        JsonArray tagsArr = imageDetails.get("RepoTags").getAsJsonArray();
        if (tagsArr.size() == 0) {
            return Collections.emptyList();
        }
        List<String> tags = new ArrayList<>();
        tagsArr.forEach(tag-> tags.add(tag.getAsString()));
        return tags;
    }

    private HttpBodyAndStatus inspectImage(String name) throws DockerAccessException {
        String url = urlBuilder.inspectImage(name);
        log.verbose(Logger.LogVerboseCategory.API, API_LOG_FORMAT_GET, url);
        try {
            return delegate.get(url, new BodyAndStatusResponseHandler(), HTTP_OK, HTTP_NOT_FOUND);
        } catch (IOException e) {
            throw new DockerAccessException(e, "Unable to inspect image [%s]", name);
        }
    }

    @Override
    public void removeContainer(String containerId, boolean removeVolumes)
            throws DockerAccessException {
        String url = urlBuilder.removeContainer(containerId, removeVolumes);
        log.verbose(Logger.LogVerboseCategory.API, API_LOG_FORMAT_DELETE, url);
        try {
            delegate.delete(url, HTTP_NO_CONTENT);
        } catch (IOException e) {
            throw new DockerAccessException(e, "Unable to remove container [%s]", containerId);
        }
    }

    @Override
    public void loadImage(String image, File tarArchive) throws DockerAccessException {
        String url = urlBuilder.loadImage();
        log.verbose(Logger.LogVerboseCategory.API, API_LOG_FORMAT_POST_FILE, url, tarArchive);
        try {
            delegate.post(url, tarArchive, new BodyAndStatusResponseHandler(), HTTP_OK);
        } catch (IOException e) {
            throw new DockerAccessException(e, "Unable to load %s", tarArchive);
        }
    }

    @Override
    public void pullImage(String image, AuthConfig authConfig, String registry, CreateImageOptions options)
            throws DockerAccessException {
        String pullUrl = urlBuilder.pullImage(options);
        log.verbose(Logger.LogVerboseCategory.API, API_LOG_FORMAT_POST, pullUrl);
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
        log.verbose(Logger.LogVerboseCategory.API, API_LOG_FORMAT_POST, pushUrl);
        TemporaryImageHandler temporaryImageHandler = tagTemporaryImage(name, registry);
        DockerAccessException dae = null;
        try {
            doPushImage(pushUrl, createAuthHeader(authConfig), createPullOrPushResponseHandler(), HTTP_OK, retries);
        } catch (IOException e) {
            dae = new DockerAccessException(e, "Unable to push '%s'%s", image, (registry != null) ? " to registry '" + registry + "'" : "");
            throw dae;
        } finally {
            temporaryImageHandler.handle(dae);
        }
    }

    @Override
    public void saveImage(String image, String filename, ArchiveCompression compression) throws DockerAccessException {
        ImageName name = new ImageName(image);
        String url = urlBuilder.getImage(name);
        log.verbose(Logger.LogVerboseCategory.API, API_LOG_FORMAT_GET, url);
        try {
            delegate.get(url, getImageResponseHandler(filename, compression), HTTP_OK);
        } catch (IOException e) {
            throw new DockerAccessException(e, "Unable to save '%s' to '%s'", image, filename);
        }
    }

    private ResponseHandler<Object> getImageResponseHandler(final String filename, final ArchiveCompression compression) throws FileNotFoundException {
        return new ResponseHandler<Object>() {
            @Override
            public Object handleResponse(HttpResponse response) throws IOException {
                try (InputStream stream = response.getEntity().getContent();
                     OutputStream out = compression.wrapOutputStream(new FileOutputStream(filename))) {
                    IOUtils.copy(stream, out, COPY_BUFFER_SIZE);
                }
                return null;
            }
        };
    }

    @Override
    public void tag(String sourceImage, String targetImage, boolean force)
            throws DockerAccessException {
        ImageName source = new ImageName(sourceImage);
        ImageName target = new ImageName(targetImage);
        String url = urlBuilder.tagContainer(source, target, force);
        log.verbose(Logger.LogVerboseCategory.API, API_LOG_FORMAT_POST, url);
        try {
            delegate.post(url, HTTP_CREATED);
        } catch (IOException e) {
            throw new DockerAccessException(e, "Unable to add tag [%s] to image [%s]", targetImage,
                    sourceImage, e);
        }
    }

    @Override
    public boolean removeImage(String image, boolean force) throws DockerAccessException {
        String url = urlBuilder.deleteImage(image, force);
        log.verbose(Logger.LogVerboseCategory.API, API_LOG_FORMAT_DELETE, url);
        try {
            HttpBodyAndStatus response = delegate.delete(url, new BodyAndStatusResponseHandler(), HTTP_OK, HTTP_NOT_FOUND);
            if (log.isDebugEnabled()) {
                logRemoveResponse(JsonFactory.newJsonArray(response.getBody()));
            }
            return response.getStatusCode() == HTTP_OK;
        } catch (IOException e) {
            throw new DockerAccessException(e, "Unable to remove image [%s]", image);
        }
    }

    @Override
    public List<Network> listNetworks() throws DockerAccessException {
        String url = urlBuilder.listNetworks();
        log.verbose(Logger.LogVerboseCategory.API, API_LOG_FORMAT_GET, url);
        try {
            String response = delegate.get(url, HTTP_OK);
            JsonArray array = JsonFactory.newJsonArray(response);
            List<Network> networks = new ArrayList<>(array.size());

            for (int i = 0; i < array.size(); i++) {
                networks.add(new NetworksListElement(array.get(i).getAsJsonObject()));
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
        String url = urlBuilder.createNetwork();
        log.verbose(Logger.LogVerboseCategory.API, API_LOG_FORMAT_POST_WITH_REQUEST, url, createJson);
        try {
            String response =
                    delegate.post(url, createJson, new ApacheHttpClientDelegate.BodyResponseHandler(), HTTP_CREATED);
            log.debug(response);
            JsonObject json = JsonFactory.newJsonObject(response);
            if (json.has("Warnings")) {
                logWarnings(json);
            }

            // only need first 12 to id a container
            return json.get("Id").getAsString().substring(0, 12);
        } catch (IOException e) {
            throw new DockerAccessException(e, "Unable to create network for [%s]",
                    networkConfig.getName());
        }
    }

    @Override
    public boolean removeNetwork(String networkId)
            throws DockerAccessException {
        String url = urlBuilder.removeNetwork(networkId);
        log.verbose(Logger.LogVerboseCategory.API, API_LOG_FORMAT_DELETE, url);
        try {
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
        String url = urlBuilder.createVolume();
        log.verbose(Logger.LogVerboseCategory.API, API_LOG_FORMAT_POST_WITH_REQUEST, url, createJson);
        try
        {
            String response =
                    delegate.post(url,
                                  createJson,
                                  new ApacheHttpClientDelegate.BodyResponseHandler(),
                                  HTTP_CREATED);
            JsonObject json = JsonFactory.newJsonObject(response);
            logWarnings(json);

            return json.get("Name").getAsString();
        }
        catch (IOException e)
        {
           throw new DockerAccessException(e, "Unable to create volume for [%s]",
                                           containerConfig.getName());
        }
    }

    @Override
    public void removeVolume(String name) throws DockerAccessException {
        String url = urlBuilder.removeVolume(name);
        log.verbose(Logger.LogVerboseCategory.API, API_LOG_FORMAT_DELETE, url);
        try {
            delegate.delete(url, HTTP_NO_CONTENT, HTTP_NOT_FOUND);
        } catch (IOException e) {
            throw new DockerAccessException(e, "Unable to remove volume [%s]", name);
        }
    }

    @Override
    public String getNativePlatform() {
        return nativePlatform;
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
        // 0: The original attempt, 1..retry: possible retries.
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

    private TemporaryImageHandler tagTemporaryImage(ImageName name, String registry) throws DockerAccessException {
        String targetImage = name.getFullName(registry);
        if (name.hasRegistry() || registry == null) {
            return () ->
                log.info("Temporary image tag skipped. Target image '%s' already has registry set or no registry is available",
                    targetImage);
        }

        String fullName = name.getFullName();
        boolean alreadyHasImage = hasImage(targetImage);

        if (alreadyHasImage) {
            log.warn("Target image '%s' already exists. Tagging of '%s' will replace existing image",
                targetImage, fullName);
        }

        tag(fullName, targetImage, false);
        return alreadyHasImage ?
            () -> log.info("Tagged image '%s' won't be removed after tagging as it already existed", targetImage) :
            new RemovingTemporaryImageHandler(targetImage);
    }

    // ===========================================================================================================

    private void logWarnings(JsonObject body) {
        if (body.has("Warnings")) {
            JsonElement warningsObj = body.get("Warnings");
            if (!warningsObj.isJsonNull()) {
                JsonArray warnings = (JsonArray) warningsObj;
                for (int i = 0; i < warnings.size(); i++) {
                    log.warn(warnings.get(i).getAsString());
                }
            }
        }
    }

    // Callback for processing response chunks
    private void logRemoveResponse(JsonArray logElements) {
        for (int i = 0; i < logElements.size(); i++) {
            JsonObject entry = logElements.get(i).getAsJsonObject();
            for (Object key : entry.keySet()) {
                log.debug("%s: %s", key, entry.get(key.toString()));
            }
        }
    }

    private static boolean isSSL(String url) {
        return url.toLowerCase().startsWith("https");
    }

    @FunctionalInterface
    private interface TemporaryImageHandler {
        void handle() throws DockerAccessException;

        default void handle(@Nullable DockerAccessException interruptingError) throws DockerAccessException {
            handle();

            if (interruptingError == null) {
                return;
            }
            throw interruptingError;
        }
    }

    private final class RemovingTemporaryImageHandler implements TemporaryImageHandler {
		private final String targetImage;

		private RemovingTemporaryImageHandler(String targetImage) {
            this.targetImage = targetImage;
        }

		@Override
		public void handle() throws DockerAccessException {
			boolean imageRemoved = removeImage(targetImage, true);
			if (imageRemoved) {
				return;
			}
			throw new DockerAccessException(
				"Image %s could be pushed, but the temporary tag could not be removed",
				targetImage
			);
		}
    }
}
