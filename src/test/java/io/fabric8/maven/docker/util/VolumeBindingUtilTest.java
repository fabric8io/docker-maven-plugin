package io.fabric8.maven.docker.util;

import com.sun.org.apache.regexp.internal.REProgram;
import io.fabric8.maven.docker.config.RunVolumeConfiguration;
import org.junit.Test;

import java.io.File;

import static io.fabric8.maven.docker.util.VolumeBindingUtil.isRelativePath;
import static io.fabric8.maven.docker.util.VolumeBindingUtil.isUserHomeRelativePath;
import static io.fabric8.maven.docker.util.VolumeBindingUtil.resolveRelativeVolumeBinding;
import static io.fabric8.maven.docker.util.VolumeBindingUtil.resolveRelativeVolumeBindings;
import static java.util.Collections.singletonList;
import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 *
 */
public class VolumeBindingUtilTest {

    private static final File ABS_BASEDIR = new File("/absolute/basedir");
    private static final String RELATIVE_PATH = "./rel";
    private static final String USER_PATH = "~/relUser";
    private static final String USER_HOME = "~user";
    private static final String CONTAINER_PATH = "/path/to/container/dir";
    private final String BIND_STRING_FMT = "%s:%s";

    @Test(expected = IllegalArgumentException.class)
    public void relativeBaseDir() throws Exception {
        resolveRelativeVolumeBinding(new File("relative/"), null);
    }

    @Test
    public void testResolveRelativeVolumePath() throws Exception {
        String volumeString = String.format(BIND_STRING_FMT, RELATIVE_PATH, CONTAINER_PATH);

        // './rel:/path/to/container/dir' to '/absolute/basedir/rel:/path/to/container/dir'
        String relativizedVolumeString = resolveRelativeVolumeBinding(ABS_BASEDIR, volumeString);

        String expectedBindingString = String.format(BIND_STRING_FMT,
                new File(ABS_BASEDIR, stripLeadingPeriod(RELATIVE_PATH)), CONTAINER_PATH);
        assertEquals(expectedBindingString, relativizedVolumeString);
    }

    @Test
    public void testResolveUserVolumePath() throws Exception {
        String volumeString = String.format(BIND_STRING_FMT, USER_PATH, CONTAINER_PATH);

        // '~/rel:/path/to/container/dir' to '/user/home/rel:/path/to/container/dir'
        String relativizedVolumeString = resolveRelativeVolumeBinding(ABS_BASEDIR, volumeString);

        String expectedBindingString = String.format(BIND_STRING_FMT,
                new File(System.getProperty("user.home"), stripLeadingTilde(USER_PATH)), CONTAINER_PATH);
        assertEquals(expectedBindingString, relativizedVolumeString);
    }

    @Test
    public void testResolveUserHomeVolumePath() throws Exception {
        String volumeString = String.format(BIND_STRING_FMT, USER_HOME, CONTAINER_PATH);

        // '~user:/path/to/container/dir' to '/home/user:/path/to/container/dir'
        String relativizedVolumeString = resolveRelativeVolumeBinding(ABS_BASEDIR, volumeString);

        String expectedBindingString = String.format(BIND_STRING_FMT,
                new File(System.getProperty("user.home")), CONTAINER_PATH);
        assertEquals(expectedBindingString, relativizedVolumeString);
    }

    @Test
    public void testResolveNamedVolume() throws Exception {
        String volumeName = "volname";
        String volumeString = String.format(BIND_STRING_FMT, volumeName, CONTAINER_PATH);

        // volumeString should be untouched
        assertEquals(volumeString, resolveRelativeVolumeBinding(ABS_BASEDIR, volumeString));
    }

    @Test
    public void testResolveAbsolutePathMapping() throws Exception {
        String absolutePath = "/absolute/path";
        String volumeString = String.format(BIND_STRING_FMT, absolutePath, CONTAINER_PATH);

        // volumeString should be untouched
        assertEquals(volumeString, resolveRelativeVolumeBinding(ABS_BASEDIR, volumeString));
    }

    @Test
    public void testResolveSinglePath() throws Exception {
        String absolutePath = "/absolute/path";

        // volumeString should be untouched
        assertEquals(absolutePath, resolveRelativeVolumeBinding(ABS_BASEDIR, absolutePath));
    }

    @Test
    public void testResolveVolumeBindingsWithRunVolumeConfiguration() throws Exception {
        RunVolumeConfiguration.Builder builder = new RunVolumeConfiguration.Builder();
        builder.bind(singletonList(String.format(BIND_STRING_FMT, RELATIVE_PATH, CONTAINER_PATH)));
        RunVolumeConfiguration volumeConfiguration = builder.build();


        // './rel:/path/to/container/dir' to '/absolute/basedir/rel:/path/to/container/dir'
        resolveRelativeVolumeBindings(ABS_BASEDIR, volumeConfiguration);

        String expectedBindingString = String.format(BIND_STRING_FMT,
                join("", ABS_BASEDIR.getAbsolutePath(), stripLeadingPeriod(RELATIVE_PATH)), CONTAINER_PATH);
        assertEquals(expectedBindingString, volumeConfiguration.getBind().get(0));
    }

    @Test
    public void testResolveParentRelativeVolumePath() throws Exception {
        String relativePath = "." + RELATIVE_PATH; // '../rel'
        String volumeString = String.format(BIND_STRING_FMT, relativePath, CONTAINER_PATH);

        // '../rel:/path/to/container/dir to '/absolute/rel:/path/to/container/dir'
        String relativizedVolumeString = resolveRelativeVolumeBinding(ABS_BASEDIR, volumeString);

        String expectedBindingString = String.format(BIND_STRING_FMT,
                new File(ABS_BASEDIR.getParent(), stripLeadingPeriod(RELATIVE_PATH)), CONTAINER_PATH);
        assertEquals(expectedBindingString, relativizedVolumeString);
    }

    /**
     * The volume binding string: {@code rel:/path/to/container/mountpoint} is not resolved, because {@code rel} is
     * considered a <em>named volume</em>.
     */
    @Test
    public void testResolveRelativeVolumePathWithoutCurrentDirectory() throws Exception {
        String relativePath = "rel";
        String volumeString = String.format(BIND_STRING_FMT, relativePath, CONTAINER_PATH);

        // 'rel:/path/to/container/dir' to 'rel:/path/to/container/dir'
        String relativizedVolumeString = resolveRelativeVolumeBinding(ABS_BASEDIR, volumeString);

        String expectedBindingString = String.format(BIND_STRING_FMT, relativePath, CONTAINER_PATH);
        assertEquals(expectedBindingString, relativizedVolumeString);
    }

    /**
     * The volume binding string: {@code src/test/docker:/path/to/container/mountpoint} is resolved, because {@code src/
     * test/docker} is considered a <em>relative path</em>.
     */
    @Test
    public void testResolveRelativeVolumePathContainingSlashes() throws Exception {
        String relativePath = "src/test/docker";
        String volumeString = String.format(BIND_STRING_FMT, relativePath, CONTAINER_PATH);

        // 'src/test/docker:/path/to/container/dir' to '/absolute/basedir/src/test/docker:/path/to/container/dir'
        String relativizedVolumeString = resolveRelativeVolumeBinding(ABS_BASEDIR, volumeString);

        String expectedBindingString = String.format(BIND_STRING_FMT,
                new File(ABS_BASEDIR, relativePath), CONTAINER_PATH);
        assertEquals(expectedBindingString, relativizedVolumeString);
    }

    @Test
    public void testIsRelativePath() throws Exception {
        assertTrue(isRelativePath("rel/"));
        assertTrue(isRelativePath("src/test/docker"));
        assertTrue(isRelativePath("./rel"));
        assertTrue(isRelativePath("~/rel"));
        assertTrue(isRelativePath("../rel"));
        assertFalse(isRelativePath("rel")); // 'rel' is a named volume in this case
        assertFalse(isRelativePath("/abs")); // '/abs' is absolute
    }

    @Test
    public void testIsUserRelativeHomeDir() throws Exception {
        assertFalse(isUserHomeRelativePath("foo~bar"));
        assertFalse(isUserHomeRelativePath("foo~"));
        assertFalse(isUserHomeRelativePath("foo"));
        assertTrue(isUserHomeRelativePath("~foo"));
        assertTrue(isUserHomeRelativePath("~/user"));
        assertTrue(isUserHomeRelativePath("~user/dir"));
    }

    private static String join(String character, String... objects) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < objects.length;) {
            sb.append(objects[i]);
            if (i++ < objects.length) {
                sb.append(character);
            }
        }

        return sb.toString();
    }

    private static String stripLeadingPeriod(String path) {
        if (path.startsWith(".")) {
            return path.substring(1);
        }

        return path;
    }

    private static String stripLeadingTilde(String path) {
        if (path.startsWith("~")) {
            return path.substring(1);
        }

        return path;
    }




}