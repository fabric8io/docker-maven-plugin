package io.fabric8.maven.docker.config.handler.compose;

import org.apache.maven.project.MavenProject;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 *
 */
public class ComposeUtilsTest {


    @Test
    public void resolveComposeFileWithAbsoluteComposeFile() throws Exception {
        String absComposeFile = "/absolute/path/to/docker-compose.yaml";

        assertEquals(new File(absComposeFile),
                ComposeUtils.resolveComposeFileAbsolutely(null, absComposeFile, null));
    }

    @Test
    public void resolveComposeFileWithRelativeComposeFileAndAbsoluteBaseDir() throws Exception {
        String relComposeFile = "relative/path/to/docker-compose.yaml";
        String absBaseDir = "/basedir/";

        assertEquals(new File(absBaseDir, relComposeFile),
                ComposeUtils.resolveComposeFileAbsolutely(absBaseDir, relComposeFile, null));
    }

    @Test
    public void resolveComposeFileWithRelativeComposeFileAndRelativeBaseDir() throws Exception {
        String relComposeFile = "relative/path/to/docker-compose.yaml";
        String relBaseDir = "basedir/";
        String absMavenProjectDir = "/absoute/path/to/maven/project";

        MavenProject project = mock(MavenProject.class);
        when(project.getBasedir()).thenReturn(new File(absMavenProjectDir));


        assertEquals(new File(new File(absMavenProjectDir, relBaseDir), relComposeFile),
                ComposeUtils.resolveComposeFileAbsolutely(relBaseDir, relComposeFile, project));
        verify(project).getBasedir();
    }
}