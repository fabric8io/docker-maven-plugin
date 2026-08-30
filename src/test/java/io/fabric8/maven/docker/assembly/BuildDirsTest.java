package io.fabric8.maven.docker.assembly;

import io.fabric8.maven.docker.util.ProjectPaths;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;

class BuildDirsTest {
    @Test
    void getBuildTopDir() {
        ProjectPaths projectPaths = new ProjectPaths(new File("."), "target");
        Assertions.assertEquals("example.net/dev-docker-local/project/no-auth/4.1.0".replace('/', File.separatorChar),
            new BuildDirs(projectPaths,"example.net/dev-docker-local/project/no-auth:4.1.0").getBuildTopDir());
    }

    @Test
    void relativeOutputDirectoryIsResolvedAgainstProjectBase(@TempDir Path projectBase) {
        BuildDirs buildDirs = new BuildDirs(new ProjectPaths(projectBase.toFile(), "target/docker"), "example:latest");

        Assertions.assertEquals(projectBase.resolve("target/docker/example/latest/build").toFile(),
            buildDirs.getOutputDirectory());
        Assertions.assertEquals(projectBase.resolve("target/docker/example/latest/work").toFile(),
            buildDirs.getWorkingDirectory());
        Assertions.assertEquals(projectBase.resolve("target/docker/example/latest/tmp").toFile(),
            buildDirs.getTemporaryRootDirectory());
    }

    @Test
    void absoluteOutputDirectoryIsNotResolvedAgainstProjectBase(@TempDir Path temporaryDirectory) {
        Path projectBase = temporaryDirectory.resolve("project");
        Path customBuildDirectory = temporaryDirectory.resolve("custom-build").toAbsolutePath();
        BuildDirs buildDirs = new BuildDirs(new ProjectPaths(projectBase.toFile(), customBuildDirectory.toString()),
            "example:latest");

        Assertions.assertEquals(customBuildDirectory.resolve("example/latest/build").toFile(),
            buildDirs.getOutputDirectory());
        Assertions.assertEquals(customBuildDirectory.resolve("example/latest/work").toFile(),
            buildDirs.getWorkingDirectory());
        Assertions.assertEquals(customBuildDirectory.resolve("example/latest/tmp").toFile(),
            buildDirs.getTemporaryRootDirectory());
    }
}
