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
 *  Mojo to remove volumes created with {@link VolumeCreateMojo}
 *
 *  @author Tom Burton
 */
@Mojo(name = "volume-remove", defaultPhase = LifecyclePhase.POST_INTEGRATION_TEST)
public class VolumeRemoveMojo extends AbstractDockerMojo {

   @Override
   protected void executeInternal(ServiceHub serviceHub)
         throws DockerAccessException, MojoExecutionException  {
      VolumeService volService = serviceHub.getVolumeService();

      for ( VolumeConfiguration volume : getVolumes()) {
         log.info("Removing volume %s", volume.getName());
         volService.removeVolume(volume.getName());
      }
   }

}
