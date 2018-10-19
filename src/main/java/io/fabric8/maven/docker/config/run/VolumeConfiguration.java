package io.fabric8.maven.docker.config.run;


import java.io.Serializable;
import java.util.Map;

import org.apache.commons.lang3.SerializationUtils;

/**
 *  Volume Configuration for Volumes to be created prior to container start
 *
 *  @author Tom Burton
 *  @version Dec 15, 2016
 */
public class VolumeConfiguration implements Serializable
{
   /** Volume Name */
   private String name;

   /** Volume driver for mounting the volume */
   private String driver;

   /** Driver specific options */
   private Map<String, String> opts;

   /** Volume labels */
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
           this.config = that == null ? new VolumeConfiguration() : SerializationUtils.clone(that);
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
