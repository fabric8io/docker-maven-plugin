package io.fabric8.maven.docker.util;

import io.fabric8.maven.docker.config.RunVolumeConfiguration;

import java.io.File;
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
     * {@code ~/}.  Relative paths beginning with {@code ./} or {@code ../} are absolutized relative to the supplied
     * {@code baseDir}, which <em>must</em> be absolute.  Paths beginning with {@code ~/} are interpreted relative to
     * {@code new File(System.getProperty( "user.home"))}, and {@code baseDir} is ignored.
     * </p>
     * <p>
     * Volume strings that do not begin with a {@code ./}, {@code ../}, or {@code ~/} are returned as-is.
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
     *
     * @param baseDir the base directory used to resolve relative paths (e.g. beginning with {@code ./}, {@code ../},
     *                {@code ~/}) present in the {@code bindingString}; <em>must</em> be absolute
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

        // only perform resolution if the localPath begins with '~/', '../' or './'

        if (localPath.startsWith("./") || localPath.startsWith("../")) {
            localPath = resolveAbsolutely(localPath, baseDir.getAbsolutePath()).getAbsolutePath();
        }

        if (localPath.startsWith("~/")) {
            localPath = resolveAbsolutely(localPath.substring(2), System.getProperty("user.home")).getAbsolutePath();
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
     *                {@code ~/}) present in the binding string; <em>must</em> be absolute
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
}
