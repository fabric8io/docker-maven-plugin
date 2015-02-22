package org.jolokia.docker.maven.access;

import static java.net.HttpURLConnection.HTTP_CREATED;
import static java.net.HttpURLConnection.HTTP_NOT_FOUND;
import static java.net.HttpURLConnection.HTTP_NOT_MODIFIED;
import static java.net.HttpURLConnection.HTTP_NO_CONTENT;
import static java.net.HttpURLConnection.HTTP_OK;
import static org.jolokia.docker.maven.access.util.RequestUtil.encode;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.FileEntity;
import org.jolokia.docker.maven.access.http.ApacheHttpDelegate;
import org.jolokia.docker.maven.access.http.ApacheHttpDelegate.Result;
import org.jolokia.docker.maven.access.http.HttpRequestException;
import org.jolokia.docker.maven.access.log.LogCallback;
import org.jolokia.docker.maven.access.log.LogGetHandle;
import org.jolokia.docker.maven.access.log.LogRequestor;
import org.jolokia.docker.maven.util.AnsiLogger;
import org.jolokia.docker.maven.util.ImageName;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

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
    private final AnsiLogger log;
    
    // Base Docker URL
    private final String baseUrl;
    
    private final ApacheHttpDelegate delegate;
    private final UrlBuilder urlBuilder;
    
    /**
     * Create a new access for the given URL
     * @param baseUrl base URL for accessing the docker Daemon
     * @param certPath used to build up a keystore with the given keys and certificates found in this directory
     * @param log a log handler for printing out logging information
     */
    public DockerAccessWithHttpClient(String apiVersion, String baseUrl, String certPath, AnsiLogger log) throws IOException {
        this.log = log;
        this.delegate = new ApacheHttpDelegate(certPath);
        this.urlBuilder = new UrlBuilder(baseUrl, apiVersion);
            
        this.baseUrl = stripSlash(baseUrl) + "/" + apiVersion;
    }

    @Override
    public boolean hasImage(String image) throws DockerAccessException {
        ImageName name = new ImageName(image);
        try {
            String response = get(urlBuilder.listImages(name), HTTP_OK).response;
            JSONArray array = new JSONArray(response);
            
            return containsImage(name, array);
        } catch (HttpRequestException e) {
            log.error(e.getMessage());
            throw new DockerAccessException("Unable to check for image [%s]", image);
        }
    }

    @Override
    public String createContainer(ContainerCreateConfig containerConfig) throws DockerAccessException {
        String createJson = containerConfig.toJson();
        log.debug("Container create config: " + createJson);

        try {
            String response = post(urlBuilder.createContainer(), createJson, HTTP_CREATED).response;
            JSONObject json = new JSONObject(response);
            logWarnings(json);

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
            String response = get(urlBuilder.inspectContainer(containerId), HTTP_OK).response;
            JSONObject json = new JSONObject(response);
            
            return json.getString("Name");
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
        String buildUrl = baseUrl + "/build?rm=true" + (image != null ? "&t=" + encode(image) : "");
        HttpPost post = new HttpPost(buildUrl);
        post.setEntity(new FileEntity(dockerArchive));
        processBuildResponse(image, request(post));
    }

    @Override
    public Map<String, Integer> queryContainerPortMapping(String containerId) throws DockerAccessException {
        try {
            String response = get(urlBuilder.inspectContainer(containerId), HTTP_OK).response;
            JSONObject json = new JSONObject(response);
            
            return extractPorts(json);           
        } catch (HttpRequestException e) {
            throw new DockerAccessException("Unable to query port mappings for container [%s]", containerId);
        }
    }

    @Override
    public List<String> getContainersForImage(String image) throws DockerAccessException {
        return getContainerIds(image,false);
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
            String response = get(urlBuilder.inspectContainer(containerId), HTTP_OK).response;
            JSONObject json = new JSONObject(response);
        
            return json.getJSONObject("State").getBoolean("Running"); 
        } catch (HttpRequestException e) {
            log.error(e.getMessage());
            throw new DockerAccessException("Unable to determine state of container [%s]", containerId);
        }
    }

    @Override
    public void getLogSync(String containerId, LogCallback callback) {
        LogRequestor extractor = new LogRequestor(delegate.getHttpClient(), baseUrl, containerId, callback);
        extractor.fetchLogs();
    }

    @Override
    public LogGetHandle getLogAsync(String containerId, LogCallback callback) {
        LogRequestor extractor = new LogRequestor(delegate.getHttpClient(), baseUrl, containerId, callback);
        extractor.start();
        return extractor;
    }

    @Override
    public void removeContainer(String containerId) throws DockerAccessException {
        try {
            delete(urlBuilder.removeContainer(containerId), HTTP_NO_CONTENT); 
        } catch (HttpRequestException e) {
            log.error(e.getMessage());
            throw new DockerAccessException("Unable to remove container [%s]", containerId);
        }
    }

    @Override
    public void pullImage(String image, AuthConfig authConfig, String registry) throws DockerAccessException {
        ImageName name = new ImageName(image);
        String pullUrl = baseUrl + "/images/create?fromImage=" + encode(name.getFullName(registry));
        pullUrl = addTagParam(pullUrl, name.getTag());
        pullOrPushImage(image, pullUrl, "pulling", authConfig);
    }

    @Override
    public void pushImage(String image, AuthConfig authConfig, String registry) throws DockerAccessException {
        ImageName name = new ImageName(image);

        String temporaryImage = tagTemporaryImage(name, registry);
        try {
            String pushUrl = baseUrl + "/images/" + encode(name.getFullName(registry)) + "/push";
            pushUrl = addTagParam(pushUrl, name.getTag());
            pullOrPushImage(image, pushUrl, "pushing", authConfig);
        } finally {
            if (temporaryImage != null) {
                removeImage(temporaryImage);
            }
        }
    }

    @Override
    public void tag(String sourceImage, String targetImage) throws DockerAccessException {
        ImageName source = new ImageName(sourceImage);
        ImageName target = new ImageName(targetImage);
        try {
            post(urlBuilder.tagContainer(source, target), null, HTTP_CREATED);
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
                logRemoveResponse(new JSONArray(result.response));
            }
            
            return result.code == HTTP_OK;
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

    private boolean containsImage(ImageName name, JSONArray array) {
        if (array.length() > 0) {
            for (int i = 0; i < array.length(); i++) {
                JSONObject imageObject = array.getJSONObject(i);
                JSONArray repoTags = imageObject.getJSONArray("RepoTags");
                for (int j = 0; j < repoTags.length(); j++) {
                    if (name.getFullNameWithTag().equals(repoTags.getString(j))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
    
    private Result delete(String url, int statusCode, int... additional) throws HttpRequestException, DockerAccessException {
        try {
            return delegate.delete(url, statusCode, additional);
        } catch (IOException e) {            
            throw new DockerAccessException("communication error occurred with the docker daemon", e);
        }
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

    private Result get(String url, int statusCode, int... additional) throws HttpRequestException, DockerAccessException {
        try {
            return delegate.get(url, statusCode, additional);
        } catch (IOException e) {            
            throw new DockerAccessException("communication error occurred with the docker daemon", e);
        }
    }
    
    private List<String> getContainerIds(String image, boolean onlyLatest) throws DockerAccessException {
        ImageName imageName = new ImageName(image);
        String imageFullName = imageName.getFullNameWithTag();

        try {
            String response = get(urlBuilder.listContainers(100), HTTP_OK).response;
            JSONArray array = new JSONArray(response);
            
            return extractMatchingContainers(onlyLatest, imageFullName, array);
        } catch (HttpRequestException e) {
            throw new DockerAccessException(e.getMessage());
        }
    }
    
    private Result post(String url, String body, int statusCode, int... additional) throws HttpRequestException, DockerAccessException {
        try {
            return delegate.post(url, body, statusCode, additional);
        } catch (IOException e) {            
            throw new DockerAccessException("communication error occurred with the docker daemon", e);
        }
    }
    
    private String tagTemporaryImage(ImageName name, String registry) throws DockerAccessException {
        String targetImage = name.getFullNameWithTag(registry);

        if (!name.hasRegistry() && registry != null && !hasImage(targetImage)) {
            tag(name.getFullNameWithTag(null), targetImage);
            return targetImage;
        }
        return null;
    }
    
    // ====================================================================================================

    private HttpResponse request(HttpUriRequest req) throws DockerAccessException {
        try {
            return delegate.getHttpClient().execute(req);
        } catch (IOException e) {
            throw new DockerAccessException("Cannot send req " + req + ": " + e,e);
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

    // ======================================================================================================

    private void pullOrPushImage(String image, String uri, String what, AuthConfig authConfig)
            throws DockerAccessException {
        try {
            HttpPost post = new HttpPost(uri);
            if (authConfig != null) {
                post.addHeader("X-Registry-Auth",authConfig.toHeaderValue());
            }

            processPullOrPushResponse(image, delegate.getHttpClient().execute(post), what);
        } catch (IOException e) {
            throw new DockerAccessException("Error while " + what + " " + image + ": ",e);
        }
    }

    private String addTagParam(String url, String tag) {
        return addQueryParam(url, "tag", tag);
    }

    private String addQueryParam(String url, String param, String value) {
        if (value != null) {
            return url + (url.contains("?") ? "&" : "?") + param + "=" + encode(value);
        } 
        return url;
    }

    private void processPullOrPushResponse(final String image, HttpResponse resp, final String action)
            throws DockerAccessException {
        processChunkedResponse(resp, new ChunkedJsonCallback() {

            private boolean downloadInProgress = false;

            @Override
			public void process(JSONObject json) {
                if (json.has("progressDetail")) {
                    JSONObject details = json.getJSONObject("progressDetail");
                    if (details.has("total")) {
                        if (!downloadInProgress) {
                            log.progressStart(details.getInt("total"));
                        }
                        log.progressUpdate(details.getInt("current"));
                        downloadInProgress = true;
                        return;
                    } 
                       
                    if (downloadInProgress) {
                    	log.progressFinished();
                    }
                    downloadInProgress = false;
                }
                if (json.has("error")) {
                    String msg = json.getString("error").trim();
                    String details = json.getJSONObject("errorDetail").getString("message").trim();
                    log.error("!!! " + msg + (msg.equals(details) ? "" : "(" + details + ")"));
                } else {
                    log.info("... " + (json.has("id") ? json.getString("id") + ": " : "") + json.getString("status"));
                }
            }

            @Override
			public String getErrorMessage(StatusLine status) {
                return "Error while " + action + " image '" + image + "' (code: " + status.getStatusCode() + ", " + status.getReasonPhrase() + ")";
            }
        });
    }

    private void processBuildResponse(final String image, HttpResponse resp) throws DockerAccessException {

        processChunkedResponse(resp, new ChunkedJsonCallback() {
            @Override
			public void process(JSONObject json) {
                if (json.has("error")) {
                    log.error("Error building image: " + json.get("error"));
                    if (json.has("errorDetail")) {
                        JSONObject details = json.getJSONObject("errorDetail");
                        log.error(details.getString("message"));
                    }
                } else if (json.has("stream")) {
                    String message = json.getString("stream");
                    log.debug(trim(message));
                } else if (json.has("status")) {
                    String status = trim(json.getString("status"));
                    String id = json.has("id") ? json.getString("id") : null;
                    if (status.matches("^.*(Download|Pulling).*")) {
                        log.info("  " + (id != null ? id + " " : "") + status);
                    }
                }
            }

            @Override
			public String getErrorMessage(StatusLine status) {
                return "Error while building image '" + image + "' (code: " + status.getStatusCode()
                       + ", " + status.getReasonPhrase() + ")";
            }
        });

    }

    private void processChunkedResponse(HttpResponse resp, final ChunkedJsonCallback cb) throws DockerAccessException {
        processChunkedResponse(resp, new TextToJsonBridgeCallback(cb));
    }

    private void processChunkedResponse(HttpResponse resp, ChunkedTextCallback cb) throws DockerAccessException {
        try {
            InputStream is = resp.getEntity().getContent();
            int len;
            int size = 8129;
            byte[] buf = new byte[size];
            // Data comes in chunkwise
            while ((len = is.read(buf, 0, size)) != -1) {
                String txt = new String(buf,0,len,"UTF-8");
                cb.process(txt);
            }
            StatusLine status = resp.getStatusLine();
            if (status.getStatusCode() != 200) {
                throw new DockerAccessException(cb.getErrorMessage(status));
            }
        } catch (IOException e) {
            throw new DockerAccessException("Cannot process chunk response: " + e,e);
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

    private String stripSlash(String url) {
        String ret = url;
        while (ret.endsWith("/")) {
            ret = ret.substring(0, ret.length() - 1);
        }
        return ret;
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


    private String trim(String message) {
        while (message.endsWith("\n")) {
            message = message.substring(0,message.length() - 1);
        }
        return message;
    }

    // ================================================================================================

    private interface ChunkedJsonCallback {
        void process(JSONObject json);
        String getErrorMessage(StatusLine status);
    }

    private interface ChunkedTextCallback {
        void process(String text);
        String getErrorMessage(StatusLine status);
    }

    // Parse text as json
    private class TextToJsonBridgeCallback implements ChunkedTextCallback {
        ChunkedJsonCallback cb;

        public TextToJsonBridgeCallback(ChunkedJsonCallback cb) {
            this.cb = cb;
        }

        @Override
        public void process(String text) {
            try {
                JSONObject json = new JSONObject(text);
                cb.process(json);
            } catch (JSONException exp) {
                log.warn("Couldn't parse answer chunk '" + text + "': " + exp);
            }
        }

        @Override
        public String getErrorMessage(StatusLine status) {
            return cb.getErrorMessage(status);
        }
    }
}
