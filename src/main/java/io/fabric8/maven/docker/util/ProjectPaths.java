package io.fabric8.maven.docker.util;

import java.io.File;
import java.nio.file.Path;

public class ProjectPaths {
    private final Path projectBasePath;
    private final Path outputPath;

    public ProjectPaths(File projectBaseDir,String outputDir) {
        this.projectBasePath = projectBaseDir.toPath();
        this.outputPath = projectBasePath.resolve(outputDir);
    }

    public Path getProjectBasePath() {
        return projectBasePath;
    }

    public Path getOutputPath() {
        return outputPath;
    }
}
