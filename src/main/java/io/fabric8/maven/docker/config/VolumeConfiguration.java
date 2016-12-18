package io.fabric8.maven.docker.config;


import io.fabric8.maven.docker.config.ImageConfiguration.Builder;
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
    
   /** Docker Driver for mounting the volume */
   @Parameter
   private String driver;
   
   /** Driver specific options */
   @Parameter
   private Map<String, String> driverOpts;
   
   /** volume labels */
   @Parameter
   private Map<String, String> labels;

   /** 
    *  get the name of this volume
    *  @return
    */
   public String getName() { return name; }

   /**
    *  get the docker driver for used to mounting the volume
    *  @return
    */
   public String getDriver() { return driver; }

   /**
    *  get driver specific options
    *  @return
    */
   public Map<String, String> getDriverOpts() { return driverOpts; }

   /**
    *  get volume labels
    *  @return
    */
   public Map<String, String> getLabels() { return labels; }

   /** Default Constructor - Does Nothing */
   public VolumeConfiguration() { }

   
   /*
    * =======================================================================
    * Builder For Volume Configurations
    * ======================================================================= 
    */

   public static final Builder builder() { return new Builder(); }
   
   public static class Builder 
   {
       private final VolumeConfiguration config;

       public Builder()  { this(null); }

       public Builder(VolumeConfiguration that) 
       {
          if (that == null) { this.config = new VolumeConfiguration(); } 
          else { this.config = DeepCopy.copy(that); }
       }

       public Builder name(String name) 
       {
           config.name = name;
           return this;
       }
       
       public Builder driver(String driver) 
       {
           config.driver = driver;
           return this;
       }

       public Builder driverOpts(Map<String, String> driverOpts) 
       {
           config.driverOpts = driverOpts;
           return this;
       }
       
       public Builder labels(Map<String, String> labels) 
       {
           config.labels = labels;
           return this;
       }
       
       public VolumeConfiguration build() { return config; }
       
   }
   
}
