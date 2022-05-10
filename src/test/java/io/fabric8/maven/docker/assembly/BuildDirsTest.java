package io.fabric8.maven.docker.assembly;

import io.fabric8.maven.docker.util.ProjectPaths;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;

public class BuildDirsTest {
    @Test
    public void getBuildTopDir() {
        ProjectPaths projectPaths = new ProjectPaths(new File("."), "target");
        Assert.assertEquals("example.net/dev-docker-local/project/no-auth/4.1.0".replace('/', File.separatorChar),
            new BuildDirs(projectPaths,"example.net/dev-docker-local/project/no-auth:4.1.0").getBuildTopDir());
    }
}