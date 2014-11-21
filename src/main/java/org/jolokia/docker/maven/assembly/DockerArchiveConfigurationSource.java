package org.jolokia.docker.maven.assembly;

import java.io.File;
import java.util.Collections;
import java.util.List;

import org.apache.maven.archiver.MavenArchiveConfiguration;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.assembly.AssemblerConfigurationSource;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.filtering.MavenFileFilter;
import org.apache.maven.shared.filtering.MavenReaderFilter;
import org.jolokia.docker.maven.util.MojoParameters;

/**
 * @author roland
 * @since 07.05.14
 */
public class DockerArchiveConfigurationSource implements AssemblerConfigurationSource {


    private String[] descriptors;
    private String[] descriptorRefs;

    private MojoParameters params;

    public DockerArchiveConfigurationSource(MojoParameters params, String descriptor, String descriptorRef) {
        this.descriptors = descriptor != null ? new String[] { descriptor } : null;
        this.descriptorRefs = descriptorRef != null ? new String[] { descriptorRef } : null;
        this.params = params;
    }

    public String[] getDescriptors() {
        return descriptors;
    }

    public String[] getDescriptorReferences() {
        return descriptorRefs;
    }

    // ============================================================================================

    public File getOutputDirectory() {
        return new File(params.getProject().getBasedir(),"target/docker");
    }

    public File getWorkingDirectory() {
        return new File(params.getProject().getBasedir(),"target/docker-work");
    }

    // X
    public File getTemporaryRootDirectory() {
        return new File(params.getProject().getBasedir(),"target/docker-tmp");
    }

    public String getFinalName() {
        //return params.getProject().getBuild().getFinalName();
        return ".";
    }

    public ArtifactRepository getLocalRepository() {
        return params.getSession().getLocalRepository();
    }
    public MavenFileFilter getMavenFileFilter() {
        return params.getMavenFileFilter();
    }

    // Maybe use injection
    public List<MavenProject> getReactorProjects() {
        return params.getProject().getCollectedProjects();
    }

    // Maybe use injection
    public List<ArtifactRepository> getRemoteRepositories() {
        return params.getProject().getRemoteArtifactRepositories();
    }

    public MavenSession getMavenSession() {
        return params.getSession();
    }

    public MavenArchiveConfiguration getJarArchiveConfiguration() {
        return params.getArchiveConfiguration();
    }

    // X
    public String getEncoding() {
        return params.getProject().getProperties().getProperty("project.build.sourceEncoding");
    }

    // X
    public String getEscapeString() {
        return null;
    }

    @Override
    public List<String> getDelimiters() {
        return null;
    }

    // X
    public MavenProject getProject() {
        return params.getProject();
    }

    // X
    public File getBasedir() {
        return params.getProject().getBasedir();
    }

    // X
    public boolean isIgnoreDirFormatExtensions() {
        return true;
    }

    // X
    public boolean isDryRun() {
        return false;
    }

    // X
    public String getClassifier() {
        return null;
    }

    // X
    public List<String> getFilters() {
        return Collections.emptyList();
    }

    @Override
    public boolean isIncludeProjectBuildFilters() {
        return true;
    }

    // X
    public File getDescriptorSourceDirectory() {
        return null;
    }

    // X
    public File getArchiveBaseDirectory() {
        return null;
    }

    // X
    public String getDescriptorId() {
        return null;
    }

    // X
    public String getDescriptor() {
        return null;
    }

    // X
    public String getTarLongFileMode() {
        return "warn";
    }

    // X
    public File getSiteDirectory() {
        return null;
    }

    // X
    public boolean isSiteIncluded() {
        return false;
    }

    // X
    public boolean isAssemblyIdAppended() {
        return false;
    }

    // X
    public boolean isIgnoreMissingDescriptor() {
        return false;
    }

    // X: (maybe inject MavenArchiveConfiguration)
    public String getArchiverConfig() {
        return null;
    }

    @Override
    public MavenReaderFilter getMavenReaderFilter() {
        return null;
    }

    public boolean isUpdateOnly() {
        return false;
    }

    public boolean isUseJvmChmod() {
        return false;
    }

    public boolean isIgnorePermissions() {
        return true;
    }
}
