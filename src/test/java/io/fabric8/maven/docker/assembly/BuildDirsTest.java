package io.fabric8.maven.docker.assembly;

import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.nio.file.Paths;

public class BuildDirsTest {
    @Test
    public void getBuildTopDir() {
        Assert.assertEquals("example.net/dev-docker-local/project/no-auth/4.1.0".replace('/', File.separatorChar), new BuildDirs(Paths.get("."),
            "example.net/dev-docker-local/project/no-auth:4.1.0").getBuildTopDir());
    }
}