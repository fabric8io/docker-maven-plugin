package io.fabric8.maven.docker.access;

import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * https://docs.docker.com/engine/api/v1.41/#operation/ImageCreate
 */
public class CreateImageOptions {

  private final Map<String, String> options;

  public CreateImageOptions() {
    this(new HashMap<>());
  }

  public CreateImageOptions(Map<String, String> options) {
    this.options = options != null ? new HashMap<>(options) : new HashMap<>();
  }

  public CreateImageOptions fromImage(String fromImage) {
    if (StringUtils.isNotBlank(fromImage)) {
      options.put("fromImage", fromImage);
    }
    return this;
  }

  public CreateImageOptions tag(String tag) {
    if (StringUtils.isNotBlank(tag)) {
      options.put("tag", tag);
    }
    return this;
  }

  public CreateImageOptions platform(String platform) {
    if (StringUtils.isNotBlank(platform)) {
      options.put("platform", platform);
    }
    return this;
  }

  public Map<String, String> getOptions() {
    return options;
  }
}
