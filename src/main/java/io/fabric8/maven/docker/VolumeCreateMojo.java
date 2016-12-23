package io.fabric8.maven.docker;


import io.fabric8.maven.docker.access.DockerAccessException;
import io.fabric8.maven.docker.config.VolumeConfiguration;
import io.fabric8.maven.docker.service.ServiceHub;
import io.fabric8.maven.docker.service.VolumeService;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

/**
 *  Mojo to create named volumes, useful for preparing integration tests
 *
 *  @author Tom Burton
 *  @version Dec 15, 2016
 */
@Mojo(name = "volume-create", defaultPhase = LifecyclePhase.PRE_INTEGRATION_TEST)
public class VolumeCreateMojo extends AbstractDockerMojo {

   @Override
   protected void executeInternal(ServiceHub serviceHub) throws DockerAccessException, MojoExecutionException {
      VolumeService volService = serviceHub.getVolumeService();

      for (VolumeConfiguration volume : getVolumes()) {
         log.info("Creating volume '%s'", volume.getName());
         volService.createVolume(volume);
      }
   }

}
