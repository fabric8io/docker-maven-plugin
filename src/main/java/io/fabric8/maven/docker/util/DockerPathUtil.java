package io.fabric8.maven.docker.util;

import java.io.File;

/**
 * Docker path resolution and manipulation utility methods.
 * <p>
 * This class provides methods for manipulating paths <em>as they appear in docker-compose or Dockerfiles</em>.  This
 * class does not provide for generic path manipulation across platforms or file systems.  Paths that appear in Docker
 * configurations use forward slash as a separator character, so this class makes no provisions for handling Windows
 * platform path semantics (e.g. the presence of drive letters or backward slash).
 * </p>
 */
public class DockerPathUtil {

    /**
     * Resolves the supplied resource (a path or directory on the filesystem) relative the supplied {@code
     * baseDir}.  The returned {@code File} is guaranteed to be {@link File#isAbsolute() absolute}.  The returned file
     * is <em>not</em> guaranteed to exist.
     * <p>
     * If the supplied {@code pathToResolve} is already {@link File#isAbsolute() absolute}, then it is returned
     * <em>unmodified</em>.  Otherwise, the {@code pathToResolve} is returned as an absolute {@code File} using the
     * supplied {@code baseDir} as its parent.
     * </p>
     *
     * @param pathToResolve represents a filesystem resource, which may be an absolute path
     * @param baseDir the absolute path used to resolve non-absolute path resources; <em>must</em> be absolute
     * @return an absolute {@code File} reference to {@code pathToResolve}; <em>not</em> guaranteed to exist
     * @throws IllegalArgumentException if the supplied {@code baseDir} does not represent an absolute path
     */
    public static File resolveAbsolutely(String pathToResolve, String baseDir) {
        File fileToResolve = new File(pathToResolve);

        if (fileToResolve.isAbsolute()) {
            return fileToResolve;
        }

        if (baseDir == null) {
            throw new IllegalArgumentException("Cannot resolve relative path '" + pathToResolve + "' with a " +
                    "null base directory.");
        }

        File baseDirAsFile = new File(baseDir);
        if (!baseDirAsFile.isAbsolute()) {
            throw new IllegalArgumentException("Base directory '" + baseDirAsFile + "' must be absolute");
        }

        return new File(baseDirAsFile, pathToResolve);
    }
}
