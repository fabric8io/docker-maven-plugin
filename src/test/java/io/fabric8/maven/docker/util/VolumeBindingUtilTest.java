package io.fabric8.maven.docker.util;

import io.fabric8.maven.docker.config.RunVolumeConfiguration;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.File;

import static io.fabric8.maven.docker.util.PathTestUtil.*;
import static io.fabric8.maven.docker.util.PathTestUtil.TMP_FILE_PRESERVE_MODE.DELETE_IMMEDIATELY;
import static io.fabric8.maven.docker.util.VolumeBindingUtil.*;
import static java.lang.String.format;
import static java.util.Collections.singletonList;

/**
 *
 */
class VolumeBindingUtilTest {

    private static final String CLASS_NAME = VolumeBindingUtilTest.class.getSimpleName();

    private static final String SEP = System.getProperty("file.separator");

    /**
     * An absolute file path that represents a base directory.  It is important for the JVM to create the file so
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
    private static final String BIND_STRING_FMT = "%s:%s";

    /**
     * Format of a volume binding string that contains access controls.  Format is: host binding string portion,
     * container binding string portion, access control portion.
     */
    private static final String BIND_STRING_WITH_ACCESS_FMT = "%s:%s:%s";

    /**
     * Access control portion of a volume binding string.
     */
    private static final String RO_ACCESS = "ro";

    /**
     * Insures the supplied base directory is absolute.
     */
    @Test
    void relativeBaseDir() {
        String format = format(BIND_STRING_FMT, RELATIVE_PATH, CONTAINER_PATH);
        File baseDir = new File("relative/");
       Assertions.assertThrows(IllegalArgumentException.class, ()-> resolveRelativeVolumeBinding(baseDir, format));
    }

    /**
     * Insures that a host volume binding string that contains a path relative to the current working directory is
     * resolved to the supplied base directory.
     */
    @Test
    void testResolveRelativeVolumePath() {
        String volumeString = format(BIND_STRING_FMT, RELATIVE_PATH, CONTAINER_PATH);

        // './rel:/path/to/container/dir' to '/absolute/basedir/rel:/path/to/container/dir'
        String relativizedVolumeString = resolveRelativeVolumeBinding(ABS_BASEDIR, volumeString);

        String expectedBindingString = format(BIND_STRING_FMT,
                new File(ABS_BASEDIR, stripLeadingPeriod(RELATIVE_PATH)), CONTAINER_PATH);
        Assertions.assertEquals(expectedBindingString, relativizedVolumeString);
    }

    /**
     * Insures that a host volume binding string that contains a path relative to the current working directory <em>and
     * </em> specifies access controls resolves to the supplied base directory <em>and</em> that the access controls are
     * preserved through the operation.
     */
    @Test
    void testResolveRelativeVolumePathWithAccessSpecifications() {
        String volumeString = format(BIND_STRING_WITH_ACCESS_FMT, RELATIVE_PATH, CONTAINER_PATH, RO_ACCESS);

        // './rel:/path/to/container/dir:ro' to '/absolute/basedir/rel:/path/to/container/dir:ro'
        String relativizedVolumeString = resolveRelativeVolumeBinding(ABS_BASEDIR, volumeString);

        String expectedBindingString = format(BIND_STRING_WITH_ACCESS_FMT,
                new File(ABS_BASEDIR, stripLeadingPeriod(RELATIVE_PATH)), CONTAINER_PATH, RO_ACCESS);
        Assertions.assertEquals(expectedBindingString, relativizedVolumeString);
    }

    /**
     * Insures that a host volume binding string that contains a path relative to the user's home directory resolves to
     * the user's home directory and not the supplied base directory.
     */
    @Test
    void testResolveUserVolumePath() {
        String volumeString = format(BIND_STRING_FMT, USER_PATH, CONTAINER_PATH);

        // '~/rel:/path/to/container/dir' to '/user/home/rel:/path/to/container/dir'
        String relativizedVolumeString = resolveRelativeVolumeBinding(new File("ignored"), volumeString);

        String expectedBindingString = format(BIND_STRING_FMT,
                new File(System.getProperty("user.home"), PathTestUtil.stripLeadingTilde(USER_PATH)), CONTAINER_PATH);
        Assertions.assertEquals(expectedBindingString, relativizedVolumeString);
    }

    /**
     * Resolving arbitrary user home paths, e.g. represented as {@code ~user}, is not supported.
     */
    @Test
    void testResolveUserHomeVolumePath() {
        String volumeString = format(BIND_STRING_FMT, USER_HOME, CONTAINER_PATH);

        // '~user:/path/to/container/dir' to '/home/user:/path/to/container/dir'
        File ignored = new File("ignored");
        Assertions.assertThrows(IllegalArgumentException.class, ()->resolveRelativeVolumeBinding(ignored, volumeString));
    }

    /**
     * Insures that volume binding strings referencing a named volume are preserved untouched.
     */
    @Test
    void testResolveNamedVolume() {
        String volumeName = "volname";
        String volumeString = format(BIND_STRING_FMT, volumeName, CONTAINER_PATH);

        // volumeString should be untouched
        Assertions.assertEquals(volumeString, resolveRelativeVolumeBinding(ABS_BASEDIR, volumeString));
    }

    /**
     * Insures that volume binding strings that contain an absolute path for the host portion are preserved untouched.
     */
    @Test
    void testResolveAbsolutePathMapping() {
        String absolutePath =
                createTmpFile(VolumeBindingUtilTest.class.getSimpleName(), DELETE_IMMEDIATELY).getAbsolutePath();
        String volumeString = format(BIND_STRING_FMT, absolutePath, CONTAINER_PATH);

        // volumeString should be untouched
        Assertions.assertEquals(volumeString, resolveRelativeVolumeBinding(ABS_BASEDIR, volumeString));
    }

    /**
     * Insures that volume binding strings with an absolute host portion are returned unchanged (no resolution necessary
     * because the the path is absolute)
     */
    @Test
    void testResolveSinglePath() {
        String absolutePath =
                createTmpFile(VolumeBindingUtilTest.class.getSimpleName(), DELETE_IMMEDIATELY).getAbsolutePath();

        // volumeString should be untouched
        Assertions.assertEquals(absolutePath, resolveRelativeVolumeBinding(ABS_BASEDIR, absolutePath));
    }

    /**
     * Insures that relative paths in the host portion of a volume binding string are properly resolved against a base
     * directory when present in a {@link RunVolumeConfiguration}.
     */
    @Test
    void testResolveVolumeBindingsWithRunVolumeConfiguration() {
        RunVolumeConfiguration.Builder builder = new RunVolumeConfiguration.Builder();
        builder.bind(singletonList(format(BIND_STRING_FMT, RELATIVE_PATH, CONTAINER_PATH)));
        RunVolumeConfiguration volumeConfiguration = builder.build();


        // './rel:/path/to/container/dir' to '/absolute/basedir/rel:/path/to/container/dir'
        resolveRelativeVolumeBindings(ABS_BASEDIR, volumeConfiguration);

        String expectedBindingString = format(BIND_STRING_FMT,
                join("", ABS_BASEDIR.getAbsolutePath(),
                        stripLeadingPeriod(RELATIVE_PATH)), CONTAINER_PATH);
        Assertions.assertEquals(expectedBindingString, volumeConfiguration.getBind().get(0));
    }

    /**
     * Insures that a relative path referencing the parent directory are properly resolved against a base directory.
     */
    @Test
    void testResolveParentRelativeVolumePath() {
        String relativePath = DOT + RELATIVE_PATH; // '../rel'
        String volumeString = format(BIND_STRING_FMT, relativePath, CONTAINER_PATH);

        // '../rel:/path/to/container/dir to '/absolute/rel:/path/to/container/dir'
        String relativizedVolumeString = resolveRelativeVolumeBinding(ABS_BASEDIR, volumeString);

        String expectedBindingString = format(BIND_STRING_FMT,
                new File(ABS_BASEDIR.getParent(), stripLeadingPeriod(RELATIVE_PATH)), CONTAINER_PATH);
        Assertions.assertEquals(expectedBindingString, relativizedVolumeString);
    }

    /**
     * Insures that a relative path referencing the parent directory are properly resolved against a base directory.
     */
    @Test
    @Disabled("TODO: fix this test, and DockerPathUtil as well")
    void testResolveParentRelativeVolumePathWithNoParent() {
        String relativePath = join(SEP, DOT + DOT, DOT + DOT, "rel"); // '../../rel'
        String volumeString = format(BIND_STRING_FMT, relativePath, CONTAINER_PATH);
        File baseDir = PathTestUtil.getFirstDirectory(ABS_BASEDIR);

        // '../../rel:/path/to/container/dir to '/absolute/rel:/path/to/container/dir'
        String relativizedVolumeString = resolveRelativeVolumeBinding(baseDir, volumeString);

        String expectedBindingString = format(BIND_STRING_FMT,
                new File(baseDir.getParent(), stripLeadingPeriod(RELATIVE_PATH)), CONTAINER_PATH);
        Assertions.assertEquals(expectedBindingString, relativizedVolumeString);
    }

    /**
     * The volume binding string: {@code rel:/path/to/container/mountpoint} is not resolved, because {@code rel} is
     * considered a <em>named volume</em>.
     */
    @Test
    void testResolveRelativeVolumePathWithoutCurrentDirectory() {
        String relativePath = "rel";
        String volumeString = format(BIND_STRING_FMT, relativePath, CONTAINER_PATH);

        // 'rel:/path/to/container/dir' to 'rel:/path/to/container/dir'
        String relativizedVolumeString = resolveRelativeVolumeBinding(ABS_BASEDIR, volumeString);

        String expectedBindingString = format(BIND_STRING_FMT, relativePath, CONTAINER_PATH);
        Assertions.assertEquals(expectedBindingString, relativizedVolumeString);
    }

    /**
     * The volume binding string: {@code src/test/docker:/path/to/container/mountpoint} is resolved, because {@code src/
     * test/docker} is considered a <em>relative path</em>.
     */
    @Test
    void testResolveRelativeVolumePathContainingSlashes() {
        String relativePath = "src" + SEP + "test" + SEP + "docker";
        String volumeString = format(BIND_STRING_FMT, relativePath, CONTAINER_PATH);

        // 'src/test/docker:/path/to/container/dir' to '/absolute/basedir/src/test/docker:/path/to/container/dir'
        String relativizedVolumeString = resolveRelativeVolumeBinding(ABS_BASEDIR, volumeString);

        String expectedBindingString = format(BIND_STRING_FMT,
                new File(ABS_BASEDIR, relativePath), CONTAINER_PATH);
        Assertions.assertEquals(expectedBindingString, relativizedVolumeString);
    }

    @Test
    void testIsRelativePath() {
        Assertions.assertTrue(isRelativePath("rel" + SEP));                            // rel/
        Assertions.assertTrue(isRelativePath(join(SEP, "src", "test", "docker")));         // src/test/docker
        Assertions.assertTrue(isRelativePath(join(SEP, DOT, "rel")));                      // ./rel
        Assertions.assertTrue(isRelativePath(join(SEP, TILDE, "rel")));                    // ~/rel
        Assertions.assertTrue(isRelativePath(join(SEP, DOT + DOT, "rel")));                // ../rel
        Assertions.assertFalse(isRelativePath("rel"));                                 // 'rel' is a named volume in this case
        Assertions.assertFalse(isRelativePath(
                createTmpFile(VolumeBindingUtilTest.class.getSimpleName(), DELETE_IMMEDIATELY)
                        .getAbsolutePath()));                                            // is absolute
    }

    @Test
    void testIsUserRelativeHomeDir() {
        Assertions.assertFalse(isUserHomeRelativePath(join(TILDE, "foo", "bar")));         // foo~bar
        Assertions.assertFalse(isUserHomeRelativePath("foo" + TILDE));                 // foo~
        Assertions.assertFalse(isUserHomeRelativePath("foo"));                         // foo
        Assertions.assertTrue(isUserHomeRelativePath(TILDE + "user"));                 // ~user
        Assertions.assertTrue(isUserHomeRelativePath(join(SEP, TILDE, "dir")));            // ~/dir
        Assertions.assertTrue(isUserHomeRelativePath(join(SEP, TILDE + "user", "dir")));   // ~user/dir
    }

    /**
     * Test windows paths even if the test JVM runtime is on *nix, specifically the consideration of an 'absolute'
     * path by {@link VolumeBindingUtil#isRelativePath(String)}.
     */
    @Test
    void testIsRelativePathForWindows() {
        Assertions.assertFalse(isRelativePath("C:\\foo"));                            // C:\foo
        Assertions.assertFalse(isRelativePath("x:\\bar"));                            // x:\bar
        Assertions.assertFalse(isRelativePath("C:\\"));                               // C:\
        Assertions.assertFalse(isRelativePath("\\"));                                 // \
    }

    /**
     * Insures that a host volume binding string that contains a windows path with .. is correctly canonicalized
     */
    @Test
    void testResolveAbsoluteWindowsVolumePath() {
        Assumptions.assumeTrue(System.getProperty("os.name").toLowerCase().startsWith("win"));
        String volumeString = format(BIND_STRING_FMT, "C:\\dir/subdir/../", CONTAINER_PATH);

        String relativizedVolumeString = resolveRelativeVolumeBinding(ABS_BASEDIR, volumeString);

        String expectedBindingString = format(BIND_STRING_FMT,
                "C:\\dir", CONTAINER_PATH);
        Assertions.assertEquals(expectedBindingString, relativizedVolumeString);
    }
}