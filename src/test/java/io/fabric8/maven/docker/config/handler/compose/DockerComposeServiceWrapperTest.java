package io.fabric8.maven.docker.config.handler.compose;

import org.junit.Test;

import java.io.File;

import static io.fabric8.maven.docker.config.handler.compose.DockerComposeServiceWrapper.resolveRelativeVolumeBinding;
import static org.junit.Assert.assertEquals;

/**
 *
 */
public class DockerComposeServiceWrapperTest {

    private File baseDir = new File("/absolute/basedir");
    private String relativePath = "./rel";
    private String userPath = "~/rel";
    private String bindingPath = "/path/to/container/dir";

    @Test(expected = IllegalArgumentException.class)
    public void relativeBaseDir() throws Exception {
        resolveRelativeVolumeBinding(new File("relative/"), null);
    }

    @Test
    public void testResolveRelativeVolumePath() throws Exception {
        String volumeString = String.format("%s:%s", relativePath, bindingPath);

        // './rel:/path/to/container/dir' to '/absolute/basedir/rel:/path/to/container/dir'
        String relativizedVolumeString = resolveRelativeVolumeBinding(baseDir, volumeString);

        assertEquals(String.format("%s:%s", new File(baseDir, relativePath.substring(2)), bindingPath),
                relativizedVolumeString);
    }

    @Test
    public void testResolveUserVolumePath() throws Exception {
        String volumeString = String.format("%s:%s", userPath, bindingPath);

        // '~/rel:/path/to/container/dir' to '/user/home/rel:/path/to/container/dir'
        String relativizedVolumeString = resolveRelativeVolumeBinding(baseDir, volumeString);

        assertEquals(String.format("%s:%s",
                    new File(System.getProperty("user.home"), relativePath.substring(2)), bindingPath),
                relativizedVolumeString);
    }

    @Test
    public void testResolveNamedVolume() throws Exception {
        String volumeName = "volname";
        String volumeString = String.format("%s:%s", volumeName, bindingPath);

        // volumeString should be untouched
        assertEquals(volumeString, resolveRelativeVolumeBinding(baseDir, volumeString));
    }

    @Test
    public void testResolveAbsolutePathMapping() throws Exception {
        String absolutePath = "/absolute/path";
        String volumeString = String.format("%s:%s", absolutePath, bindingPath);

        // volumeString should be untouched
        assertEquals(volumeString, resolveRelativeVolumeBinding(baseDir, volumeString));
    }

    @Test
    public void testResolveSinglePath() throws Exception {
        String absolutePath = "/absolute/path";

        // volumeString should be untouched
        assertEquals(absolutePath, resolveRelativeVolumeBinding(baseDir, absolutePath));
    }
}