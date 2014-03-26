package org.jolokia.maven.docker;

import java.io.*;
import java.util.List;
import java.util.Map;

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

    private final static String DOCKER_API_VERSION = "v1.8";

    private final Log log;
    private final String url;

    public DockerAccess(String url, Log log) {
        this.url = stripSlash(url) + "/" + DOCKER_API_VERSION ;
        this.log = log;
    }


    public boolean hasImage(String image) throws MojoExecutionException {
        try {
            BaseRequest req = Unirest.get(url + "/images/json?filter={image}")
                           .header("accept", "application/json")
                           .routeParam("image", image);
            HttpResponse<JsonNode> resp = request(req);

            checkReturnCode("Checking for image '" + image + "'",resp);
            JsonNode node = resp.getBody();
            JSONArray array = node.getArray();
            return array.length() > 0;
        } catch (UnirestException e) {
            throw new MojoExecutionException("Cannot list images for '" + image + "'",e);
        }
    }

    public String createContainer(String image, Map<Integer, Integer> ports, String command) throws MojoExecutionException {
        try {
            BaseRequest req = Unirest.post(url + "/containers/create")
                                           .header("accept", "application/json")
                                           .body(getContainerConfig(image, ports, command));
            HttpResponse<JsonNode> resp = request(req);
            checkReturnCode("Creating container for image '" + image + "'", resp);
            logWarnings(resp.getBody());
            return resp.getBody().getObject().getString("Id");
        } catch (UnirestException e) {
            throw new MojoExecutionException("Cannot create container for '" + image + "'",e);
        }
    }

    public void startContainer(String containerId, Map<Integer,Integer> ports) throws MojoExecutionException {
        try {
            BaseRequest req = Unirest.post(url + "/containers/{id}/start")
                                           .header("accept", "application/json")
                                           .routeParam("id", containerId)
                                           .body(getStartConfig(ports));
            HttpResponse<JsonNode> resp = request(req);
            checkReturnCode("Starting container with id " + containerId,resp);
        } catch (UnirestException e) {
            throw new MojoExecutionException("Cannot start container " + containerId,e);
        }
    }

    public void stopContainer(String containerId) throws MojoExecutionException {
        try {
            BaseRequest req = Unirest.post(url + "/containers/{id}/stop")
                                     .header("accept","application/json")
                                 .routeParam("id",containerId);
            HttpResponse<JsonNode> resp = requestPlain(req);
            checkReturnCode("Stopping container with id " + containerId,resp);

        } catch (UnirestException e) {
            throw new MojoExecutionException("Cannot stop container " + containerId,e);
        }
    }

    public void removeContainer(String containerId) throws MojoExecutionException {
        try {
            BaseRequest req = Unirest.delete(url + "/containers/{id}")
                                     .header("accept", "application/json")
                                 .routeParam("id", containerId);
            HttpResponse resp = requestPlain(req);
            checkReturnCode("Stopping container with id " + containerId,resp);

        } catch (UnirestException e) {
            throw new MojoExecutionException("Cannot remove container " + containerId,e);
        }
    }

    // ===========================================================================================================

    private HttpResponse<JsonNode> request(BaseRequest req) throws UnirestException {
        if (log.isDebugEnabled()) {
            dump(req);
        }
        HttpResponse<JsonNode> resp = req.asJson();
        if (log.isDebugEnabled()) {
            dump(resp);
        }
        return resp;
    }

    private HttpResponse requestPlain(BaseRequest req) throws UnirestException {
        if (log.isDebugEnabled()) {
            dump(req);
        }
        HttpResponse<String> resp = req.asString();
        if (log.isDebugEnabled()) {
            String txt = resp.getBody();
            log.debug("<<<< " + (txt != null ? txt : "[empty]"));
        }
        return resp;
    }

     private String getContainerConfig(String image, Map<Integer, Integer> ports, String command) {
        JsonObject ret = object(field("Image", image));
        if (ports.size() > 0) {
            JsonObject exposedPorts = new JsonObject();
            for (Integer port : ports.keySet()) {
                exposedPorts.put(port.toString() + "/tcp",new JsonObject());
            }
            ret.put("ExposedPorts",exposedPorts);
        }
        if (command != null) {
            ret.put("Cmd",array(command.split("\\s+")));
        }
        return ret.toString();
    }

    private String getStartConfig(Map<Integer, Integer> ports) {
        if (ports.size() > 0) {
            JsonObject c = new JsonObject();
            for (Integer containerPort : ports.keySet()) {
                c.put(containerPort + "/tcp",
                      array(object(
                                    field("HostPort", "" + ports.get(containerPort))
                                  )
                           ));
            }
            return object(field("PortBindings",c)).toString();
        } else {
            return "{}";
        }
    }

    // ======================================================================================================

    private void dump(HttpResponse<JsonNode> resp) {
        JsonNode node = resp.getBody();
        log.debug("<<<< " + (node != null ? node.toString() : "[empty]"));
    }

    private void dump(BaseRequest req) {
        HttpRequest hReq = req.getHttpRequest();
        log.debug(">>>> " + hReq.getUrl());
        Map<String,List<String>> headers = hReq.getHeaders();
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
            StringBuilder sb=new StringBuilder();
            BufferedReader br = new BufferedReader(is);
            String read = br.readLine();

            while(read != null) {
            //System.out.println(read);
                sb.append(read);
                read =br.readLine();
            }
            return sb.toString();
        } catch (IOException e) {
            return "[error serializing inputstream for debugging]";
        }
    }


    private void logWarnings(JsonNode body) {
        Object warningsObj = body.getObject().get("Warnings");
        if (warningsObj != JSONObject.NULL) {
            JSONArray warnings = (JSONArray) warningsObj;
            for (int i = 0; i < warnings.length(); i++) {
                log.warn(warnings.getString(i));
            }
        }
    }


    private void checkReturnCode(String msg, HttpResponse<JsonNode> resp) throws MojoExecutionException {
        if (resp.getCode() < 200 || resp.getCode() >= 300) {
            throw new MojoExecutionException("Error while calling docker: " + msg + " (code: " + resp.getCode() + ", " +
                                             resp.getBody() + ")");

        }
    }


    private String stripSlash(String url) {
        String ret = url;
        while (ret.endsWith("/")) {
            ret = ret.substring(0,ret.length() - 1);
        }
        return ret;
    }
}
