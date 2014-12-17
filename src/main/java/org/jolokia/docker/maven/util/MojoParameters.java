package org.jolokia.docker.maven.util;

import org.apache.maven.archiver.MavenArchiveConfiguration;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.filtering.MavenFileFilter;

/**
 * Helper class for encapsulating Mojo params which are not Plexus components
 *
 * @author roland
 * @since 09.05.14
 */
public class MojoParameters {
    private MavenArchiveConfiguration archive;
    private MavenSession session;
    private MavenFileFilter mavenFileFilter;
    private MavenProject project;

    private final String outputDirectory;
    private final String sourceDirectory;

    public MojoParameters(MavenSession session, MavenProject project, MavenArchiveConfiguration archive, MavenFileFilter mavenFileFilter,
            String sourceDirectory, String outputDirectory) {
        this.archive = archive;
        this.session = session;
        this.mavenFileFilter = mavenFileFilter;
        this.project = project;

        this.sourceDirectory = sourceDirectory;
        this.outputDirectory = outputDirectory;
    }

    public MavenArchiveConfiguration getArchiveConfiguration() {
        return archive;
    }

    public String getDockerSourceDirectory() {
        return sourceDirectory;
    }

    public String getDockerOutputDirectory() {
        return outputDirectory;
    }

    public MavenSession getSession() {
        return session;
    }

    public MavenFileFilter getMavenFileFilter() {
        return mavenFileFilter;
    }

    public MavenProject getProject() {
        return project;
    }
}
