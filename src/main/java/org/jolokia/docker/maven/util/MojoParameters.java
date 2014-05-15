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

    public MojoParameters(MavenSession session, MavenProject project, MavenArchiveConfiguration archive, MavenFileFilter mavenFileFilter) {
        this.archive = archive;
        this.session = session;
        this.mavenFileFilter = mavenFileFilter;
        this.project = project;
    }

    public MavenArchiveConfiguration getArchiveConfiguration() {
        return archive;
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
