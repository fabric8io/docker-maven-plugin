package io.fabric8.maven.docker.config;

import io.fabric8.maven.docker.util.Logger;
import io.fabric8.maven.docker.util.MojoParameters;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;

/**
 * @author roland
 * @since 2019-04-21
 */
@ExtendWith(MockitoExtension.class)
class BuildImageConfigurationWithMavenDepsTest {

    @Mock
    private MojoParameters params;

    @Mock
    private MavenProject project;

    @Mock
    Logger logger;

    @Test
    void simpleDockerfileWithoutParentDir() {
        Mockito.doReturn("src/main/docker").when(params).getSourceDirectory();
        Mockito.doReturn(project).when(params).getProject();
        Mockito.doReturn(new File("/project")).when(project).getBasedir();

        String[] data = new String[] {
            null, "Dockerfile", "/project/src/main/docker", "/project/src/main/docker/Dockerfile",
            null, "/context/Dockerfile", "/context", "/context/Dockerfile",
            "/context", "Dockerfile", "/context", "/context/Dockerfile",
            "context", "Dockerfile", "/project/src/main/docker/context", "/project/src/main/docker/context/Dockerfile",
            "context", "/other/Dockerfile", "/project/src/main/docker/context", "/other/Dockerfile",
            "/context", "/other/Dockerfile", "/context", "/other/Dockerfile"
        };

        // If the tests are run on Windows, the expected paths need to be adjusted.
        // On platforms that use the Unix convention, the following does not actually change
        // the test data.
        for (int i = 0; i < data.length; ++i) {
            if(data[i] != null) {
                File file = new File(data[i]);
                if(data[i].startsWith("/")) {
                    file = file.getAbsoluteFile();
                }
                data[i] = file.getPath();
            }
        }

        for (int i = 0; i < data.length; i+= 4) {
            BuildImageConfiguration config =
                new BuildImageConfiguration.Builder()
                    .contextDir(data[i])
                    .dockerFile(data[i + 1]).build();
            config.initAndValidate(logger);

            Assertions.assertEquals(data[i + 2], config.getAbsoluteContextDirPath(params).getAbsolutePath());
            Assertions.assertEquals(data[i + 3], config.getAbsoluteDockerFilePath(params).getAbsolutePath());
        }
    }

}
