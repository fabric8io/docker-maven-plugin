package io.fabric8.maven.docker.config;

import io.fabric8.maven.docker.util.EnvUtil;
import java.io.Serializable;
import java.util.List;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Attestation Configuration
 */
public class AttestationConfiguration implements Serializable {

  /**
   * Provenance attestation mode; one of true, false, min, max
   */
  @Parameter
  private String provenance;

  /**
   * Enable Software Bill of Materials attestation
   */
  @Parameter
  private Boolean sbom;

  public String getProvenance() {
    return provenance;
  }

  public Boolean getSbom() {
    return sbom;
  }

  public static class Builder {

    private final AttestationConfiguration config = new AttestationConfiguration();
    private boolean isEmpty = true;

    public AttestationConfiguration build() {
      return isEmpty ? null : config;
    }

    public AttestationConfiguration.Builder provenance(String provenance) {
      config.provenance = provenance;
      if (provenance != null) {
        isEmpty = false;
      }
      return this;
    }

    public AttestationConfiguration.Builder sbom(Boolean sbom) {
      config.sbom = sbom;
      if (sbom != null) {
        isEmpty = false;
      }
      return this;
    }
  }
}
