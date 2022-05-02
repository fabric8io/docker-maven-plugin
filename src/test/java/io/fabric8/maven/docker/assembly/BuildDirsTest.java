package io.fabric8.maven.docker.assembly;

import io.fabric8.maven.docker.util.ProjectPaths;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.File;

class BuildDirsTest {
    @Test
    void getBuildTopDir() {
        ProjectPaths projectPaths = new ProjectPaths(new File("."), "target");
        Assertions.assertEquals("example.net/dev-docker-local/project/no-auth/4.1.0".replace('/', File.separatorChar),
            new BuildDirs(projectPaths,"example.net/dev-docker-local/project/no-auth:4.1.0").getBuildTopDir());
    }
}