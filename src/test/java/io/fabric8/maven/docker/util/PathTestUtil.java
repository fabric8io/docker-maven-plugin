package io.fabric8.maven.docker.util;

import java.io.File;
import java.io.IOException;

import io.fabric8.maven.docker.model.JsonParsingException;

import static org.junit.Assert.assertTrue;

/**
 * Utility methods and constants for path-related tests
 */
public class PathTestUtil {

    /**
     * A dot representing the current working directory
     */
    public static final String DOT = ".";

    /**
     * A tilde representing the current user's home directory
     */
    public static final String TILDE = "~";

    /**
     * The current runtime platform file separator
     */
    public static final String SEP = System.getProperty("file.separator");

    /**
     * Joins the supplied strings.
     *
     * @param joinWith the string used to join the strings
     * @param objects the strings to be joined
     * @return the joined strings
     */
    public static String join(String joinWith, String... objects) {
        return join(joinWith, false, false, objects);
    }

    /**
     * Joins the supplied strings, optionally prefixing and postfixing the returned string.
     *
     * @param joinWith the string used to join the strings
     * @param prefix prefix the returned string with {@code joinWith}
     * @param postfix postfix the returned string with {@code joinWith}
     * @param objects the strings to be joined
     * @return the joined strings
     */
    public static String join(String joinWith, boolean prefix, boolean postfix, String... objects) {
        StringBuilder sb = null;
        if (prefix) {
            sb = new StringBuilder(joinWith);
        } else {
            sb = new StringBuilder();
        }

        for (int i = 0; i < objects.length; ) {
            sb.append(objects[i]);
            if (i++ < objects.length) {
                sb.append(joinWith);
            }
        }

        if (postfix) {
            sb.append(joinWith);
        }

        return sb.toString();
    }

    /**
     * Strips "." off of the {@code path}, if present.
     *
     * @param path the path which may begin with a "."
     * @return the path stripped of a "."
     */
    public static String stripLeadingPeriod(String path) {
        if (path.startsWith(DOT)) {
            return path.substring(1);
        }

        return path;
    }

    /**
     * Strips "~" off of the {@code path}, if present.
     *
     * @param path the path which may begin with a "~"
     * @return the path stripped of a "~"
     */
    public static String stripLeadingTilde(String path) {
        if (path.startsWith(TILDE)) {
            return path.substring(1);
        }

        return path;
    }

    /**
     * Creates a unique file under {@code java.io.tmpdir} and returns the {@link File#getCanonicalFile() canonical}
     * {@code File}.  The file is deleted on exit.  This methodology
     * <ol>
     *   <li>guarantees a unique file name,</li>
     *   <li>doesn't clutter the filesystem with test-related directories or files,</li>
     *   <li>returns an absolute path (important for relative volume binding strings),
     *   <li>and returns a canonical file name.</li>
     * </ol>
     *
     * @param nameHint a string used to help create the temporary file name, may be {@code null}
     * @return the temporary file
     */
    public static File createTmpFile(String nameHint) {
        return createTmpFile(nameHint, TMP_FILE_PRESERVE_MODE.DELETE_ON_EXIT);
    }

    /**
     * Creates a unique file under {@code java.io.tmpdir} and returns the {@link File#getCanonicalFile() canonical}
     * {@code File}.  The optional {@code preserveMode} parameter dictates who is responsible for deleting the created
     * file, and when.  This methodology
     * <ol>
     *   <li>guarantees a unique file name,</li>
     *   <li>doesn't clutter the filesystem with test-related directories or files,</li>
     *   <li>returns an absolute path (important for relative volume binding strings),
     *   <li>and returns a canonical file name.</li>
     * </ol>
     *
     * @param nameHint a string used to help create the temporary file name, may be {@code null}
     * @param preserveMode mechanism for handling the clean up of files created by this method, may be {@code null}
     *                     which is equivalent to {@link TMP_FILE_PRESERVE_MODE#DELETE_ON_EXIT}
     * @return the absolute temporary file, which may not exist depending on the {@code preserveMode}
     */
    public static File createTmpFile(String nameHint, TMP_FILE_PRESERVE_MODE preserveMode) {
        try {
            File tmpFile = File.createTempFile(nameHint, ".tmp");
            assertTrue("The created temporary file " + tmpFile + " is not absolute!", tmpFile.isAbsolute());
            if (preserveMode != null) {
                switch (preserveMode) {
                    case DELETE_IMMEDIATELY:
                        assertTrue("Unable to delete temporary file " + tmpFile, tmpFile.delete());
                        break;
                    case DELETE_ON_EXIT:
                        tmpFile.deleteOnExit();
                        break;
                    // PRESERVE is a no-op
                }
            } else {
                // default when preserveMode is null
                tmpFile.deleteOnExit();
            }
            return tmpFile.getCanonicalFile();
        } catch (IOException e) {
            throw new JsonParsingException("Unable to create or canonicalize temporary directory");
        }
    }

    public static File getFirstDirectory(File file) {
        File result = file;
        while (result.getParentFile() != null) {
            result = result.getParentFile();
        }

        return result;
    }

    /**
     * Modes for handling the removal of created temporary files
     */
    public enum TMP_FILE_PRESERVE_MODE {

        /**
         * Deletes the created file immediately
         */
        DELETE_IMMEDIATELY,

        /**
         * Asks the JVM to delete the file on exit
         */
        DELETE_ON_EXIT,

        /**
         * Preserve the file, do not delete it.  The caller is responsible for clean up.
         */
        PRESERVE
    };
}
