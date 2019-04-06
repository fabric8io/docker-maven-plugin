package io.fabric8.maven.docker.config.handler.compose;

import java.io.File;

import mockit.Expectations;
import mockit.Mocked;
import mockit.VerificationsInOrder;
import org.apache.maven.project.MavenProject;
import org.junit.Test;

import static io.fabric8.maven.docker.util.PathTestUtil.DOT;
import static io.fabric8.maven.docker.util.PathTestUtil.SEP;
import static io.fabric8.maven.docker.util.PathTestUtil.createTmpFile;
import static io.fabric8.maven.docker.util.PathTestUtil.join;
import static org.junit.Assert.assertEquals;

/**
 *
 */

public class ComposeUtilsTest {

    private final String className = ComposeUtilsTest.class.getSimpleName();

    private final String ABS_BASEDIR = createTmpFile(className).getAbsolutePath();

    @Mocked
    private MavenProject project;

    @Test
    public void resolveComposeFileWithAbsoluteComposeFile() throws Exception {
        String absComposeFile = createTmpFile(className).getAbsolutePath() + SEP + "docker-compose.yaml";

        assertEquals(new File(absComposeFile),
                ComposeUtils.resolveComposeFileAbsolutely(null, absComposeFile, null));
    }

    @Test
    public void resolveComposeFileWithRelativeComposeFileAndAbsoluteBaseDir() throws Exception {
        String relComposeFile = join(SEP, "relative", "path", "to", "docker-compose.yaml");  // relative/path/to/docker-compose.yaml
        final String absMavenProjectDir = createTmpFile(className).getAbsolutePath();

        new Expectations() {{
            project.getBasedir();
            result = new File(absMavenProjectDir);
        }};

        assertEquals(new File(ABS_BASEDIR, relComposeFile),
                ComposeUtils.resolveComposeFileAbsolutely(ABS_BASEDIR, relComposeFile, project));

        new VerificationsInOrder() {{
            project.getBasedir();
        }};
    }

    @Test
    public void resolveComposeFileWithRelativeComposeFileAndRelativeBaseDir() throws Exception {
        String relComposeFile = join(SEP, "relative", "path", "to", "docker-compose.yaml");  // relative/path/to/docker-compose.yaml
        String relBaseDir = "basedir" + SEP;
        final String absMavenProjectDir = createTmpFile(className).getAbsolutePath();

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

    @Test
    public void resolveComposesFileWithRelativeComposeFileParentDirectory() throws Exception {
        String relComposeFile = join(SEP, DOT + DOT, "relative", "path", "to", "docker-compose.yaml");  // ../relative/path/to/docker-compose.yaml
        File tmpDir = createTmpFile(ComposeUtilsTest.class.getName());
        String absBaseDir = tmpDir.getAbsolutePath();

        assertEquals(new File(tmpDir.getParentFile(), relComposeFile.substring(3)),
                ComposeUtils.resolveComposeFileAbsolutely(absBaseDir, relComposeFile, null));
    }
}