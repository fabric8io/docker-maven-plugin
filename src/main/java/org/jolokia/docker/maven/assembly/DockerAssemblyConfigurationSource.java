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
import org.jolokia.docker.maven.config.AssemblyConfiguration;
import org.jolokia.docker.maven.util.EnvUtil;
import org.jolokia.docker.maven.util.MojoParameters;

/**
 * @author roland
 * @since 07.05.14
 */
public class DockerAssemblyConfigurationSource implements AssemblerConfigurationSource {

    private final AssemblyConfiguration assemblyConfig;
    private final MojoParameters params;
    
    public DockerAssemblyConfigurationSource(MojoParameters params, AssemblyConfiguration assemblyConfig) {
        this.params = params;
        this.assemblyConfig = assemblyConfig;
    }

    @Override
    public String[] getDescriptors() {
        String descriptor = assemblyConfig.getDescriptor();
        
        if (descriptor != null) {
            return new String[] { EnvUtil.prepareAbsolutePath(params.getSourceDirectory(),descriptor).getAbsolutePath() };
        } else {
            return new String[0];
        }
    }

    @Override
    public String[] getDescriptorReferences() {
        String descriptorRef = assemblyConfig.getDescriptorRef();
        return (descriptorRef != null) ? new String[] { descriptorRef } : null;
    }

    // ============================================================================================

    @Override
    public File getOutputDirectory() {
        return new File(params.getOutputDirectory() +  "/build");
    }

    @Override
    public File getWorkingDirectory() {
        return new File(params.getOutputDirectory() + "/work");
    }

    // X
    @Override
    public File getTemporaryRootDirectory() {
        return new File(params.getOutputDirectory() +  "/tmp");
    }

    @Override
    public String getFinalName() {
        //return params.getProject().getBuild().getFinalName();
        return ".";
    }

    @Override
    public ArtifactRepository getLocalRepository() {
        return params.getSession().getLocalRepository();
    }
    
    public MavenFileFilter getMavenFileFilter() {
        return params.getMavenFileFilter();
    }

    // Maybe use injection
    @Override
    public List<MavenProject> getReactorProjects() {
        return params.getProject().getCollectedProjects();
    }

    // Maybe use injection
    @Override
    public List<ArtifactRepository> getRemoteRepositories() {
        return params.getProject().getRemoteArtifactRepositories();
    }

    @Override
    public MavenSession getMavenSession() {
        return params.getSession();
    }

    @Override
    public MavenArchiveConfiguration getJarArchiveConfiguration() {
        return params.getArchiveConfiguration();
    }

    // X
    @Override
    public String getEncoding() {
        return params.getProject().getProperties().getProperty("project.build.sourceEncoding");
    }

    // X
    @Override
    public String getEscapeString() {
        return null;
    }

    @Override
    public List<String> getDelimiters() {
        return null;
    }

    // X
    @Override
    public MavenProject getProject() {
        return params.getProject();
    }

    // X
    @Override
    public File getBasedir() {
        return params.getProject().getBasedir();
    }

    // X
    @Override
    public boolean isIgnoreDirFormatExtensions() {
        return true;
    }

    // X
    @Override
    public boolean isDryRun() {
        return false;
    }

    // X
    @Override
    public String getClassifier() {
        return null;
    }

    // X
    @Override
    public List<String> getFilters() {
        return Collections.emptyList();
    }

    @Override
    public boolean isIncludeProjectBuildFilters() {
        return true;
    }

    // X
    @Override
    public File getDescriptorSourceDirectory() {
        return null;
    }

    // X
    @Override
    public File getArchiveBaseDirectory() {
        return null;
    }

    // X
    @Override
    public String getDescriptorId() {
        return null;
    }

    // X
    @Override
    public String getDescriptor() {
        return null;
    }

    // X
    @Override
    public String getTarLongFileMode() {
        return "warn";
    }

    // X
    @Override
    public File getSiteDirectory() {
        return null;
    }

    // X
    @Override
    public boolean isSiteIncluded() {
        return false;
    }

    // X
    @Override
    public boolean isAssemblyIdAppended() {
        return false;
    }

    // X
    @Override
    public boolean isIgnoreMissingDescriptor() {
        return false;
    }

    // X: (maybe inject MavenArchiveConfiguration)
    @Override
    public String getArchiverConfig() {
        return null;
    }

    @Override
    public MavenReaderFilter getMavenReaderFilter() {
        return null;
    }

    @Override
    public boolean isUpdateOnly() {
        return false;
    }

    @Override
    public boolean isUseJvmChmod() {
        return false;
    }

    @Override
    public boolean isIgnorePermissions() {
        return assemblyConfig.isIgnorePermissions();
    }

}
