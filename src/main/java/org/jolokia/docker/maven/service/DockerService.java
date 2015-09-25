package org.jolokia.docker.maven.service;

import org.jolokia.docker.maven.access.DockerAccess;

/**
 * Interface to introduce common method getDockerAccess()
 */
public interface DockerService {
    DockerAccess getDockerAccess();
}
