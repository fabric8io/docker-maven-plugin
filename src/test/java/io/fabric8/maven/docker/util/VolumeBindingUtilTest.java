package io.fabric8.maven.docker.util;

import io.fabric8.maven.docker.config.RunVolumeConfiguration;
import org.junit.Ignore;
import org.junit.Test;

import java.io.File;

import static io.fabric8.maven.docker.util.PathTestUtil.DOT;
import static io.fabric8.maven.docker.util.PathTestUtil.TILDE;
import static io.fabric8.maven.docker.util.PathTestUtil.TMP_FILE_PRESERVE_MODE.DELETE_IMMEDIATELY;
import static io.fabric8.maven.docker.util.PathTestUtil.createTmpFile;
import static io.fabric8.maven.docker.util.PathTestUtil.join;
import static io.fabric8.maven.docker.util.PathTestUtil.stripLeadingPeriod;
import static io.fabric8.maven.docker.util.VolumeBindingUtil.isRelativePath;
import static io.fabric8.maven.docker.util.VolumeBindingUtil.isUserHomeRelativePath;
import static io.fabric8.maven.docker.util.VolumeBindingUtil.resolveRelativeVolumeBinding;
import static io.fabric8.maven.docker.util.VolumeBindingUtil.resolveRelativeVolumeBindings;
import static java.lang.String.format;
import static java.util.Collections.singletonList;
import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 *
 */
public class VolumeBindingUtilTest {

    private static final String CLASS_NAME = VolumeBindingUtilTest.class.getSimpleName();

    private static final String SEP = System.getProperty("file.separator");

    /**
     * An absolute file path that represents a base directory.  It is important for the JVM to create the the file so
     * that the absolute path representation of the test platform is used.
     */
    private static final File ABS_BASEDIR = createTmpFile(CLASS_NAME, DELETE_IMMEDIATELY);

    /**
     * Host portion of a volume binding string representing a directory relative to the current working directory.
     */
    private static final String RELATIVE_PATH = DOT + SEP + "rel";          // ./rel

    /**
     * Host portion of a volume binding string representing a directory relative to the current user's home directory.
     */
    private static final String USER_PATH = TILDE + SEP + "relUser";        // ~/relUser

    /**
     * Host portion of a volume binding string representing the current user's home directory.
     */
    private static final String USER_HOME = TILDE + "user";                 // ~user

    /**
     * Container portion of a volume binding string; the location in the container where the host portion is mounted.
     */
    private static final String CONTAINER_PATH = "/path/to/container/dir";

    /**
     * Format of a volume binding string that does not have any access controls.  Format is: host binding string
     * portion, container binding string portion
     */
    private final String BIND_STRING_FMT = "%s:%s";

    /**
     * Format of a volume binding string that contains access controls.  Format is: host binding string portion,
     * container binding string portion, access control portion.
     */
    private final String BIND_STRING_WITH_ACCESS_FMT = "%s:%s:%s";

    /**
     * Access control portion of a volume binding string.
     */
    private final String RO_ACCESS = "ro";

    /**
     * Insures the supplied base directory is absolute.
     */
    @Test(expected = IllegalArgumentException.class)
    public void relativeBaseDir() {
        resolveRelativeVolumeBinding(new File("relative/"),
                format(BIND_STRING_FMT, RELATIVE_PATH, CONTAINER_PATH));
    }

    /**
     * Insures that a host volume binding string that contains a path relative to the current working directory is
     * resolved to the supplied base directory.
     */
    @Test
    public void testResolveRelativeVolumePath() {
        String volumeString = format(BIND_STRING_FMT, RELATIVE_PATH, CONTAINER_PATH);

        // './rel:/path/to/container/dir' to '/absolute/basedir/rel:/path/to/container/dir'
        String relativizedVolumeString = resolveRelativeVolumeBinding(ABS_BASEDIR, volumeString);

        String expectedBindingString = format(BIND_STRING_FMT,
                new File(ABS_BASEDIR, stripLeadingPeriod(RELATIVE_PATH)), CONTAINER_PATH);
        assertEquals(expectedBindingString, relativizedVolumeString);
    }

    /**
     * Insures that a host volume binding string that contains a path relative to the current working directory <em>and
     * </em> specifies access controls resolves to the supplied base directory <em>and</em> that the access controls are
     * preserved through the operation.
     */
    @Test
    public void testResolveRelativeVolumePathWithAccessSpecifications() {
        String volumeString = format(BIND_STRING_WITH_ACCESS_FMT, RELATIVE_PATH, CONTAINER_PATH, RO_ACCESS);

        // './rel:/path/to/container/dir:ro' to '/absolute/basedir/rel:/path/to/container/dir:ro'
        String relativizedVolumeString = resolveRelativeVolumeBinding(ABS_BASEDIR, volumeString);

        String expectedBindingString = format(BIND_STRING_WITH_ACCESS_FMT,
                new File(ABS_BASEDIR, stripLeadingPeriod(RELATIVE_PATH)), CONTAINER_PATH, RO_ACCESS);
        assertEquals(expectedBindingString, relativizedVolumeString);
    }

    /**
     * Insures that a host volume binding string that contains a path relative to the user's home directory resolves to
     * the user's home directory and not the supplied base directory.
     */
    @Test
    public void testResolveUserVolumePath() {
        String volumeString = format(BIND_STRING_FMT, USER_PATH, CONTAINER_PATH);

        // '~/rel:/path/to/container/dir' to '/user/home/rel:/path/to/container/dir'
        String relativizedVolumeString = resolveRelativeVolumeBinding(new File("ignored"), volumeString);

        String expectedBindingString = format(BIND_STRING_FMT,
                new File(System.getProperty("user.home"), PathTestUtil.stripLeadingTilde(USER_PATH)), CONTAINER_PATH);
        assertEquals(expectedBindingString, relativizedVolumeString);
    }

    /**
     * Resolving arbitrary user home paths, e.g. represented as {@code ~user}, is not supported.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testResolveUserHomeVolumePath() {
        String volumeString = format(BIND_STRING_FMT, USER_HOME, CONTAINER_PATH);

        // '~user:/path/to/container/dir' to '/home/user:/path/to/container/dir'
        String relativizedVolumeString = resolveRelativeVolumeBinding(new File("ignored"), volumeString);
    }

    /**
     * Insures that volume binding strings referencing a named volume are preserved untouched.
     */
    @Test
    public void testResolveNamedVolume() throws Exception {
        String volumeName = "volname";
        String volumeString = format(BIND_STRING_FMT, volumeName, CONTAINER_PATH);

        // volumeString should be untouched
        assertEquals(volumeString, resolveRelativeVolumeBinding(ABS_BASEDIR, volumeString));
    }

    /**
     * Insures that volume binding strings that contain an absolute path for the host portion are preserved untouched.
     */
    @Test
    public void testResolveAbsolutePathMapping() {
        String absolutePath =
                createTmpFile(VolumeBindingUtilTest.class.getSimpleName(), DELETE_IMMEDIATELY).getAbsolutePath();
        String volumeString = format(BIND_STRING_FMT, absolutePath, CONTAINER_PATH);

        // volumeString should be untouched
        assertEquals(volumeString, resolveRelativeVolumeBinding(ABS_BASEDIR, volumeString));
    }

    /**
     * Insures that volume binding strings with an absolute host portion are returned unchanged (no resolution necessary
     * because the the path is absolute)
     */
    @Test
    public void testResolveSinglePath() {
        String absolutePath =
                createTmpFile(VolumeBindingUtilTest.class.getSimpleName(), DELETE_IMMEDIATELY).getAbsolutePath();

        // volumeString should be untouched
        assertEquals(absolutePath, resolveRelativeVolumeBinding(ABS_BASEDIR, absolutePath));
    }

    /**
     * Insures that relative paths in the host portion of a volume binding string are properly resolved against a base
     * directory when present in a {@link RunVolumeConfiguration}.
     */
    @Test
    public void testResolveVolumeBindingsWithRunVolumeConfiguration() {
        RunVolumeConfiguration.Builder builder = new RunVolumeConfiguration.Builder();
        builder.bind(singletonList(format(BIND_STRING_FMT, RELATIVE_PATH, CONTAINER_PATH)));
        RunVolumeConfiguration volumeConfiguration = builder.build();


        // './rel:/path/to/container/dir' to '/absolute/basedir/rel:/path/to/container/dir'
        resolveRelativeVolumeBindings(ABS_BASEDIR, volumeConfiguration);

        String expectedBindingString = format(BIND_STRING_FMT,
                join("", ABS_BASEDIR.getAbsolutePath(),
                        stripLeadingPeriod(RELATIVE_PATH)), CONTAINER_PATH);
        assertEquals(expectedBindingString, volumeConfiguration.getBind().get(0));
    }

    /**
     * Insures that a relative path referencing the parent directory are properly resolved against a base directory.
     */
    @Test
    public void testResolveParentRelativeVolumePath() {
        String relativePath = DOT + RELATIVE_PATH; // '../rel'
        String volumeString = format(BIND_STRING_FMT, relativePath, CONTAINER_PATH);

        // '../rel:/path/to/container/dir to '/absolute/rel:/path/to/container/dir'
        String relativizedVolumeString = resolveRelativeVolumeBinding(ABS_BASEDIR, volumeString);

        String expectedBindingString = format(BIND_STRING_FMT,
                new File(ABS_BASEDIR.getParent(), stripLeadingPeriod(RELATIVE_PATH)), CONTAINER_PATH);
        assertEquals(expectedBindingString, relativizedVolumeString);
    }

    /**
     * Insures that a relative path referencing the parent directory are properly resolved against a base directory.
     */
    @Test
    @Ignore("TODO: fix this test, and DockerPathUtil as well")
    public void testResolveParentRelativeVolumePathWithNoParent() {
        String relativePath = join(SEP, DOT + DOT, DOT + DOT, "rel"); // '../../rel'
        String volumeString = format(BIND_STRING_FMT, relativePath, CONTAINER_PATH);
        File baseDir = PathTestUtil.getFirstDirectory(ABS_BASEDIR);

        // '../../rel:/path/to/container/dir to '/absolute/rel:/path/to/container/dir'
        String relativizedVolumeString = resolveRelativeVolumeBinding(baseDir, volumeString);

        String expectedBindingString = format(BIND_STRING_FMT,
                new File(baseDir.getParent(), stripLeadingPeriod(RELATIVE_PATH)), CONTAINER_PATH);
        assertEquals(expectedBindingString, relativizedVolumeString);
    }

    /**
     * The volume binding string: {@code rel:/path/to/container/mountpoint} is not resolved, because {@code rel} is
     * considered a <em>named volume</em>.
     */
    @Test
    public void testResolveRelativeVolumePathWithoutCurrentDirectory() throws Exception {
        String relativePath = "rel";
        String volumeString = format(BIND_STRING_FMT, relativePath, CONTAINER_PATH);

        // 'rel:/path/to/container/dir' to 'rel:/path/to/container/dir'
        String relativizedVolumeString = resolveRelativeVolumeBinding(ABS_BASEDIR, volumeString);

        String expectedBindingString = format(BIND_STRING_FMT, relativePath, CONTAINER_PATH);
        assertEquals(expectedBindingString, relativizedVolumeString);
    }

    /**
     * The volume binding string: {@code src/test/docker:/path/to/container/mountpoint} is resolved, because {@code src/
     * test/docker} is considered a <em>relative path</em>.
     */
    @Test
    public void testResolveRelativeVolumePathContainingSlashes() throws Exception {
        String relativePath = "src" + SEP + "test" + SEP + "docker";
        String volumeString = format(BIND_STRING_FMT, relativePath, CONTAINER_PATH);

        // 'src/test/docker:/path/to/container/dir' to '/absolute/basedir/src/test/docker:/path/to/container/dir'
        String relativizedVolumeString = resolveRelativeVolumeBinding(ABS_BASEDIR, volumeString);

        String expectedBindingString = format(BIND_STRING_FMT,
                new File(ABS_BASEDIR, relativePath), CONTAINER_PATH);
        assertEquals(expectedBindingString, relativizedVolumeString);
    }

    @Test
    public void testIsRelativePath() throws Exception {
        assertTrue(isRelativePath("rel" + SEP));                            // rel/
        assertTrue(isRelativePath(join(SEP, "src", "test", "docker")));         // src/test/docker
        assertTrue(isRelativePath(join(SEP, DOT, "rel")));                      // ./rel
        assertTrue(isRelativePath(join(SEP, TILDE, "rel")));                    // ~/rel
        assertTrue(isRelativePath(join(SEP, DOT + DOT, "rel")));                // ../rel
        assertFalse(isRelativePath("rel"));                                 // 'rel' is a named volume in this case
        assertFalse(isRelativePath(
                createTmpFile(VolumeBindingUtilTest.class.getSimpleName(), DELETE_IMMEDIATELY)
                        .getAbsolutePath()));                                            // is absolute
    }

    @Test
    public void testIsUserRelativeHomeDir() throws Exception {
        assertFalse(isUserHomeRelativePath(join(TILDE, "foo", "bar")));         // foo~bar
        assertFalse(isUserHomeRelativePath("foo" + TILDE));                 // foo~
        assertFalse(isUserHomeRelativePath("foo"));                         // foo
        assertTrue(isUserHomeRelativePath(TILDE + "user"));                 // ~user
        assertTrue(isUserHomeRelativePath(join(SEP, TILDE, "dir")));            // ~/dir
        assertTrue(isUserHomeRelativePath(join(SEP, TILDE + "user", "dir")));   // ~user/dir
    }

    /**
     * Test windows paths even if the test JVM runtime is on *nix, specifically the consideration of an 'absolute'
     * path by {@link VolumeBindingUtil#isRelativePath(String)}.
     */
    @Test
    public void testIsRelativePathForWindows() {
        assertFalse(isRelativePath("C:\\foo"));                            // C:\foo
        assertFalse(isRelativePath("x:\\bar"));                            // x:\bar
        assertFalse(isRelativePath("C:\\"));                               // C:\
        assertFalse(isRelativePath("\\"));                                 // \
    }
}