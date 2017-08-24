package io.fabric8.maven.docker.config.handler.compose;

import io.fabric8.maven.docker.util.DockerPathUtil;
import org.apache.maven.project.MavenProject;

import java.io.File;

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
        return yamlFile.isAbsolute() ? yamlFile :  new File(resolveAbsolutely(baseDir, project),composeFile);
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
     * @param project the Maven project used to resolve non-absolute path resources
     * @return an absolute {@code File} reference to {@code pathToResolve}; <em>not</em> guaranteed to exist
     */
    static File resolveAbsolutely(String pathToResolve, MavenProject project) {
        return DockerPathUtil.resolveAbsolutely(pathToResolve, project.getBasedir().getAbsolutePath());
    }
}
