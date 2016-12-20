package io.fabric8.maven.docker.service;

import io.fabric8.maven.docker.access.ContainerCreateConfig;
import io.fabric8.maven.docker.access.ContainerNetworkingConfig;
import io.fabric8.maven.docker.access.DockerAccess;
import io.fabric8.maven.docker.access.DockerAccessException;
import io.fabric8.maven.docker.access.PortMapping;
import io.fabric8.maven.docker.access.VolumeCreateConfig;
import io.fabric8.maven.docker.config.RunVolumeConfiguration;
import io.fabric8.maven.docker.config.NetworkConfig;
import io.fabric8.maven.docker.config.RunImageConfiguration;
import io.fabric8.maven.docker.config.VolumeConfiguration;
import io.fabric8.maven.docker.util.Logger;
import io.fabric8.maven.docker.util.PomLabel;

import java.lang.String;
import java.util.Map;
import java.util.Properties;

/**
 *  Service Class for helping control Volumes 
 * 
 *  @author Tom Burton
 *  @version Dec 15, 2016
 */
public class VolumeService
{
   // logger delegated from top
   private Logger log;

   // DAO for accessing the docker daemon
   private DockerAccess docker;

   /**
    * Sets the DockerAccess object
    */
   public VolumeService(DockerAccess dockerAccess) { 
      this.docker = dockerAccess; 
   }
   
   public String createVolume(VolumeConfiguration vc)
          throws DockerAccessException
   {
      VolumeCreateConfig vcc = createVolumeConfig(vc.getName(), vc.getDriver(), 
                                                  vc.getDriverOpts(), 
                                                  vc.getLabels());
      
      return docker.createVolume(vcc);
   }
   
   // visible for testing
   VolumeCreateConfig createVolumeConfig(String volumeName,
                                         String driver,
                                         Map<String, String> driverOpts, 
                                         Map<String, String> labels)
                      throws DockerAccessException 
   {
       VolumeCreateConfig vconfig = new VolumeCreateConfig(volumeName)
             .driver(driver).driverOpts(driverOpts)
             .labels(labels);
           return vconfig;
   }
   
   public void removeVolume(String volumeName) throws DockerAccessException { 
      docker.removeVolume(volumeName); 
   }
}
