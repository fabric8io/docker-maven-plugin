package io.fabric8.maven.docker;


import io.fabric8.maven.docker.access.DockerAccessException;
import io.fabric8.maven.docker.config.VolumeConfiguration;
import io.fabric8.maven.docker.service.ServiceHub;
import io.fabric8.maven.docker.service.VolumeService;

import java.lang.String;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

/**
 *  Mojo to Create Named Volumes prior to Docker container start
 *  
 *  @author Tom Burton
 *  @version Dec 15, 2016
 */
@Mojo(name = "volume-remove", defaultPhase = LifecyclePhase.PRE_INTEGRATION_TEST)
public class VolumeRemoveMojo extends AbstractDockerMojo
{
   
   /** Default Constructor - Does Nothing */
   public VolumeRemoveMojo() { }

   /* (non-Javadoc)
    * @see io.fabric8.maven.docker.AbstractDockerMojo#executeInternal(io.fabric8.maven.docker.service.ServiceHub)
    */
   @Override
   protected void executeInternal(ServiceHub serviceHub)
         throws DockerAccessException, MojoExecutionException
   {
      VolumeService volService = serviceHub.getVolumeService();
      
      for ( VolumeConfiguration volume : getVolumes())
      { volService.removeVolume(volume.getName()); }
   }

}
