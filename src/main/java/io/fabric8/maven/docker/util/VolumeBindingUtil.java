package io.fabric8.maven.docker.util;

import io.fabric8.maven.docker.config.RunVolumeConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static io.fabric8.maven.docker.util.DockerPathUtil.resolveAbsolutely;

/**
 * Utility methods for working with Docker volume bindings.
 */
public class VolumeBindingUtil {

    /**
     * Resolves relative paths in the supplied {@code bindingString}, and returns a binding string that has relative
     * paths replaced with absolute paths.  If the supplied {@code bindingString} does not contain a relative path, it
     * is returned unmodified.
     * <h3>Discussion:</h3>
     * <p>
     * Volumes may be defined inside of {@code service} blocks <a href="https://docs.docker.com/compose/compose-file/compose-file-v2/#volumes-volume_driver">
     * as documented here</a>:
     * </p>
     * <pre>
     * volumes:
     * # Just specify a path and let the Engine create a volume
     * - /var/lib/mysql
     *
     * # Specify an absolute path mapping
     * - /opt/data:/var/lib/mysql
     *
     * # Path on the host, relative to the Compose file
     * - ./cache:/tmp/cache
     *
     * # User-relative path
     * - ~/configs:/etc/configs/:ro
     *
     * # Named volume
     * - datavolume:/var/lib/mysql"
     * </pre>
     * <p>
     * This method only operates on volume strings that are relative: beginning with {@code ./}, {@code ../}, or
     * {@code ~}.  Relative paths beginning with {@code ./} or {@code ../} are absolutized relative to the supplied
     * {@code baseDir}, which <em>must</em> be absolute.  Paths beginning with {@code ~/} are interpreted relative to
     * {@code new File(System.getProperty( "user.home"))}, and {@code baseDir} is ignored.
     * </p>
     * <p>
     * Volume strings that do not begin with a {@code ./}, {@code ../}, or {@code ~} are returned as-is.
     * </p>
     * <h3>Examples:</h3>
     * <p>
     * Given {@code baseDir} equal to "/path/to/basedir" and a {@code bindingString} string equal to
     * "./reldir:/some/other/dir", this method returns {@code /path/to/basedir/reldir:/some/other/dir}
     * </p>
     * <p>
     * Given {@code baseDir} equal to "/path/to/basedir" and a {@code bindingString} string equal to
     * "../reldir:/some/other/dir", this method returns {@code /path/to/reldir:/some/other/dir}
     * </p>
     * <p>
     * Given {@code baseDir} equal to "/path/to/basedir" and a {@code bindingString} string equal to
     * "~/reldir:/some/other/dir", this method returns {@code /path/to/user/home/reldir:/some/other/dir}
     * </p>
     * <p>
     * Given {@code baseDir} equal to "/path/to/basedir" and a {@code bindingString} equal to
     * "src/test/docker:/some/other/dir", this method returns {@code /path/to/basedir/src/test/docker:/some/other/dir}
     * </p>
     * <p>
     * Given a {@code bindingString} equal to "foo:/some/other/dir", this method returns {@code foo:/some/other/dir},
     * because {@code foo} is considered to be a <em>named volume</em>, not a relative path.
     * </p>
     *
     * @param baseDir the base directory used to resolve relative paths (e.g. beginning with {@code ./}, {@code ../},
     *                {@code ~}) present in the {@code bindingString}; <em>must</em> be absolute
     * @param bindingString the volume string from the docker-compose file
     * @return the volume string, with any relative paths resolved as absolute paths
     * @throws IllegalArgumentException if the supplied {@code baseDir} is not absolute
     */
    public static String resolveRelativeVolumeBinding(File baseDir, String bindingString) {

        if (!baseDir.isAbsolute()) {
            throw new IllegalArgumentException("Base directory '" + baseDir + "' must be absolute.");
        }

        // a 'services:' -> service -> 'volumes:' may be formatted as:
        // (https://docs.docker.com/compose/compose-file/compose-file-v2/#volumes-volume_driver)
        //
        // volumes:
        //  # Just specify a path and let the Engine create a volume
        //  - /var/lib/mysql
        //
        //  # Specify an absolute path mapping
        //  - /opt/data:/var/lib/mysql
        //
        //  # Path on the host, relative to the Compose file
        //  - ./cache:/tmp/cache
        //
        //  # User-relative path
        //  - ~/configs:/etc/configs/:ro
        //
        //  # Named volume
        // - datavolume:/var/lib/mysql

        String[] pathParts = bindingString.split(":");
        String localPath = pathParts[0];
        String serverPath = (pathParts.length > 1) ? pathParts[1] : "";

        if (isRelativePath(localPath)) {
            File resolvedFile;
            if (isUserHomeRelativePath(localPath)) {
                resolvedFile = resolveAbsolutely(prepareUserHomeRelativePath(localPath), System.getProperty("user.home"));
            } else {
                resolvedFile = resolveAbsolutely(localPath, baseDir.getAbsolutePath());
            }
            try {
                localPath = resolvedFile.getCanonicalFile().getAbsolutePath();
            } catch (IOException e) {
                throw new RuntimeException("Unable to canonicalize '" + resolvedFile + "'");
            }
        }

        if (serverPath.length() > 0) {
            return String.format("%s:%s", localPath, serverPath);
        }

        return localPath;
    }

    /**
     * Iterates over each {@link RunVolumeConfiguration#getBind() binding} in the {@code volumeConfiguration}, and
     * resolves any relative paths in the binding strings using {@link #resolveRelativeVolumeBinding(File, String)}.
     *
     * @param baseDir the base directory used to resolve relative paths (e.g. beginning with {@code ./}, {@code ../},
     *                {@code ~}) present in the binding string; <em>must</em> be absolute
     * @param volumeConfiguration the volume configuration that may contain volume binding specifications
     * @throws IllegalArgumentException if the supplied {@code baseDir} is not absolute
     */
    public static void resolveRelativeVolumeBindings(File baseDir, RunVolumeConfiguration volumeConfiguration) {

        if (!baseDir.isAbsolute()) {
            throw new IllegalArgumentException("Base directory '" + baseDir + "' must be absolute.");
        }

        List<String> bindings = volumeConfiguration.getBind();

        if (bindings.isEmpty()) {
            return;
        }

        for (int i = 0; i < bindings.size(); i++) {
            bindings.set(i, resolveRelativeVolumeBinding(baseDir, bindings.get(i)));
        }
    }

    /**
     * Determines if the supplied volume binding path contains a relative path.  This is subtle, because volume
     * bindings may specify a named volume per the discussion below.
     * <h3>Discussion:</h3>
     * <p>
     * Volumes may be defined inside of {@code service} blocks <a href="https://docs.docker.com/compose/compose-file/compose-file-v2/#volumes-volume_driver">
     * as documented here</a>:
     * </p>
     * <pre>
     * volumes:
     * # Just specify a path and let the Engine create a volume
     * - /var/lib/mysql
     *
     * # Specify an absolute path mapping
     * - /opt/data:/var/lib/mysql
     *
     * # Path on the host, relative to the Compose file
     * - ./cache:/tmp/cache
     *
     * # User-relative path
     * - ~/configs:/etc/configs/:ro
     *
     * # Named volume
     * - datavolume:/var/lib/mysql"
     * </pre>
     * <p>
     * Volume binding paths that begin with {@code ./}, {@code ../}, or {@code ~} clearly represent a relative path.
     * However, binding paths that do not begin with those characters may represent a <em>named volume</em>.  For
     * example, the binding string {@code rel:/path/to/container/mountpoint} refers to the <em>named volume</em> {@code
     * rel}. Because it is desirable to fully support relative paths for volumes provided in a run configuration, this
     * method attempts to resolve the ambiguity between a <em>named volume</em> and a <em>relative path</em>.
     * </p>
     * <p>
     * Therefore, volume binding strings will be considered to contain a relative path when any of the following
     * conditions are true:
     * <ul>
     *     <li>the volume binding path begins with {@code ./}, {@code ../}, or {@code ~}</li>
     *     <li>the volume binding path contains the character {@code /} <em>and</em> {@code /} is not at index 0 of
     *         the volume binding path</li>
     * </ul>
     * </p>
     * <p>
     * If the binding string {@code rel:/path/to/container/mountpoint} is intended to represent {@code rel} as a
     * <em>relative path</em> and not as a <em>named volume</em>, then the binding string should be modified to contain
     * a forward slash like so: {@code rel/:/path/to/container/mountpoint}.  Another option would be to prefix {@code
     * rel} with a {@code ./} like so: {@code ./rel:/path/to/container/mountpoint}
     * </p>
     *
     *
     * @param candidatePath the candidate volume binding path
     * @return true if the candidate path is considered to be a relative path
     */
    static boolean isRelativePath(String candidatePath) {
        if (candidatePath.startsWith("/")) {
            return false;
        }

        if (candidatePath.startsWith("./") || candidatePath.startsWith("../")) {
            return true;
        }

        if (candidatePath.contains("/")) {
            return true;
        }

        if (isUserHomeRelativePath(candidatePath)) {
            return true;
        }

        return false;
    }

    /**
     * Returns true if the supplied path begins with {@code ~}.  This means that the path should be resolved relative
     * to the user's home directory.
     *
     * @param candidatePath the candidate path that may represent a path under the user's home directory
     * @return true if the path begins with {@code ~}
     */
    static boolean isUserHomeRelativePath(String candidatePath) {
        return candidatePath.startsWith("~");
    }

    private static String prepareUserHomeRelativePath(String userHomePath) {
        if (!(isUserHomeRelativePath(userHomePath))) {
            return userHomePath;
        }

        // Handle ~user and ~/path and ~

        if (userHomePath.equals("~")) {
            return "";
        }

        if (userHomePath.startsWith("~/")) {
            return userHomePath.substring(2);
        }

        // e.g. userHomePath = '~user/foo' we just want 'foo'
        if (userHomePath.contains("/")) {
            return userHomePath.substring(userHomePath.indexOf("/") + 1);
        }

        // otherwise userHomePath = '~user' and we can just return the empty string.

        return "";
    }
}
