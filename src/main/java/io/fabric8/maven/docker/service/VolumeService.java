package io.fabric8.maven.docker.service;

import io.fabric8.maven.docker.access.DockerAccess;
import io.fabric8.maven.docker.access.DockerAccessException;
import io.fabric8.maven.docker.access.VolumeCreateConfig;
import io.fabric8.maven.docker.config.VolumeConfiguration;

import java.lang.String;
import java.util.Map;

/**
 *  Service Class for helping control Volumes
 *
 *  @author Tom Burton
 *  @version Dec 15, 2016
 */
public class VolumeService {
   // DAO for accessing the docker daemon
   private DockerAccess docker;

   VolumeService(DockerAccess dockerAccess) {
      this.docker = dockerAccess;
   }

   public String createVolume(VolumeConfiguration vc) throws DockerAccessException {
      VolumeCreateConfig config =
          new VolumeCreateConfig(vc.getName())
              .driver(vc.getDriver())
              .opts(vc.getOpts())
              .labels(vc.getLabels());

      return docker.createVolume(config);
   }

   public void removeVolume(String volumeName) throws DockerAccessException {
      try {
	docker.removeVolume(volumeName);
      } catch ( DockerAccessException dae ) {
	// IGNORE
	// If you're using volume-clean, the most likely cause for a failure
	// is that the volume doesn't exist.  In that case, the build should
	// not be failed.  For any other probable cause of failure, another
	// goal will almost certainly point you at the real problem.
      }
   }
}
