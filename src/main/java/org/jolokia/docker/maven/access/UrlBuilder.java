package org.jolokia.docker.maven.access;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.LinkedHashMap;
import java.util.Map;

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

    public String inspectImage(String name) {
        return createUrl(String.format("/images/%s/json", encode(name)));
    }

    public String containerLogs(String containerId, boolean follow) {
        String url = createUrl(String.format("/containers/%s/logs", containerId));
        url += "?stdout=1&stderr=1&timestamps=1";
        url = addQueryParam(url, "follow", follow);
        
        return url;
    }
    
    public String createContainer(String name) {
        String url = createUrl("/containers/create");
        url = addQueryParam(url, "name", name);
        
        return url;
    }

    public String deleteImage(String name, boolean force) {
        String url = createUrl(String.format("/images/%s", name));
        url = addQueryParam(url, "force", force);

        return url;
    }

    public DockerUrl inspectContainer(String containerId) {
        return createDockerUrl(String.format("/containers/%s/json", encode(containerId)));
    }
    
    public DockerUrl listContainers() {
        return createDockerUrl(String.format("/containers/json"));
    }

    public DockerUrl listImages() {
        return createDockerUrl("/images/json");
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
        // "force=1" helps Fedora/CentOs Docker variants to push to public registries
        url = addForceParam(url,true);
        return url;
    }

    public String removeContainer(String containerId, boolean removeVolumes) {
        String url = createUrl(String.format("/containers/%s", encode(containerId)));
        if (removeVolumes) {
            url = addQueryParam(url, "v", "1");
        }

        return url;
    }

    public String startContainer(String containerId) {
        return createUrl(String.format("/containers/%s/start", encode(containerId)));
    }

    public String stopContainer(String containerId) {
        return createUrl(String.format("/containers/%s/stop", encode(containerId)));
    }

    public String tagContainer(ImageName source, ImageName target, boolean force) {
        String url = createUrl(String.format("/images/%s/tag", encode(source.getFullName())));
        url = addRepositoryParam(url, target.getNameWithoutTag());
        url = addTagParam(url, target.getTag());
        if (force) {
            url = addQueryParam(url, "force", "1");
        }

        return url;
    }

    // ============================================================================

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

    private String addForceParam(String url, boolean force) {
        return addQueryParam(url, "force", force);
    }

    private DockerUrl createDockerUrl(String path) {
        return new DockerUrl(createUrl(path));
    }

    private String createUrl(String path) {
        return String.format("%s/%s%s", baseUrl, apiVersion, path);
    }

    @SuppressWarnings("deprecation")
    private static String encode(String param) {
        try {
            return URLEncoder.encode(param, "UTF-8");
        }
        catch (@SuppressWarnings("unused") UnsupportedEncodingException e) {
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
    
    public static class DockerUrl {
        private final String url;
        private final Map<String, String> queryParams;
        
        DockerUrl(String url) {            
            this.url = url;
            this.queryParams = new LinkedHashMap<>();
        }
        
        public void addQueryParam(String key, String value) {
            queryParams.put(key, value);
        }
        
        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder(url);
            builder.append("?");
            
            int count = queryParams.size();
            for (Map.Entry<String, String> entry : queryParams.entrySet()) {
                builder.append(entry.getKey()).append("=").append(encode(entry.getValue()));
                if (--count > 0) {
                    builder.append("&");
                }
            }
            
            return builder.toString();
        }
    }    
}
