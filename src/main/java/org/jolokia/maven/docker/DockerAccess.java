package org.jolokia.maven.docker;

import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.github.jsonj.JsonObject;
import com.mashape.unirest.http.*;
import com.mashape.unirest.http.exceptions.UnirestException;
import com.mashape.unirest.request.BaseRequest;
import com.mashape.unirest.request.HttpRequest;
import com.mashape.unirest.request.body.Body;
import org.apache.http.HttpEntity;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.json.JSONArray;
import org.json.JSONObject;

import static com.github.jsonj.tools.JsonBuilder.*;

/**
 * @author roland
 * @since 26.03.14
 */
public class DockerAccess {

    private final static String DOCKER_API_VERSION = "v1.10";

    private final Log log;
    private final String url;

    public DockerAccess(String url, Log log) {
        this.url = stripSlash(url) + "/" + DOCKER_API_VERSION;
        this.log = log;
    }


    public boolean hasImage(String image) throws MojoExecutionException {
        try {
            Matcher matcher = Pattern.compile("^(.*?):?([^:]+)?$").matcher(image);
            String base, tag;
            if (matcher.matches()) {
                base = matcher.group(1);
                tag = matcher.group(2);
            } else {
                base = image;
            }
            BaseRequest req = Unirest.get(url + "/images/json?filter={image}")
                                     .header("accept", "application/json")
                                     .routeParam("image", base);
            HttpResponse<String> resp = request(req);
            checkReturnCode("Checking for image '" + image + "'", resp, 200);
            JSONArray array = new JSONArray(resp.getBody());
            return array.length() > 0;
        } catch (UnirestException e) {
            throw new MojoExecutionException("Cannot list images for '" + image + "'", e);
        }
    }

    public String createContainer(String image, Map<Integer, Integer> ports, String command) throws MojoExecutionException {
        try {
            BaseRequest req = Unirest.post(url + "/containers/create")
                                     .header("Accept", "*/*")
                                     .header("Content-Type", "application/json")
                                     .body(getContainerConfig(image, ports, command));
            HttpResponse<String> resp = request(req);
            checkReturnCode("Creating container for image '" + image + "'", resp, 201);
            JSONObject json = new JSONObject(resp.getBody());
            logWarnings(json);
            return json.getString("Id");
        } catch (UnirestException e) {
            throw new MojoExecutionException("Cannot create container for '" + image + "'", e);
        }
    }

    public void startContainer(String containerId, Map<Integer, Integer> ports) throws MojoExecutionException {
        try {
            BaseRequest req = Unirest.post(url + "/containers/{id}/start")
                                     .header("Accept", "*/*")
                                     .header("Content-Type", "application/json")
                                     .routeParam("id", containerId)
                                     .body(getStartConfig(ports));
            HttpResponse<String> resp = request(req);
            checkReturnCode("Starting container with id " + containerId, resp, 204);
            Thread.sleep(4000);
        } catch (UnirestException e) {
            throw new MojoExecutionException("Cannot start container " + containerId, e);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

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

    public Map<Integer, Integer> getContainerPortMapping(String containerId) throws MojoExecutionException {
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

    private Map<Integer, Integer> extractPorts(JSONObject info) {
        Map<Integer, Integer> ret = new HashMap<Integer, Integer>();
        JSONObject networkSettings = info.getJSONObject("NetworkSettings");
        if (networkSettings != null) {
            JSONObject ports = networkSettings.getJSONObject("Ports");
            if (ports != null) {
                for (Object portSpecO : ports.keySet()) {
                    String portSpec = portSpecO.toString();
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
        return ret;
    }

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

    private String getContainerConfig(String image, Map<Integer, Integer> ports, String command) {
        JsonObject ret = object(field("Image", image));
        if (ports.size() > 0) {
            JsonObject exposedPorts = new JsonObject();
            for (Integer port : ports.keySet()) {
                exposedPorts.put(port.toString() + "/tcp", new JsonObject());
            }
            ret.put("ExposedPorts", exposedPorts);
        }
        if (command != null) {
            ret.put("Cmd", array(command.split("\\s+")));
        }
        return ret.toString();
    }

    private String getStartConfig(Map<Integer, Integer> ports) {
        if (ports.size() > 0) {
            JsonObject c = new JsonObject();
            for (Integer containerPort : ports.keySet()) {
                Integer port = ports.get(containerPort);
                c.put(containerPort + "/tcp",
                      array(object(field("HostPort", port != null ? port.toString() : ""))));
            }
            return object(field("PortBindings", c)).toString();
        } else {
            return "{}";
        }
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
}
