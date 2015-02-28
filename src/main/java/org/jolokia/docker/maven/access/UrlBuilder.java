package org.jolokia.docker.maven.access;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

import org.jolokia.docker.maven.util.ImageName;

public final class UrlBuilder {

    private final String apiVersion;

    // Base Docker URL
    private final String baseUrl;

    public UrlBuilder(String baseUrl, String apiVersion) {
        this.apiVersion = apiVersion;
        this.baseUrl = stripSlash(baseUrl);
    }

    public String buildImage(String tag, boolean forcerm, boolean pull) {
        String url = createUrl("/build");
        
        if (tag != null) {
            url = addQueryParam(url, "t", tag);
        }
        
        url = addQueryParam(url, "pull", pull);
        url = addQueryParam(url, forcerm ? "forcerm" : "rm", true);
        
        return url;
    }

    public String containerLogs(String containerId, boolean follow) {
        String url = createUrl(String.format("/containers/%s/logs", containerId));
        url += "?stdout=1&stderr=1&timestamps=1";
        url = addQueryParam(url, "follow", follow);
        
        return url;
    }
    
    public String createContainer() {
        return createUrl("/containers/create");
    }

    public String deleteImage(String name, boolean force) {
        String url = createUrl(String.format("/images/%s", name));
        url = addQueryParam(url, name, force);
        
        return url;
    }

    public String inspectContainer(String containerId) {
        return createUrl(String.format("/containers/%s/json", encode(containerId)));
    }
    
    public String listContainers(int limit) {
        return createUrl(String.format("/containers/json?limit=%s", limit));
    }

    public String listImages(ImageName name) {
        return createUrl(String.format("/images/json?filter=%s", name.getNameWithoutTag()));
    }

    public String pullImage(ImageName name, String registry) {
        String url = createUrl(String.format("/images/create?fromImage=%s", encode(name.getNameWithoutTag(registry))));
        url = addTagParam(url, name.getTag());

        return url;
    }

    public String pushImage(ImageName name, String registry) {
        String url = createUrl(String.format("/images/%s/push", encode(name.getNameWithoutTag(registry))));
        url = addTagParam(url, name.getTag());

        return url;
    }

    public String removeContainer(String containerId) {
        return createUrl(String.format("/containers/%s", encode(containerId)));
    }

    public String startContainer(String containerId) {
        return createUrl(String.format("/containers/%s/start", encode(containerId)));
    }

    public String stopContainer(String containerId) {
        return createUrl(String.format("/containers/%s/stop", encode(containerId)));
    }

    public String tagContainer(ImageName source, ImageName target, boolean force) {
        String url = createUrl(String.format("/images/%s/tag", encode(source.getNameWithoutTag(null))));
        url = addRepositoryParam(url, target.getNameWithoutTag(null));
        url = addTagParam(url, target.getTag());
        if (force) {
            url = addQueryParam(url, "force", "1");
        }

        return url;
    }

    private String addQueryParam(String url, String param, boolean value) {
        return addQueryParam(url, param, (value) ? "1" : "0");
    }

    private String addQueryParam(String url, String param, String value) {
        if (value != null) {
            return url + (url.contains("?") ? "&" : "?") + param + "=" + encode(value);
        }
        return url;
    }

    private String addRepositoryParam(String url, String repository) {
        return addQueryParam(url, "repo", repository);
    }

    private String addTagParam(String url, String tag) {
        return addQueryParam(url, "tag", tag);
    }

    private String createUrl(String path) {
        return String.format("%s/%s%s", baseUrl, apiVersion, path);
    }

    @SuppressWarnings("deprecation")
    private String encode(String param) {
        try {
            return URLEncoder.encode(param, "UTF-8");
        }
        catch (UnsupportedEncodingException e) {
            // wont happen
            return URLEncoder.encode(param);
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
