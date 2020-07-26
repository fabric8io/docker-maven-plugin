package io.fabric8.maven.docker.config.handler.compose;

import io.fabric8.maven.docker.util.DockerPathUtil;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.io.IOException;

/**
 * Path-resolution methods
 */
class ComposeUtils {

    /**
     * Resolves a docker-compose file against the supplied base directory.  The returned {@code File} is guaranteed to
     * be {@link File#isAbsolute() absolute}.
     * <p>
     * If {@code composeFile} is {@link File#isAbsolute() absolute}, then it is returned unmodified.  Otherwise, the
     * {@code composeFile} is returned as an absolute {@code File} using the {@link #resolveAbsolutely(String,
     * MavenProject) resolved} {@code baseDir} as its parent.
     * </p>
     *
     * @param baseDir the base directory containing the docker-compose file (ignored if {@code composeFile} is absolute)
     * @param composeFile the path of the docker-compose file, may be absolute
     * @param project the {@code MavenProject} used to resolve the {@code baseDir}
     * @return an absolute {@code File} reference to the {@code composeFile}
     */
    static File resolveComposeFileAbsolutely(String baseDir, String composeFile, MavenProject project) {
        File yamlFile = new File(composeFile);
        if (yamlFile.isAbsolute()) {
            return yamlFile;
        }

        File toCanonicalize = new File(resolveAbsolutely(baseDir, project), composeFile);

        try {
            return toCanonicalize.getCanonicalFile();
        } catch (IOException e) {
            throw new RuntimeException("Unable to canonicalize the resolved docker-compose file path '" + toCanonicalize + "'");
        }
    }

    /**
     * Resolves the supplied resource (a path or directory on the filesystem) relative the Maven {@link
     * MavenProject#getBasedir() base directory}.  The returned {@code File} is guaranteed to be {@link
     * File#isAbsolute() absolute}.  The returned file is <em>not</em> guaranteed to exist.
     * <p>
     * If {@code pathToResolve} is {@link File#isAbsolute() absolute}, then it is returned unmodified.  Otherwise, the
     * {@code pathToResolve} is returned as an absolute {@code File} using the {@link MavenProject#getBasedir() Maven
     * Project base directory} as its parent.
     * </p>
     *
     * @param pathToResolve represents a filesystem resource, which may be an absolute path
     * @param project the Maven project used to resolve non-absolute path resources, may be {@code null} if
     *                {@code pathToResolve} is {@link File#isAbsolute() absolute}
     * @return an absolute {@code File} reference to {@code pathToResolve}; <em>not</em> guaranteed to exist
     * @throws IllegalArgumentException if {@code pathToResolve} is relative, and {@code project} is {@code null} or
     *                                  provides a relative {@link MavenProject#getBasedir() base directory}
     */
    static File resolveAbsolutely(String pathToResolve, MavenProject project) {
        // avoid an NPE if the Maven project is not needed by DockerPathUtil
        return DockerPathUtil.resolveAbsolutely(pathToResolve,
                (project == null) ? null : project.getBasedir().getAbsolutePath());
    }
}
