package io.fabric8.maven.docker.access;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;

import io.fabric8.maven.docker.util.ImageName;

public final class UrlBuilder {

    private final String apiVersion;

    // Base Docker URL
    private final String baseUrl;

    public UrlBuilder(String baseUrl, String apiVersion) {
        this.apiVersion = apiVersion;
        this.baseUrl = stripSlash(baseUrl);
    }

    public String buildImage(String image, BuildOptions options) {
        Builder urlBuilder = u("build")
            .p("t", image);
        if (options != null) {
            urlBuilder.p(options.getOptions());
        }
        return urlBuilder.build();
    }

    public String copyArchive(String containerId, String targetPath) {
        return u("containers/%s/archive",containerId)
                .p("path",targetPath)
                .build();
    }

    public String inspectImage(String name) {
        return u("images/%s/json", name)
                .build();
    }

    public String listImages(boolean all) {
        return u("images/json")
                .p("all", all)
                .build();
    }

    public String containerLogs(String containerId, boolean follow) {
        return u("containers/%s/logs", containerId)
                .p("stdout",true)
                .p("stderr",true)
                .p("timestamps", true)
                .p("follow", follow)
                .build();
    }

    public String createContainer(String name) {
        return u("containers/create")
                .p("name", name)
                .build();
    }

    public String version() {
        return String.format("%s/version", baseUrl);
    }

    public String deleteImage(String name, boolean force) {
        return u("images/%s", name)
                .p("force", force)
                .build();
    }

    public String getImage(ImageName name) {
        return u("images/%s/get", name.getFullName())
            .build();
    }

    public String inspectContainer(String containerId) {
        return u("containers/%s/json", containerId)
                .build();
    }

    public String inspectExecContainer(String containerId) {
        return u("exec/%s/json", containerId)
                .build();
    }

    public String listContainers(boolean all, String ... filter) {
        Builder builder = u("containers/json").p("all", all);
        addFilters(builder, filter);
        return builder.build();
    }

    public String loadImage() {
        return u("images/load")
            .build();
    }

    public String pullImage(ImageName name, String registry) {
        return u("images/create")
                .p("fromImage", name.getNameWithoutTag(registry))
                .p("tag", name.getDigest() != null ? name.getDigest() : name.getTag())
                .build();
    }

    public String pushImage(ImageName name, String registry) {
        return u("images/%s/push", name.getNameWithoutTag(registry))
                .p("tag", name.getTag())
                // "force=1" helps Fedora/CentOs Docker variants to push to public registries
                .p("force", true)
                .build();
    }

    public String removeContainer(String containerId, boolean removeVolumes) {
        return u("containers/%s", containerId)
                .p("v", removeVolumes)
                .build();
    }

    public String startContainer(String containerId) {
        return u("containers/%s/start", containerId)
                .build();
    }

    public String createExecContainer(String containerId) {
        return u("containers/%s/exec", containerId)
                .build();
    }

    public String startExecContainer(String containerId) {
        return u("exec/%s/start", containerId)
                .build();
    }

    public String stopContainer(String containerId, int killWait) {
        Builder b = u("containers/%s/stop", containerId);
        if (killWait > 0) {
            b.p("t", killWait);
        }
        return b.build();
    }

    public String tagContainer(ImageName source, ImageName target, boolean force) {
        return u("images/%s/tag", source.getFullName())
                .p("repo",target.getNameWithoutTag())
                .p("tag",target.getTag())
                .p("force",force)
                .build();
    }

    public String listNetworks() {
        return u("networks")
                .build();
    }

    public String createNetwork() {
        return u("networks/create")
                .build();
    }

    public String removeNetwork(String id) {
        return u("networks/%s", id)
                .build();
    }

    public String createVolume() {
       return u("volumes/create").build();
    }

    public String removeVolume(String name) {
       return u("volumes/%s", name).build();
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    // ============================================================================

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

    // Entry point for builder
    private Builder u(String format, String ... args) {
        return new Builder(createUrl(String.format(format, (Object[]) encodeArgs(args))));
    }

    private String[] encodeArgs(String[] args) {
        String ret[] = new String[args.length];
        int i=0;
        for (String arg : args) {
            ret[i++] = encode(arg);
        }
        return ret;
    }

    private String createUrl(String path) {
        return String.format("%s/%s/%s", baseUrl, apiVersion, path);
    }

    private void addFilters(Builder builder, String... filter) {
       if (filter.length > 0) {
           if (filter.length % 2 != 0) {
               throw new IllegalArgumentException("Filters must be given as key value pairs and not " + Arrays.asList(filter));
           }
           JsonObject filters = new JsonObject();
           for (int i = 0; i < filter.length; i +=2) {
               JsonArray value = new JsonArray();
               value.add(filter[i+1]);
               filters.add(filter[i],value);
           }
           builder.p("filters",filters.toString());
       }
    }

    private static class Builder {

        private Map<String,String> queryParams = new HashMap<>();
        private String url;

        public Builder(String url) {
            this.url = url;
        }

        private Builder p(Map<String, String> params) {
            queryParams.putAll(params);
            return this;
        }

        private Builder p(String key, String value) {
             if (value != null) {
                queryParams.put(key, value);
            }
            return this;
        }

        private Builder p(String key, boolean value) {
            return p(key,value ? "1" : "0");
        }

        private Builder p(String key, int value) {
            return p(key,Integer.toString(value));
        }

        public String build() {
            if (queryParams.size() > 0) {
                StringBuilder ret = new StringBuilder(url);
                ret.append("?");
                // Sort to make order predictable e.g. for unit testing
                for (String key : new TreeSet<>(queryParams.keySet())) {
                    ret.append(key)
                       .append("=")
                       .append(encode(queryParams.get(key)))
                       .append("&");
                }
                return ret.substring(0,ret.length() - 1);
            } else {
                return url;
            }
        }
    }
}
