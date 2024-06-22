package io.fabric8.maven.docker.service.helper;

import com.google.gson.JsonObject;
import io.fabric8.maven.docker.service.BuildService;
import io.fabric8.maven.docker.util.DockerFileUtil;
import io.fabric8.maven.docker.util.Logger;
import org.apache.commons.lang3.StringUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.stream.Stream;

public class BuildArgResolver {
  private static final String ARG_PREFIX = "docker.buildArg.";
  private final Logger log;

  public BuildArgResolver(Logger log) {
    this.log = log;
  }

  public Map<String, String> resolveBuildArgs(BuildService.BuildContext buildContext) {
    Map<String, String> buildArgsFromProject = addBuildArgsFromProperties(buildContext.getMojoParameters().getProject().getProperties());
    Map<String, String> buildArgsFromSystem = addBuildArgsFromProperties(System.getProperties());
    Map<String, String> buildArgsFromDockerConfig = addBuildArgsFromDockerConfig();

    //merge build args from all the sources into one map. Different sources maps are allowed to contain duplicate keys between them
    return mergeBuildArgsFrom(buildArgsFromDockerConfig,
      buildContext.getBuildArgs() != null ? buildContext.getBuildArgs() : Collections.emptyMap(),
      buildArgsFromProject,
      buildArgsFromSystem);
  }

  private Map<String, String> addBuildArgsFromProperties(Properties properties) {
    Map<String, String> buildArgs = new HashMap<>();
    for (Object keyObj : properties.keySet()) {
      String key = (String) keyObj;
      if (key.startsWith(ARG_PREFIX)) {
        String argKey = key.replaceFirst(ARG_PREFIX, "");
        String value = properties.getProperty(key);

        if (StringUtils.isNotEmpty(value)) {
          buildArgs.put(argKey, value);
        }
      }
    }
    log.debug("Build args set %s", buildArgs);
    return buildArgs;
  }

  private Map<String, String> addBuildArgsFromDockerConfig() {
    JsonObject dockerConfig = DockerFileUtil.readDockerConfig();
    if (dockerConfig == null) {
      return Collections.emptyMap();
    }

    // add proxies
    Map<String, String> buildArgs = new HashMap<>();
    if (dockerConfig.has("proxies")) {
      JsonObject proxies = dockerConfig.getAsJsonObject("proxies");
      if (proxies.has("default")) {
        JsonObject defaultProxyObj = proxies.getAsJsonObject("default");
        String[] proxyMapping = new String[]{
          "httpProxy", "http_proxy",
          "httpsProxy", "https_proxy",
          "noProxy", "no_proxy",
          "ftpProxy", "ftp_proxy"
        };

        for (int index = 0; index < proxyMapping.length; index += 2) {
          if (defaultProxyObj.has(proxyMapping[index])) {
            buildArgs.put(proxyMapping[index + 1], defaultProxyObj.get(proxyMapping[index]).getAsString());
          }
        }
      }
    }
    log.debug("Build args set %s", buildArgs);
    return buildArgs;
  }

  @SafeVarargs
  private static Map<String, String> mergeBuildArgsFrom(Map<String, String>... buildArgSources) {
    final Map<String, String> buildArgs = new HashMap<>();
    Stream.of(buildArgSources)
      .filter(Objects::nonNull)
      .flatMap(map -> map.entrySet().stream())
      .forEach(entry -> buildArgs.put(entry.getKey(), entry.getValue()));
    return buildArgs;
  }

}