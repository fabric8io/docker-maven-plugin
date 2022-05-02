package io.fabric8.maven.docker.config.handler.compose;

import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;

import static io.fabric8.maven.docker.util.PathTestUtil.*;

/**
 *
 */

@ExtendWith(MockitoExtension.class)
class ComposeUtilsTest {

    private final String className = ComposeUtilsTest.class.getSimpleName();

    private final String ABS_BASEDIR = createTmpFile(className).getAbsolutePath();

    @Mock
    private MavenProject project;

    @Test
    void resolveComposeFileWithAbsoluteComposeFile() {
        String absComposeFile = createTmpFile(className).getAbsolutePath() + SEP + "docker-compose.yaml";

        Assertions.assertEquals(new File(absComposeFile),
            ComposeUtils.resolveComposeFileAbsolutely(null, absComposeFile, null));
    }

    @Test
    void resolveComposeFileWithRelativeComposeFileAndAbsoluteBaseDir() {
        String relComposeFile = join(SEP, "relative", "path", "to", "docker-compose.yaml");  // relative/path/to/docker-compose.yaml
        final String absMavenProjectDir = createTmpFile(className).getAbsolutePath();

        Mockito.doReturn(new File(absMavenProjectDir)).when(project).getBasedir();

        Assertions.assertEquals(new File(ABS_BASEDIR, relComposeFile),
            ComposeUtils.resolveComposeFileAbsolutely(ABS_BASEDIR, relComposeFile, project));

        Mockito.verify(project).getBasedir();
    }

    @Test
    void resolveComposeFileWithRelativeComposeFileAndRelativeBaseDir() {
        String relComposeFile = join(SEP, "relative", "path", "to", "docker-compose.yaml");  // relative/path/to/docker-compose.yaml
        String relBaseDir = "basedir" + SEP;
        final String absMavenProjectDir = createTmpFile(className).getAbsolutePath();

        Mockito.doReturn(new File(absMavenProjectDir)).when(project).getBasedir();

        Assertions.assertEquals(new File(new File(absMavenProjectDir, relBaseDir), relComposeFile),
            ComposeUtils.resolveComposeFileAbsolutely(relBaseDir, relComposeFile, project));

        Mockito.verify(project).getBasedir();
    }

    @Test
    void resolveComposesFileWithRelativeComposeFileParentDirectory() {
        String relComposeFile = join(SEP, DOT + DOT, "relative", "path", "to", "docker-compose.yaml");  // ../relative/path/to/docker-compose.yaml
        File tmpDir = createTmpFile(ComposeUtilsTest.class.getName());
        String absBaseDir = tmpDir.getAbsolutePath();

        Assertions.assertEquals(new File(tmpDir.getParentFile(), relComposeFile.substring(3)),
            ComposeUtils.resolveComposeFileAbsolutely(absBaseDir, relComposeFile, null));
    }
}