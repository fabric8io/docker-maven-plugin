package io.fabric8.maven.docker.util;

import org.junit.Ignore;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.assertEquals;

/**
 * Path manipulation tests
 */
public class DockerPathUtilTest {

    /**
     * A sample relative path, that does not begin or end with a forward slash
     */
    private final String RELATIVE_PATH = "relative/path";

    /**
     * A sample absolute path, which begins with a forward slash, but has no trailing slash
     */
    private final String ABS_BASE_DIR = "/base/directory";

    /**
     * A sample relative path (no different than {@link #RELATIVE_PATH}), provided as the member name is
     * self-documenting in the test.
     */
    private final String REL_BASE_DIR = "base/directory";

    @Test
    public void resolveAbsolutelyWithRelativePath() {
        String toResolve = RELATIVE_PATH;
        String absBaseDir = ABS_BASE_DIR;

        assertEquals(new File(absBaseDir + "/" + toResolve),
                DockerPathUtil.resolveAbsolutely(toResolve, absBaseDir));
    }

    @Test
    public void resolveAbsolutelyWithRelativePathAndTrailingSlash() {
        String toResolve = RELATIVE_PATH + "/";
        String absBaseDir = ABS_BASE_DIR;

        assertEquals(new File(absBaseDir + "/" + toResolve),
                DockerPathUtil.resolveAbsolutely(toResolve, absBaseDir));
    }

    @Test
    public void resolveAbsolutelyWithTrailingSlashWithRelativePath() {
        String toResolve = RELATIVE_PATH;
        String absBaseDir = ABS_BASE_DIR + "/";

        assertEquals(new File(absBaseDir + toResolve), DockerPathUtil.resolveAbsolutely(toResolve, absBaseDir));
    }

    @Test(expected = IllegalArgumentException.class)
    public void resolveAbsolutelyWithRelativePathAndRelativeBaseDir() throws IllegalArgumentException {
        DockerPathUtil.resolveAbsolutely(RELATIVE_PATH, REL_BASE_DIR);
    }

    /**
     * The supplied base directory is relative, but isn't used because the supplied path is absolute.  Should an
     * IAE be thrown, even though the base directory isn't used?
     */
    @Test
    public void resolveAbsolutelyWithAbsolutePathAndRelativeBaseDir() {
        String absolutePath = "/" + RELATIVE_PATH;
        assertEquals(new File(absolutePath), DockerPathUtil.resolveAbsolutely(absolutePath, REL_BASE_DIR));
    }

    @Test
    public void resolveAbsolutelyWithExtraSlashes() throws Exception {
        String toResolve = RELATIVE_PATH + "//";
        assertEquals(new File(ABS_BASE_DIR + "/" + RELATIVE_PATH),
                DockerPathUtil.resolveAbsolutely(toResolve, ABS_BASE_DIR));
    }

    @Test
    @Ignore("TODO: what does PathUtil do, if anything, when encountering backward slashes?")
    public void resolveAbsolutelyWithBackwardSlashes() throws Exception {
        String toResolve = RELATIVE_PATH.replace("/", "\\");

        assertEquals(new File(ABS_BASE_DIR + "/" + RELATIVE_PATH),
                DockerPathUtil.resolveAbsolutely(toResolve, ABS_BASE_DIR));
    }
}