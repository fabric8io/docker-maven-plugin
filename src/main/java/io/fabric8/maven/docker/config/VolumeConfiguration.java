package io.fabric8.maven.docker.config;


import io.fabric8.maven.docker.util.DeepCopy;

import java.io.Serializable;
import java.lang.String;
import java.util.Map;

import org.apache.maven.plugins.annotations.Parameter;

/**
 *  Volume Configuration for Volumes to be created prior to container start
 *
 *  @author Tom Burton
 *  @version Dec 15, 2016
 */
public class VolumeConfiguration implements Serializable
{
   /** Volume Name */
   @Parameter
   private String name;

   /** Volume driver for mounting the volume */
   @Parameter
   private String driver;

   /** Driver specific options */
   @Parameter
   private Map<String, String> opts;

   /** Volume labels */
   @Parameter
   private Map<String, String> labels;

   public String getName() {
       return name;
   }

   public String getDriver() {
       return driver;
   }

   public Map<String, String> getOpts() {
       return opts;
   }

   public Map<String, String> getLabels() {
       return labels;
   }

   // =============================================================================

   public static class Builder {
       private final VolumeConfiguration config;

       public Builder()  {
           this(null);
       }

       public Builder(VolumeConfiguration that) {
           this.config = that == null ? new VolumeConfiguration() : DeepCopy.copy(that);
       }

       public Builder name(String name) {
           config.name = name;
           return this;
       }

       public Builder driver(String driver) {
           config.driver = driver;
           return this;
       }

       public Builder opts(Map<String, String> opts) {
           config.opts = opts;
           return this;
       }

       public Builder labels(Map<String, String> labels) {
           config.labels = labels;
           return this;
       }

       public VolumeConfiguration build() {
           return config;
       }
   }

}
