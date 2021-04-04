package io.fabric8.maven.docker.util;

import io.fabric8.maven.docker.config.RunVolumeConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.regex.Pattern;

import static io.fabric8.maven.docker.util.DockerPathUtil.resolveAbsolutely;
import static io.fabric8.maven.docker.util.EnvUtil.getUserHome;

/**
 * Utility methods for working with Docker volume bindings.
 * <p>
 * This class provides explicit support for relative binding paths.  This means that the plugin configuration or
 * docker compose file can specify a relative path when configuring a volume binding.  Methods in this class will
 * examine volume binding strings in a {@link RunVolumeConfiguration} and resolve any relative paths in the host portion
 * of volume bindings.  Examples of relative bindings include:
 * <dl>
 *     <dd>A host path relative to the current working directory</dd>
 *     <dt>./relative/path:/absolute/container/path</dt>
 *
 *     <dd>A host path relative to the current working directory</dd>
 *     <dt>relative/path/:/absolute/container/path</dt>
 *
 *     <dd>A host path relative to the parent of the current working directory</dd>
 *     <dt>../relative/path:/absolute/container/path</dt>
 *
 *     <dd>A host path equal to the current user's home directory</dd>
 *     <dt>~:/absolute/container/path</dt>
 *
 *     <dd>A host path relative to the current user's home directory</dd>
 *     <dt>~/relative/path:/absolute/container/path</dt>
 * </dl>
 * </p>
 * <p>
 * Understand that the following is <em>not</em> considered a relative binding path, and is instead interpreted as a
 * <em>named volume</em>:
 * <dl>
 *     <dd>{@code rel} is interpreted as a <em>named volume</em>.  Use {@code ./rel} or {@code rel/} to have it
 *         interpreted as a relative path.</dd>
 *     <dt>rel:/absolute/container/path</dt>
 * </dl>
 * </p>
 * <p>
 * Volume bindings that specify an absolute path for the host portion are preserved and returned unmodified.
 * </p>
 */
public class VolumeBindingUtil {

    /**
     * A dot representing the current working directory
     */
    private static final String DOT = ".";

    /**
     * A tilde representing the current user's home directory
     */
    private static final String TILDE = "~";

    /**
     * The current runtime platform file separator, '/' for *nix, '\' for Windows
     */
    private static final String RUNTIME_SEP = System.getProperty("file.separator");

    /**
     * Windows file separator: '\'
     */
    private static final String WINDOWS_SEP = "\\";

    /**
     * Unix file separator '/'
     */
    private static final String UNIX_SEP = "/";

    /**
     * Matches a windows drive letter followed by a colon and backwards slash.  For example, will match:
     * 'C:\' or 'x:\'.
     */
    private static final Pattern WINDOWS_DRIVE_PATTERN = Pattern.compile("^[A-Za-z]:\\\\.*");

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
     * {@code baseDir}, which <em>must</em> be absolute.  Paths beginning with {@code ~} are interpreted relative to
     * {@code new File(System.getProperty("user.home"))}, and {@code baseDir} is ignored.
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
     * "~/reldir:/some/other/dir", this method returns {@code /home/user/reldir:/some/other/dir}
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

        if (isRelativePath(localPath)) {
            File resolvedFile;
            if (isUserHomeRelativePath(localPath)) {
                resolvedFile = resolveAbsolutely(prepareUserHomeRelativePath(localPath), getUserHome());
            } else {
                if (!baseDir.isAbsolute()) {
                    throw new IllegalArgumentException("Base directory '" + baseDir + "' must be absolute.");
                }
                resolvedFile = resolveAbsolutely(localPath, baseDir.getAbsolutePath());
            }
            try {
                localPath = resolvedFile.getCanonicalFile().getAbsolutePath();
            } catch (IOException e) {
                throw new RuntimeException("Unable to canonicalize '" + resolvedFile + "'");
            }
        }

        if (pathParts.length > 1) {
            pathParts[0] = localPath;
            return join(":", pathParts);
        }

        return localPath;
    }

    /**
     * Iterates over each {@link RunVolumeConfiguration#getBind() binding} in the {@code volumeConfiguration}, and
     * resolves any relative paths in the binding strings using {@link #resolveRelativeVolumeBinding(File, String)}.
     * The {@code volumeConfiguration} is modified in place, with any relative paths replaced with absolute paths.
     * <p>
     * Relative paths are resolved relative to the supplied {@code baseDir}, which <em>must</em> be absolute.
     * </p>
     *
     * @param baseDir the base directory used to resolve relative paths (e.g. beginning with {@code ./}, {@code ../},
     *                {@code ~}) present in the binding string; <em>must</em> be absolute
     * @param volumeConfiguration the volume configuration that may contain volume binding specifications
     * @throws IllegalArgumentException if the supplied {@code baseDir} is not absolute
     */
    public static void resolveRelativeVolumeBindings(File baseDir, RunVolumeConfiguration volumeConfiguration) {
        List<String> bindings = volumeConfiguration.getBind();

        if (bindings == null || bindings.isEmpty()) {
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

        // java.io.File considers Windows paths to be absolute _only_ if they start with a drive letter.  That is,
        // a Windows path '\foo\bar\baz' is _not_ considered absolute by File#isAbsolute.  This block differs from
        // java.io.File in that it considers Windows paths to be absolute if they begin with the file separator _or_ a
        // drive letter
        if (candidatePath.startsWith(UNIX_SEP) ||
                candidatePath.startsWith(WINDOWS_SEP) ||
                WINDOWS_DRIVE_PATTERN.matcher(candidatePath).matches()) {
            return false;
        }

        // './' or '../'
        if (candidatePath.startsWith(DOT + RUNTIME_SEP) || candidatePath.startsWith(DOT + DOT + RUNTIME_SEP)) {
            return true;
        }

        if (candidatePath.contains(UNIX_SEP) || candidatePath.contains(WINDOWS_SEP)) {
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
        return candidatePath.startsWith(TILDE);
    }

    private static String prepareUserHomeRelativePath(String userHomePath) {
        if (!(isUserHomeRelativePath(userHomePath))) {
            return userHomePath;
        }

        // Handle ~user and ~/path and ~

        // '~'
        if (userHomePath.equals(TILDE)) {
            return "";
        }

        // '~/'
        if (userHomePath.startsWith(TILDE + RUNTIME_SEP)) {
            return userHomePath.substring(2);
        }

        // '~user' is not supported; no logic to support "find the home directory for an arbitrary user".
        // e.g. '~user' or '~user/foo'
        throw new IllegalArgumentException("'" + userHomePath + "' cannot be relativized, cannot resolve arbitrary" +
                " user home paths.");
    }

    private static String join(String with, String... components) {
        StringBuilder result = new StringBuilder();
        int i = 0;
        while (i < components.length) {
            result.append(components[i++]);
            if (i < components.length) {
                result.append(with);
            }
        }

        return result.toString();
    }
}
