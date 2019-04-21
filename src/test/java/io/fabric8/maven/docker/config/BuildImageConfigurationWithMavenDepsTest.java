package io.fabric8.maven.docker.config;

import java.io.File;

import io.fabric8.maven.docker.util.Logger;
import io.fabric8.maven.docker.util.MojoParameters;
import mockit.Expectations;
import mockit.Mocked;
import org.apache.maven.project.MavenProject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author roland
 * @since 2019-04-21
 */
public class BuildImageConfigurationWithMavenDepsTest {

    @Mocked
    private MojoParameters params;

    @Mocked
    private MavenProject project;

    @Mocked
    Logger logger;

    @Test
    public void simpleDockerfileWithoutParentDir() {
        new Expectations() {{
           params.getSourceDirectory(); result = "src/main/docker";
           params.getProject(); result = project;
           project.getBasedir(); result = "/project";
        }};

        String[] data = new String[] {
            null, "Dockerfile", "/project/src/main/docker", "/project/src/main/docker/Dockerfile",
            null, "/context/Dockerfile", "/context", "/context/Dockerfile",
            "/context", "Dockerfile", "/context", "/context/Dockerfile",
            "context", "Dockerfile", "/project/src/main/docker/context", "/project/src/main/docker/context/Dockerfile",
            "context", "/other/Dockerfile", "/project/src/main/docker/context", "/other/Dockerfile",
            "/context", "/other/Dockerfile", "/context", "/other/Dockerfile"
        };

        for (int i = 0; i < data.length; i+= 4) {
            BuildImageConfiguration config =
                new BuildImageConfiguration.Builder()
                    .contextDir(data[i])
                    .dockerFile(data[i + 1]).build();
            config.initAndValidate(logger);


            assertEquals(data[i + 2], config.getAbsoluteContextDirPath(params).getAbsolutePath());
            assertEquals(data[i + 3], config.getAbsoluteDockerFilePath(params).getAbsolutePath());
        }
    }

}
