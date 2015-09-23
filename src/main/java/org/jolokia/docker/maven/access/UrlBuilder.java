package org.jolokia.docker.maven.access;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.*;

import org.jolokia.docker.maven.util.ImageName;

public final class UrlBuilder {

    private final String apiVersion;

    // Base Docker URL
    private final String baseUrl;

    public UrlBuilder(String baseUrl, String apiVersion) {
        this.apiVersion = apiVersion;
        this.baseUrl = stripSlash(baseUrl);
    }
    
    public String buildImage(String image, boolean forceRemove) {
        return u("build")
                .p("t",image)
                .p(forceRemove ? "forcerm" : "rm", true)
                .build();
    }

    public String inspectImage(String name) {
        return u("images/%s/json", name)
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

    public String deleteImage(String name, boolean force) {
        return u("images/%s", name)
                .p("force", force)
                .build();
    }

    public String inspectContainer(String containerId) {
        return u("containers/%s/json", containerId)
                .build();
    }
    
    public String listContainers(int limit) {
        return u("containers/json")
                .p("limit", limit)
                .build();
    }

    public String pullImage(ImageName name, String registry) {
        return u("images/create")
                .p("fromImage", name.getNameWithoutTag(registry))
                .p("tag", name.getTag())
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
        return new Builder(createUrl(String.format(format,encodeArgs(args))));
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

    private static class Builder {

        private Map<String,String> queryParams = new HashMap<>();
        private String url;

        public Builder(String url) {
            this.url = url;
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
                for (Map.Entry<String, String> entry : queryParams.entrySet()) {
                    ret.append(entry.getKey())
                       .append("=")
                       .append(encode(entry.getValue()))
                       .append("&");
                }
                return ret.substring(0,ret.length() - 1);
            } else {
                return url;
            }
        }
    }
}
