package org.jolokia.docker.maven.access;

import java.io.*;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.SSLContext;

import org.apache.http.*;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.*;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLContexts;
import org.apache.http.entity.FileEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.jolokia.docker.maven.util.*;
import org.json.*;

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

    // Used Docker API
    static private final String DOCKER_API_VERSION = "v1.10";
    public static final String HEADER_ACCEPT = "Accept";
    public static final String HEADER_ACCEPT_ALL = "*/*";

    // Logging
    private final LogHandler log;

    // Base Docker URL
    private final String baseUrl;

    // HttpClient to use
    private final HttpClient client;

    /**
     * Create a new access for the given URL
     * @param baseUrl base URL for accessing the docker Daemon
     * @param certPath used to build up a keystore with the given keys and certificates found in this directory
     * @param log a log handler for printing out logging information
     */
    public DockerAccessWithHttpClient(String baseUrl, String certPath, LogHandler log) throws DockerAccessException {
        this.baseUrl = stripSlash(baseUrl) + "/" + DOCKER_API_VERSION;
        this.log = log;
        this.client = createHttpClient(certPath);
    }

    /** {@inheritDoc} */
    public boolean hasImage(String image) throws DockerAccessException {
        Matcher matcher = Pattern.compile("^(.*?):([^:]+)?$").matcher(image);
        String base = matcher.matches() ? matcher.group(1) : image;

        HttpUriRequest req = newGet(baseUrl + "/images/json?filter=" + base);
        HttpResponse resp = request(req);
        checkReturnCode("Checking for image '" + image + "'", resp, 200);
        JSONArray array = asJsonArray(resp);
        return array.length() > 0;
    }

    /** {@inheritDoc} */
    public String createContainer(String image, String command, Set<Integer> ports, Map<String, String> env) throws DockerAccessException {
        HttpUriRequest post = newPost(baseUrl + "/containers/create", getContainerConfig(image, ports, command, env));
        HttpResponse resp = request(post);
        checkReturnCode("Creating container for image '" + image + "'", resp, 201);
        JSONObject json = asJsonObject(resp);
        logWarnings(json);
        return json.getString("Id");
    }

    @Override
    public String getContainerName(String id) throws DockerAccessException {
        HttpUriRequest req = newGet(baseUrl + "/containers/" + encode(id) + "/json");
        HttpResponse  resp = request(req);
        checkReturnCode("Getting information about container '" + id + "'", resp, 200);
        JSONObject json = asJsonObject(resp);
        return json.getString("Name");
    }

    /** {@inheritDoc} */
    public void startContainer(String containerId, Map<Integer, Integer> ports, List<String> volumesFrom,List<String> links)
            throws DockerAccessException {
        HttpUriRequest req = newPost(baseUrl + "/containers/" + encode(containerId) + "/start",
                                     getStartConfig(ports, volumesFrom, links));
        HttpResponse resp = request(req);
        checkReturnCode("Starting container with id " + containerId, resp, 204);
    }

    /** {@inheritDoc} */
    public void stopContainer(String containerId) throws DockerAccessException {
        HttpUriRequest req = newPost(baseUrl + "/containers/" + encode(containerId) + "/stop", null);
        HttpResponse  resp = request(req);
        checkReturnCode("Stopping container with id " + containerId, resp, 204, 304);
    }

    /** {@inheritDoc} */
    public void buildImage(String image, File dockerArchive) throws DockerAccessException {
        String buildUrl = baseUrl + "/build?rm=true" + (image != null ? "&t=" + encode(image) : "");
        HttpPost post = new HttpPost(buildUrl);
        post.setEntity(new FileEntity(dockerArchive));
        processBuildResponse(image, request(post));
    }

    /** {@inheritDoc} */
    public Map<Integer, Integer> queryContainerPortMapping(String containerId) throws DockerAccessException {
        HttpUriRequest req = newGet(baseUrl + "/containers/" + encode(containerId) + "/json");
        HttpResponse resp = request(req);
        checkReturnCode("Getting container information for " + containerId, resp, 200);
        return extractPorts(asJsonObject(resp));
    }

    /** {@inheritDoc} */
    public List<String> getContainersForImage(String image) throws DockerAccessException {
        List<String> ret = new ArrayList<String>();
        HttpUriRequest req = newGet(baseUrl + "/containers/json?limit=100");
        HttpResponse resp = request(req);
        checkReturnCode("Fetching container information", resp, 200);
        JSONArray configs = asJsonArray(resp);
        for (int i = 0; i < configs.length(); i ++) {
            JSONObject config = configs.getJSONObject(i);
            String containerImage = config.getString("Image");

            if (image.equals(containerImage)) {
                ret.add(config.getString("Id"));
            }
        }
        return ret;
    }

    @Override
    public String getLogs(final String containerId) throws DockerAccessException {
        HttpUriRequest get = newGet(baseUrl + "/containers/" + encode(containerId) + "/logs?stdout=1&stderr=1");

        HttpResponse resp = request(get);
        checkReturnCode("Getting log for '" + containerId + "' ",resp,200);
        return asString(resp);
    }

    /** {@inheritDoc} */
    public void removeContainer(String containerId) throws DockerAccessException {
        HttpUriRequest req = newDelete(baseUrl + "/containers/" + encode(containerId));
        HttpResponse  resp = request(req);
        checkReturnCode("Stopping container with id " + containerId, resp, 204);
    }

    /** {@inheritDoc} */
    public void pullImage(String image,AuthConfig authConfig) throws DockerAccessException {
        ImageName name = new ImageName(image);
        String pullUrl = baseUrl + "/images/create?fromImage=" + encode(name.getRepository());
        pullUrl = addTagAndRegistry(pullUrl, name);
        pullOrPushImage(image,pullUrl,"pulling",authConfig);
    }

    /** {@inheritDoc} */
    public void pushImage(String image, AuthConfig authConfig) throws DockerAccessException {
        ImageName name = new ImageName(image);
        String pushUrl = baseUrl + "/images/" + encode(name.getRepository()) + "/push";
        pushUrl = addTagAndRegistry(pushUrl,name);
        pullOrPushImage(image,pushUrl,"pushing",authConfig);
    }

    /** {@inheritDoc} */
    public boolean removeImage(String image, boolean ... forceOpt) throws DockerAccessException {
        boolean force = forceOpt != null && forceOpt.length > 0 && forceOpt[0];
        HttpUriRequest req = newDelete(baseUrl + "/images/" + image + (force ? "?force=1" : ""));
        HttpResponse resp = request(req);
        checkReturnCode("Removing image " + image, resp, 200, 404);
        if (log.isDebugEnabled()) {
            logRemoveResponse(asJsonArray(resp));
        }
        return resp.getStatusLine().getStatusCode() == 200;
    }

    // ---------------
    // Lifecycle methods not needed here
    /** {@inheritDoc} */
    public void start() throws DockerAccessException {}


    /** {@inheritDoc} */
    public void shutdown() {}

    // ====================================================================================================

    // HttpClient to use
    private HttpClient createHttpClient(String certPath) throws DockerAccessException {
        HttpClientBuilder builder = HttpClients.custom();
        addSslSupportIfNeeded(builder,certPath);

        // TODO: Tune client if needed (e.g. add pooling factoring .....
        // But I think, that's not really required.

        return builder.build();
    }

    // Lookup a keystore and add it to the client
    private void addSslSupportIfNeeded(HttpClientBuilder builder, String certPath) throws DockerAccessException {
        if (certPath != null) {
            try {
                KeyStore keyStore = KeyStoreUtil.createDockerKeyStore(certPath);

                SSLContext sslContext =
                        SSLContexts.custom()
                                   .useTLS()
                                   .loadKeyMaterial(keyStore,"docker".toCharArray())
                                   .loadTrustMaterial(keyStore)
                                   .build();
                SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(sslContext);
                builder.setSSLSocketFactory(sslsf);
            } catch (IOException | GeneralSecurityException e) {
                throw new DockerAccessException("Cannot read keys and/or certs from " + certPath + ": " + e,e);
            }
        }

    }

    // -----------------------
    // Request related methods
    private HttpUriRequest newGet(String url) {
        return addDefaultHeaders(new HttpGet(url));
    }

    private HttpUriRequest newPost(String url, String body) {
        HttpPost post = new HttpPost(url);
        if (body != null) {
            post.setEntity(new StringEntity(body, Charset.defaultCharset()));
        }
        return addDefaultHeaders(post);
    }

    private HttpUriRequest newDelete(String url) {
        return addDefaultHeaders(new HttpDelete(url));
    }

    private HttpUriRequest addDefaultHeaders(HttpUriRequest req) {
        req.addHeader(HEADER_ACCEPT, HEADER_ACCEPT_ALL);
        req.addHeader("Content-Type", "application/json");
        return req;
    }

    private HttpResponse request(HttpUriRequest req) throws DockerAccessException {
        try {
            return client.execute(req);
        } catch (IOException e) {
            throw new DockerAccessException("Cannot send req " + req + ": " + e,e);
        }
    }

    // -----------------------
    // Serialization stuff

    private JSONArray asJsonArray(HttpResponse resp) throws DockerAccessException {
        try {
            return new JSONArray(EntityUtils.toString(resp.getEntity()));
        } catch (IOException e) {
            throw new DockerAccessException("Cannot read content from response " + resp,e);
        }
    }

    private JSONObject asJsonObject(HttpResponse resp) throws DockerAccessException {
        try {
            return new JSONObject(EntityUtils.toString(resp.getEntity()));
        } catch (IOException e) {
            throw new DockerAccessException("Cannot read content from response as JSON object " + resp,e);
        }
    }

    private String asString(HttpResponse resp) throws DockerAccessException {
        try {
            return EntityUtils.toString(resp.getEntity());
        } catch (IOException e) {
            throw new DockerAccessException("Cannot read content from response as string " + resp,e);
        }
    }
    private String encode(String param) {
        try {
            return URLEncoder.encode(param,"UTF-8");
        } catch (UnsupportedEncodingException e) {
            // wont happen
            return URLEncoder.encode(param);
        }
    }


    // ===========================================================================================================
    // Preparation for performing requests

    private Map<Integer, Integer> extractPorts(JSONObject info) {
        JSONObject networkSettings = info.getJSONObject("NetworkSettings");
        if (networkSettings != null) {
            JSONObject ports = networkSettings.getJSONObject("Ports");
            if (ports != null) {
                return createPortMapping(ports);
            }
        }
        return Collections.emptyMap();
    }

    private Map<Integer, Integer> createPortMapping(JSONObject ports) {
        Map<Integer, Integer> portMapping = new HashMap<Integer, Integer>();
        for (Object portSpecO : ports.keySet()) {
            String portSpec = portSpecO.toString();
            if (!ports.isNull(portSpec)) {
                JSONArray hostSpecs = ports.getJSONArray(portSpec);
                parseHostSpecsAndUpdateMapping(portMapping, hostSpecs, portSpec);
            }
        }
        return portMapping;
    }

    private void parseHostSpecsAndUpdateMapping(Map<Integer, Integer> portMapping, JSONArray hostSpecs, String portSpec) {
        if (hostSpecs != null && hostSpecs.length() > 0) {
            // We take only the first
            JSONObject hostSpec = hostSpecs.getJSONObject(0);
            Object hostPortO = hostSpec.get("HostPort");
            if (hostPortO != null) {
                parsePortSpecAndUpdateMapping(portMapping, hostPortO, portSpec);
            }
        }
    }

    private void parsePortSpecAndUpdateMapping(Map<Integer, Integer> portMapping, Object hostPort, String portSpec) {
        try {
            Integer hostP = (Integer.parseInt(hostPort.toString()));
            int idx = portSpec.indexOf('/');
            String p = idx > 0 ? portSpec.substring(0, idx) : portSpec;
            Integer containerPort = Integer.parseInt(p);
            portMapping.put(containerPort, hostP);
        } catch (NumberFormatException exp) {
            log.warn("Cannot parse " + hostPort + " or " + portSpec + " as a port number. Ignoring in mapping");
        }
    }

    private String getContainerConfig(String image, Set<Integer> ports, String command, Map<String, String> env) {
        JSONObject ret = new JSONObject();
        ret.put("Image", image);
        if (ports != null && ports.size() > 0) {
            JSONObject exposedPorts = new JSONObject();
            for (Integer port : ports) {
                exposedPorts.put(port.toString() + "/tcp", new JSONObject());
            }
            ret.put("ExposedPorts", exposedPorts);
        }
        if (command != null) {
            JSONArray a = new JSONArray();
            for (String s : EnvUtil.splitWOnSpaceWithEscape(command)) {
                a.put(s);
            }
            ret.put("Cmd",a);
        }
        if (env != null && env.size() > 0) {
            JSONArray a = new JSONArray();
            for (Map.Entry<String,String> entry : env.entrySet()) {
                a.put(entry.getKey() + "=" + entry.getValue());
            }
            ret.put("Env", a);
        }
        log.debug("Container create config: " + ret.toString());
        return ret.toString();
    }

    private String getStartConfig(Map<Integer, Integer> ports, List<String> volumesFrom, List<String> links) {
        JSONObject ret = new JSONObject();
        if (ports != null && ports.size() > 0) {
            JSONObject c = new JSONObject();
            for (Map.Entry<Integer,Integer> entry : ports.entrySet()) {
                Integer port = entry.getValue();
                JSONArray a = new JSONArray();
                JSONObject o = new JSONObject();
                o.put("HostPort",port != null ? port.toString() : "");
                a.put(o);
                c.put(entry.getKey() + "/tcp",a);
            }
            ret.put("PortBindings", c);
        }
        if (volumesFrom != null) {
            ret.put("VolumesFrom", new JSONArray(volumesFrom));
        }
        if (links != null) {
            ret.put("Links", new JSONArray(links));
        }
        log.debug("Container start config: " + ret.toString());
        return ret.toString();
    }

    // ======================================================================================================

    private void dump(HttpResponse resp) {
        String body = null;
        try {
            body = EntityUtils.toString(resp.getEntity());
            log.debug("<<<< " + (body != null ? body : "[empty]"));
        } catch (IOException e) {
            log.error("<<<< Error while deserializing response " + resp + ": " + e);
        }
    }

    private void dump(HttpUriRequest req) {
        try {
            log.debug(">>>> " + req.getURI());
            Header[] headers = req.getAllHeaders();
            if (headers != null) {
                for (Header h : headers) {
                    log.debug("|||| " + h.getName() + "=" + h.getValue());
                }
            }
            if (req.getMethod() == "POST") {
                HttpPost post = (HttpPost) req;
                log.debug("---- " + (post.getEntity() != null ? EntityUtils.toString(post.getEntity()) : "[empty]"));
            }
        } catch (IOException e) {
            log.error("<<<< Error while deserializing response " + req + ": " + e);
        }
    }

    private void pullOrPushImage(String image, String uri, String what, AuthConfig authConfig)
            throws DockerAccessException {
        try {
            HttpPost post = new HttpPost(uri);
            if (authConfig != null) {
                post.addHeader("X-Registry-Auth",authConfig.toHeaderValue());
            }

            processPullOrPushResponse(image, client.execute(post), what);
        } catch (IOException e) {
            throw new DockerAccessException("Error while " + what + " " + image + ": ",e);
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

    private void processPullOrPushResponse(final String image, HttpResponse resp, final String action)
            throws DockerAccessException {
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
                    log.error("!!! " + msg + (msg.equals(details) ? "" : "(" + details + ")"));
                } else {
                    log.info("... " + (json.has("id") ? json.getString("id") + ": " : "") + json.getString("status"));
                }
            }

            public String getErrorMessage(StatusLine status) {
                return "Error while " + action + " image '" + image + "' (code: " + status.getStatusCode() + ", " + status.getReasonPhrase() + ")";
            }
        });
    }

    private void processBuildResponse(final String image, org.apache.http.HttpResponse resp) throws DockerAccessException {

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
                    log.debug(trim(message));
                } else if (json.has("status")) {
                    String status = trim(json.getString("status"));
                    String id = json.has("id") ? json.getString("id") : null;
                    if (status.matches("^.*(Download|Pulling).*")) {
                        log.info("  " + (id != null ? id + " " : "") + status);
                    }
                }
            }

            private String trim(String message) {
                while (message.endsWith("\n")) {
                    message = message.substring(0,message.length() - 1);
                }
                return message;
            }

            public String getErrorMessage(StatusLine status) {
                return "Error while building image '" + image + "' (code: " + status.getStatusCode()
                       + ", " + status.getReasonPhrase() + ")";
            }
        });

    }

    private void processChunkedResponse(org.apache.http.HttpResponse resp, ChunkedCallback cb) throws DockerAccessException {
        try {
            InputStream is = resp.getEntity().getContent();
            int len;
            int size = 8129;
            byte[] buf = new byte[size];
            // Data comes in chunkwise
            while ((len = is.read(buf, 0, size)) != -1) {
                String txt = new String(buf,0,len,"UTF-8");
                try {
                    JSONObject json = new JSONObject(txt);
                    cb.process(json);
                } catch (JSONException exp) {
                    log.warn("Couldn't parse answer chunk '" + txt + "': " + exp);
                }
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

    private void checkReturnCode(String msg, HttpResponse resp, int ... expectedCodes) throws DockerAccessException {
        StatusLine status = resp.getStatusLine();
        int statusCode = status.getStatusCode();
        for (int code : expectedCodes) {
            if (statusCode == code) {
                return;
            }
        }
        throw new DockerAccessException("Error while calling docker: " + msg + " (code: " + statusCode + ")");
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

    // ================================================================================================

    private interface ChunkedCallback {
        void process(JSONObject json);
        String getErrorMessage(StatusLine status);
    }
}
