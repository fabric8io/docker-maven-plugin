package org.jolokia.docker.maven.access;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.mashape.unirest.http.*;
import com.mashape.unirest.http.exceptions.UnirestException;
import com.mashape.unirest.http.options.Options;
import com.mashape.unirest.http.utils.ClientFactory;
import com.mashape.unirest.http.utils.URLParamEncoder;
import com.mashape.unirest.request.BaseRequest;
import com.mashape.unirest.request.HttpRequest;
import com.mashape.unirest.request.body.Body;
import org.apache.http.HttpEntity;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIUtils;
import org.apache.http.entity.FileEntity;
import org.apache.maven.plugin.MojoExecutionException;
import org.jolokia.docker.maven.util.*;
import org.json.*;

/**
 * Implementation for a DockerAccess based on the <a href="http://unirest.io/java.html">Unirest</a>
 * REST access library.
 *
 * @author roland
 * @since 26.03.14
 */
public class DockerAccessUnirest implements DockerAccess {

    // Used Docker API
    private final static String DOCKER_API_VERSION = "v1.10";

    // Logging
    private final LogHandler log;

    // Base Docker URL
    private final String url;

    /**
     * Create a new access for the given URL
     *
     * @param url base URL for accessing the docker Daemon
     * @param log a log handler for printing out logging information
     */
    public DockerAccessUnirest(String url, LogHandler log) {
        this.url = stripSlash(url) + "/" + DOCKER_API_VERSION;
        this.log = log;
        Unirest.setDefaultHeader("accept", "application/json");
    }

    /** {@inheritDoc} */
    public boolean hasImage(String image) throws MojoExecutionException {
        try {
            Matcher matcher = Pattern.compile("^(.*?):([^:]+)?$").matcher(image);
            String base, tag;
            if (matcher.matches()) {
                base = matcher.group(1);
                tag = matcher.group(2);
            } else {
                base = image;
            }
            BaseRequest req = Unirest.get(url + "/images/json?filter={image}")
                                     .routeParam("image", base);
            HttpResponse<String> resp = request(req);
            checkReturnCode("Checking for image '" + image + "'", resp, 200);
            JSONArray array = new JSONArray(resp.getBody());
            return array.length() > 0;
        } catch (UnirestException e) {
            throw new MojoExecutionException("Cannot list images for '" + image + "'", e);
        }
    }

    /** {@inheritDoc} */
    public String createContainer(String image, Set<Integer> ports, String command, Map<String, String> env) throws MojoExecutionException {
        try {
            BaseRequest req = Unirest.post(url + "/containers/create")
                                     .header("Accept", "*/*")
                                     .header("Content-Type", "application/json")
                                     .body(getContainerConfig(image, ports, command, env));
            HttpResponse<String> resp = request(req);
            checkReturnCode("Creating container for image '" + image + "'", resp, 201);
            JSONObject json = new JSONObject(resp.getBody());
            logWarnings(json);
            return json.getString("Id");
        } catch (UnirestException e) {
            throw new MojoExecutionException("Cannot create container for '" + image + "'", e);
        }
    }

    /** {@inheritDoc} */
    public void startContainer(String containerId, Map<Integer, Integer> ports, String volumesFrom) throws MojoExecutionException {
        try {
            BaseRequest req = Unirest.post(url + "/containers/{id}/start")
                                     .header("Accept", "*/*")
                                     .header("Content-Type", "application/json")
                                     .routeParam("id", containerId)
                                     .body(getStartConfig(ports, volumesFrom));
            HttpResponse<String> resp = request(req);
            checkReturnCode("Starting container with id " + containerId, resp, 204);
        } catch (UnirestException e) {
            throw new MojoExecutionException("Cannot start container " + containerId, e);
        }
    }

    /** {@inheritDoc} */
    public void stopContainer(String containerId) throws MojoExecutionException {
        try {
            BaseRequest req = Unirest.post(url + "/containers/{id}/stop")
                                     .header("Accept", "*/*")
                                     .routeParam("id", containerId);
            HttpResponse<String> resp = request(req);
            checkReturnCode("Stopping container with id " + containerId, resp, 204);

        } catch (UnirestException e) {
            throw new MojoExecutionException("Cannot stop container " + containerId, e);
        }
    }

    /** {@inheritDoc} */
    public Map<Integer, Integer> queryContainerPortMapping(String containerId) throws MojoExecutionException {
        try {
            BaseRequest req = Unirest.get(url + "/containers/{id}/json")
                                     .header("Accept", "*/*")
                                     .routeParam("id", containerId);
            HttpResponse<String> resp = request(req);
            checkReturnCode("Getting container information for " + containerId, resp, 200);
            return extractPorts(new JSONObject(resp.getBody()));
        } catch (UnirestException e) {
            throw new MojoExecutionException("Cannot fetch information for container " + containerId, e);
        }
    }

    /** {@inheritDoc} */
    private Map<Integer, Integer> extractPorts(JSONObject info) {
        Map<Integer, Integer> ret = new HashMap<Integer, Integer>();

        JSONObject networkSettings = info.getJSONObject("NetworkSettings");
        if (networkSettings != null) {
            JSONObject ports = networkSettings.getJSONObject("Ports");
            if (ports != null) {
                for (Object portSpecO : ports.keySet()) {
                    String portSpec = portSpecO.toString();
                    if (!ports.isNull(portSpec)) {
                        JSONArray hostSpecs = ports.getJSONArray(portSpec);
                        if (hostSpecs != null && hostSpecs.length() > 0) {
                            // We take only the first
                            JSONObject hostSpec = hostSpecs.getJSONObject(0);
                            Object hostPortO = hostSpec.get("HostPort");
                            if (hostPortO != null) {
                                try {
                                    Integer hostPort = (Integer.parseInt(hostPortO.toString()));
                                    int idx = portSpec.indexOf('/');
                                    String p = idx > 0 ? portSpec.substring(0, idx) : portSpec;
                                    Integer containerPort = Integer.parseInt(p);
                                    ret.put(containerPort, hostPort);
                                } catch (NumberFormatException exp) {
                                    log.warn("Cannot parse " + hostPortO + " or " + portSpec + " as a port number. Ignoring in mapping");
                                }
                            }
                        }
                    }
                }
            }
        }
        return ret;
    }

    /** {@inheritDoc} */
    public List<String> getContainersForImage(String image) throws MojoExecutionException {
        try {
            List<String> ret = new ArrayList<String>();
            BaseRequest req = Unirest.get(url + "/containers/json?limit=100")
                                     .header("Accept", "*/*");
            HttpResponse<String> resp = null;
            resp = request(req);
            checkReturnCode("Fetching container information", resp, 200);
            JSONArray configs = new JSONArray(resp.getBody());
            for (int i = 0; i < configs.length(); i ++) {
                JSONObject config = configs.getJSONObject(i);
                String containerImage = config.getString("Image");

                if (image.equals(containerImage)) {
                    ret.add(config.getString("Id"));
                }
            }
            return ret;
        } catch (UnirestException e) {
            throw new MojoExecutionException("Cannot get container information", e);
        }
    }

    /** {@inheritDoc} */
    public void removeContainer(String containerId) throws MojoExecutionException {
        try {
            BaseRequest req = Unirest.delete(url + "/containers/{id}")
                                     .header("Accept", "*/*")
                                     .routeParam("id", containerId);
            HttpResponse<String> resp = request(req);
            checkReturnCode("Stopping container with id " + containerId, resp, 204);

        } catch (UnirestException e) {
            throw new MojoExecutionException("Cannot remove container " + containerId, e);
        }
    }

    /** {@inheritDoc} */
    public void pullImage(String image,AuthConfig authConfig) throws MojoExecutionException {
        ImageName name = new ImageName(image);
        String pullUrl = url + "/images/create?fromImage=" + URLParamEncoder.encode(name.getRepository());
        pullUrl = addTagAndRegistry(pullUrl, name);
        pullOrPushImage(image,pullUrl,"pulling",authConfig);
    }

    /** {@inheritDoc} */
    public void pushImage(String image, AuthConfig authConfig) throws MojoExecutionException {
        ImageName name = new ImageName(image);
        String pushUrl = url + "/images/" + URLParamEncoder.encode(name.getRepository()) + "/push";
        pushUrl = addTagAndRegistry(pushUrl,name);
        pullOrPushImage(image,pushUrl,"pushing",authConfig);
    }

    private void pullOrPushImage(String image, String uriString, String what, AuthConfig authConfig)
            throws MojoExecutionException {
        try {
            HttpClient client = ClientFactory.getHttpClient();
            URI uri = new URI(uriString);
            HttpPost post = new HttpPost(uri);
            if (authConfig != null) {
                post.addHeader("X-Registry-Auth",authConfig.toHeaderValue());
            }

            processPullOrPushResponse(image, client.execute(URIUtils.extractHost(uri), post), what);
        } catch (IOException | URISyntaxException e) {
            throw new MojoExecutionException("Error while " + what + " " + image + ": ",e);
        }
    }

    private String addTagAndRegistry(String url, ImageName name) {
        List<String> params = new ArrayList<>();
        if (name.getTag() != null) {
            params.add("tag=" + name.getTag());
        }
        if (name.getRegistry() != null) {
            params.add("registry=" + name.getRegistry());
        }
        if (params.size() > 0) {
            StringBuilder addOn = new StringBuilder();

            for (int i = 0; i < params.size(); i ++) {
                addOn.append(params.get(i));
                if (i < params.size() - 1) {
                    addOn.append("&");
                }
            }
            return url + (url.contains("?") ? "&" : "?") + addOn.toString();
        } else {
            return url;
        }
    }

    /** {@inheritDoc} */
    public void removeImage(String image) throws MojoExecutionException {
        try {
            BaseRequest req = Unirest.delete(url + "/images/{id}")
                                     .header("Accept", "*/*")
                                     .routeParam("id", image);

            HttpResponse<String> resp = request(req);
            checkReturnCode("Removing image " + image, resp, 200);
            if (log.isDebugEnabled()) {
                logRemoveResponse(resp.getBody());
            }
        } catch (UnirestException e) {
            throw new MojoExecutionException("Cannot remove image " + image,e);
        }
    }

    /** {@inheritDoc} */
    public void start() {
        Options.refresh();
    }

    /** {@inheritDoc} */
    public void shutdown() {
        try {
            Unirest.shutdown();
        } catch (IOException e) {
            log.warn("Couldnt properly shutdown rest interface: " + e);
        }
    }

    /** {@inheritDoc} */
    public void buildImage(String image, File dockerArchive) throws MojoExecutionException {
        try {
            HttpClient client = ClientFactory.getHttpClient();
            URI buildUrl = new URI(url + "/build?rm=true" + (image != null ? "&t=" + URLParamEncoder.encode(image) : ""));
            HttpPost post = new HttpPost(buildUrl);
            post.setEntity(new FileEntity(dockerArchive));
            processBuildResponse(image, client.execute(URIUtils.extractHost(buildUrl), post));
        } catch (IOException e) {
            throw new MojoExecutionException("Cannot build image " + image + " from " + dockerArchive,e);
        }  catch (URISyntaxException e) {
            throw new MojoExecutionException("Cannot build image " + image + " from " + dockerArchive,e);
        }
    }

    // ===========================================================================================================

    private HttpResponse<String> request(BaseRequest req) throws UnirestException {
        if (log.isDebugEnabled()) {
            dump(req);
        }
        HttpResponse<String> resp = req.asString();
        if (log.isDebugEnabled()) {
            dump(resp);
        }
        return resp;
    }

    private String getContainerConfig(String image, Set<Integer> ports, String command, Map<String, String> env) {
        JSONObject ret = new JSONObject();
        ret.put("Image",image);
        if (ports != null && ports.size() > 0) {
            JSONObject exposedPorts = new JSONObject();
            for (Integer port : ports) {
                exposedPorts.put(port.toString() + "/tcp", new JSONObject());
            }
            ret.put("ExposedPorts", exposedPorts);
        }
        if (command != null) {
            JSONArray a = new JSONArray();
            for (String s : command.split("\\s+")) {
                a.put(s);
            }
            ret.put("Cmd",a);
        }
        if (env != null && env.size() > 0) {
            JSONArray a = new JSONArray();
            for (Map.Entry<String,String> entry : env.entrySet()) {
                a.put(entry.getKey() + "=" + entry.getValue());
            }
            ret.put("Env",a);
        }
        log.debug("Container create config: " + ret.toString());
        return ret.toString();
    }

    private String getStartConfig(Map<Integer, Integer> ports, String volumesFrom) {
        JSONObject ret = new JSONObject();
        if (ports != null && ports.size() > 0) {
            JSONObject c = new JSONObject();
            for (Integer containerPort : ports.keySet()) {
                Integer port = ports.get(containerPort);
                JSONArray a = new JSONArray();
                JSONObject o = new JSONObject();
                o.put("HostPort",port != null ? port.toString() : "");
                a.put(o);
                c.put(containerPort + "/tcp",a);
            }
            ret.put("PortBindings", c);
        }
        if (volumesFrom != null) {
            JSONArray a = new JSONArray();
            a.put(volumesFrom);
            ret.put("VolumesFrom", a);
        }
        log.debug("Container start config: " + ret.toString());
        return ret.toString();
    }

    // ======================================================================================================

    private void dump(HttpResponse<String> resp) {
        String body = resp.getBody();
        log.debug("<<<< " + (body != null ? body : "[empty]"));
    }

    private void dump(BaseRequest req) {
        HttpRequest hReq = req.getHttpRequest();
        log.debug(">>>> " + hReq.getUrl());
        Map<String, List<String>> headers = hReq.getHeaders();
        if (headers != null) {
            for (String h : headers.keySet()) {
                log.debug("|||| " + h + "=" + headers.get(h));
            }
        }
        if (hReq.getHttpMethod() == HttpMethod.POST) {
            Body body = hReq.getBody();
            log.debug("---- " + (body != null ? convert(body.getEntity()) : "[empty]"));
        }
    }

    private String convert(HttpEntity entity) {
        try {
            if (entity.isStreaming()) {
                return entity.toString();
            }
            InputStreamReader is = new InputStreamReader(entity.getContent());
            StringBuilder sb = new StringBuilder();
            BufferedReader br = new BufferedReader(is);
            String read = br.readLine();

            while (read != null) {
                //System.out.println(read);
                sb.append(read);
                read = br.readLine();
            }
            return sb.toString();
        } catch (IOException e) {
            return "[error serializing inputstream for debugging]";
        }
    }
    private void processPullOrPushResponse(final String image, org.apache.http.HttpResponse resp, final String action) throws IOException, MojoExecutionException {
        processChunkedResponse(resp, new ChunkedCallback() {

            private boolean downloadInProgress = false;

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
                    } else {
                        if (downloadInProgress) {
                            log.progressFinished();
                        }
                        downloadInProgress = false;
                    }
                }
                if (json.has("error")) {
                    String msg = json.getString("error").trim();
                    String details = json.getJSONObject("errorDetail").getString("message").trim();
                    log.error("!!! " +  msg + (msg.equals(details) ? "" : "(" + details + ")"));
                } else {
                    log.info("... " + (json.has("id") ? json.getString("id") + ": " : "") + json.getString("status"));
                }
            }

            public String getErrorMessage(StatusLine status) {
                return "Error while " + action + " image '" + image + "' (code: " + status.getStatusCode() + ", " + status.getReasonPhrase() + ")";
            }
        });
    }

    private void processBuildResponse(final String image, org.apache.http.HttpResponse resp) throws IOException, MojoExecutionException {

        processChunkedResponse(resp, new ChunkedCallback() {
            public void process(JSONObject json) {
                if (json.has("error")) {
                    log.error("Error building image: " + json.get("error"));
                    if (json.has("errorDetail")) {
                        JSONObject details = json.getJSONObject("errorDetail");
                        log.error(details.getString("message"));
                    }
                } else if (json.has("stream")) {
                    String message = json.getString("stream");
                    while (message.endsWith("\n")) {
                        message = message.substring(0,message.length() - 1);
                    }
                    log.debug(message);
                }
            }

            public String getErrorMessage(StatusLine status) {
                return "Error while building image '" + image + "' (code: " + status.getStatusCode()
                       + ", " + status.getReasonPhrase() + ")";
            }
        });

    }

    private void processChunkedResponse(org.apache.http.HttpResponse resp, ChunkedCallback cb) throws IOException, MojoExecutionException {
        InputStream is = resp.getEntity().getContent();
        int len;
        int size = 8129;
        byte[] buf = new byte[size];
        // Data comes in chunkwise
        while ((len = is.read(buf, 0, size)) != -1) {
            String txt = new String(buf,0,len);
            try {
                JSONObject json = new JSONObject(txt);
                cb.process(json);
            } catch (JSONException exp) {
                log.warn("Couldn't parse answer chunk '" + txt + "': " + exp);
            }
        }
        StatusLine status = resp.getStatusLine();
        if (status.getStatusCode() != 200) {
            throw new MojoExecutionException(cb.getErrorMessage(status));
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

    private void checkReturnCode(String msg, HttpResponse resp, int expectedCode) throws MojoExecutionException {
        if (resp.getCode() != expectedCode) {
            throw new MojoExecutionException("Error while calling docker: " + msg + " (code: " + resp.getCode() + ", " +
                                             resp.getBody().toString().trim() + ")");

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
    private void logRemoveResponse(String pResp) {
        JSONArray logElements = new JSONArray(pResp);
        for (int i = 0; i < logElements.length(); i++) {
            JSONObject entry = logElements.getJSONObject(i);
            for (Object key : entry.keySet()) {
                log.debug(key + ": " + entry.get(key.toString()));
            }
        }
    }

    // ================================================================================================

    private interface ChunkedCallback {
        public void process(JSONObject json);
        String getErrorMessage(StatusLine status);
    }
}
