package io.fabric8.maven.docker.util;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.File;

import static io.fabric8.maven.docker.util.PathTestUtil.*;

/**
 * Path manipulation tests
 */
class DockerPathUtilTest {

    private final String className = DockerPathUtilTest.class.getSimpleName();

    /**
     * A sample relative path, that does not begin or end with a file.separator character
     */
    private final String RELATIVE_PATH = "relative" + SEP + "path";

    /**
     * A sample absolute path, which begins with a file.separator character (or drive letter on the Windows platform)
     */
    private final String ABS_BASE_DIR = createTmpFile(className).getAbsolutePath();

    /**
     * A sample relative path (no different than {@link #RELATIVE_PATH}), provided as the member name is
     * self-documenting in the test.
     */
    private final String REL_BASE_DIR = "base" + SEP + "directory";

    @Test
    void resolveAbsolutelyWithRelativePath() {
        String toResolve = RELATIVE_PATH; // relative/path
        String absBaseDir = ABS_BASE_DIR; // /base/directory

        // '/base/directory' and 'relative/path' to '/base/directory/relative/path'
        Assertions.assertEquals(new File(absBaseDir + SEP + toResolve),
            DockerPathUtil.resolveAbsolutely(toResolve, absBaseDir));
    }

    @Test
    void resolveAbsolutelyWithRelativePathAndTrailingSlash() {
        String toResolve = RELATIVE_PATH + SEP; // relative/path/
        String absBaseDir = ABS_BASE_DIR;       // /base/directory

        // '/base/directory' and 'relative/path/' to '/base/directory/relative/path'
        Assertions.assertEquals(new File(absBaseDir + SEP + toResolve),
            DockerPathUtil.resolveAbsolutely(toResolve, absBaseDir));
    }

    @Test
    void resolveAbsolutelyWithTrailingSlashWithRelativePath() {
        String toResolve = RELATIVE_PATH;        // relative/path
        String absBaseDir = ABS_BASE_DIR + SEP;  // /base/directory/

        // '/base/directory/' and 'relative/path' to '/base/directory/relative/path'
        Assertions.assertEquals(new File(absBaseDir + toResolve),
            DockerPathUtil.resolveAbsolutely(toResolve, absBaseDir));
    }

    @Test
    void resolveAbsolutelyWithRelativePathAndRelativeBaseDir() {
        Assertions.assertThrows(IllegalArgumentException.class, () ->
            DockerPathUtil.resolveAbsolutely(RELATIVE_PATH, REL_BASE_DIR));
    }

    /**
     * The supplied base directory is relative, but isn't used because the supplied path is absolute.
     */
    @Test
    void resolveAbsolutelyWithAbsolutePathAndRelativeBaseDir() {
        String absolutePath = createTmpFile(className).getAbsolutePath();
        Assertions.assertEquals(new File(absolutePath), DockerPathUtil.resolveAbsolutely(absolutePath, REL_BASE_DIR));
    }

    @Test
    void resolveAbsolutelyWithExtraSlashes() {
        String toResolve = RELATIVE_PATH + SEP + SEP; // relative/path//

        // '/base/directory' and 'relative/path//' to '/base/directory/relative/path'
        Assertions.assertEquals(new File(ABS_BASE_DIR + SEP + RELATIVE_PATH),
            DockerPathUtil.resolveAbsolutely(toResolve, ABS_BASE_DIR));
    }

    @Test
    void resolveAbsolutelyWithRelativeParentPath() {
        String toResolve = join(SEP, DOT + DOT, RELATIVE_PATH); // ../relative/path

        // '/base/directory' and '../relative/path' to '/base/relative/path'
        Assertions.assertEquals(new File(new File(ABS_BASE_DIR).getParent(), RELATIVE_PATH),
            DockerPathUtil.resolveAbsolutely(toResolve, ABS_BASE_DIR));
    }

    @Test
    @Disabled("TODO: what does PathUtil do, if anything, when encountering backward slashes?")
    void resolveAbsolutelyWithBackwardSlashes() {
        String toResolve = RELATIVE_PATH.replace("/", "\\");

        Assertions.assertEquals(new File(ABS_BASE_DIR + "/" + RELATIVE_PATH),
            DockerPathUtil.resolveAbsolutely(toResolve, ABS_BASE_DIR));
    }

    @Test
    @Disabled("TODO: there is no parent to the root directory, so how can '../../relative/path' be resolved?")
    void resolveNonExistentPath() {
        String toResolve = join(SEP, DOT + DOT, DOT + DOT, "relative", "path");        // ../../relative/path
        String rootDir = getFirstDirectory(
            createTmpFile(DockerPathUtilTest.class.getName())).getAbsolutePath();  // /

        // '/' and '../../relative/path' to ??
        Assertions.assertEquals(new File(rootDir, RELATIVE_PATH), DockerPathUtil.resolveAbsolutely(toResolve, rootDir));
    }
}