package io.fabric8.maven.docker.config.handler.compose;

import mockit.Expectations;
import mockit.Mocked;
import mockit.VerificationsInOrder;
import mockit.integration.junit4.JMockit;
import org.apache.maven.project.MavenProject;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;

import static org.junit.Assert.assertEquals;

/**
 *
 */
@RunWith(JMockit.class)
public class ComposeUtilsTest {

    @Mocked
    private MavenProject project;

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
        final String absMavenProjectDir = "/absoute/path/to/maven/project";

        new Expectations() {{
            project.getBasedir();
            result = new File(absMavenProjectDir);
        }};

        assertEquals(new File(new File(absMavenProjectDir, relBaseDir), relComposeFile),
                ComposeUtils.resolveComposeFileAbsolutely(relBaseDir, relComposeFile, project));

        new VerificationsInOrder() {{
            project.getBasedir();
        }};
    }
}