package io.fabric8.maven.docker.util;

import java.util.List;

import org.apache.maven.archiver.MavenArchiveConfiguration;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;
import org.apache.maven.settings.Settings;
import org.apache.maven.shared.filtering.MavenFileFilter;
import org.apache.maven.shared.filtering.MavenReaderFilter;

/**
 * Helper class for encapsulating Mojo params which are not Plexus components
 *
 * @author roland
 * @since 09.05.14
 */
public class MojoParameters {
    private final MavenArchiveConfiguration archive;
    private final MavenSession session;
    private final MavenFileFilter mavenFileFilter;
    private final MavenReaderFilter mavenFilterReader;
    private final MavenProject project;
    private final Settings settings;

    private final String outputDirectory;
    private final String sourceDirectory;
    
    private final List<MavenProject> reactorProjects;

    public MojoParameters(MavenSession session, MavenProject project, MavenArchiveConfiguration archive, MavenFileFilter mavenFileFilter,
            MavenReaderFilter mavenFilterReader, Settings settings, String sourceDirectory, String outputDirectory, List<MavenProject> reactorProjects) {
        this.archive = archive;
        this.session = session;
        this.mavenFileFilter = mavenFileFilter;
        this.mavenFilterReader = mavenFilterReader;
        this.project = project;
        this.settings = settings;

        this.sourceDirectory = sourceDirectory;
        this.outputDirectory = outputDirectory;
        
        this.reactorProjects = reactorProjects;
    }

    public MavenArchiveConfiguration getArchiveConfiguration() {
        return archive;
    }

    public String getSourceDirectory() {
        return sourceDirectory;
    }

    public String getOutputDirectory() {
        return outputDirectory;
    }

    public MavenSession getSession() {
        return session;
    }

    public MavenFileFilter getMavenFileFilter() {
        return mavenFileFilter;
    }
    
    public MavenReaderFilter getMavenFilterReader() {
        return mavenFilterReader;
    }

    public MavenProject getProject() {
        return project;
    }

    public Settings getSettings() {
        return settings;
    }

	public List<MavenProject> getReactorProjects() {
		return reactorProjects;
	}
}
