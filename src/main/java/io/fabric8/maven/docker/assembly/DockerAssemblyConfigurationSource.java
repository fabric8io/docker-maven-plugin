package io.fabric8.maven.docker.assembly;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import javax.annotation.Nonnull;

import io.fabric8.maven.docker.config.AssemblyConfiguration;
import io.fabric8.maven.docker.util.MojoParameters;
import io.fabric8.maven.docker.util.EnvUtil;
import org.apache.maven.archiver.MavenArchiveConfiguration;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugins.assembly.AssemblerConfigurationSource;
import org.apache.maven.plugins.assembly.utils.InterpolationConstants;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.filtering.MavenFileFilter;
import org.apache.maven.shared.filtering.MavenReaderFilter;
import org.apache.maven.shared.utils.cli.CommandLineUtils;
import org.codehaus.plexus.interpolation.fixed.FixedStringSearchInterpolator;
import org.codehaus.plexus.interpolation.fixed.PrefixedPropertiesValueSource;
import org.codehaus.plexus.interpolation.fixed.PropertiesBasedValueSource;

/**
 * @author roland
 * @since 07.05.14
 */
public class DockerAssemblyConfigurationSource implements AssemblerConfigurationSource {

    private final AssemblyConfiguration assemblyConfig;
    private final MojoParameters params;
    private final BuildDirs buildDirs;

    // Required by configuration source and duplicated from AbstractAssemblyMojo (which is unfortunately
    // not extracted to be usab;e
    private FixedStringSearchInterpolator commandLinePropertiesInterpolator;
    private FixedStringSearchInterpolator envInterpolator;
    private FixedStringSearchInterpolator rootInterpolator;
    private FixedStringSearchInterpolator mainProjectInterpolator;

    public DockerAssemblyConfigurationSource(MojoParameters params, BuildDirs buildDirs, AssemblyConfiguration assemblyConfig) {
        this.params = params;
        this.assemblyConfig = assemblyConfig;
        this.buildDirs = buildDirs;
    }

    @Override
    public String[] getDescriptors() {
        if (assemblyConfig != null) {
          String descriptor = assemblyConfig.getDescriptor();

          if (descriptor != null) {
            return new String[] {EnvUtil.prepareAbsoluteSourceDirPath(params, descriptor).getAbsolutePath() };
          }
        }
        return new String[0];
    }

    @Override
    public String[] getDescriptorReferences() {
        if (assemblyConfig != null) {
            String descriptorRef = assemblyConfig.getDescriptorRef();
            if (descriptorRef != null) {
                return new String[]{descriptorRef};
            }
        }
        return null;
    }

    // ============================================================================================

    @Override
    public File getOutputDirectory() {
        return buildDirs.getOutputDirectory();
    }

    @Override
    public File getWorkingDirectory() {
        return buildDirs.getWorkingDirectory();
    }

    // X
    @Override
    public File getTemporaryRootDirectory() {
        return buildDirs.getTemporaryRootDirectory();
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
        return params.getReactorProjects();
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


    @Nonnull public FixedStringSearchInterpolator getCommandLinePropsInterpolator()
    {
        if (commandLinePropertiesInterpolator == null) {
            this.commandLinePropertiesInterpolator = createCommandLinePropertiesInterpolator();
        }
        return commandLinePropertiesInterpolator;
    }

    @Nonnull
    public FixedStringSearchInterpolator getEnvInterpolator()
    {
        if (envInterpolator == null) {
            this.envInterpolator = createEnvInterpolator();
        }
        return envInterpolator;
    }

    @Nonnull public FixedStringSearchInterpolator getRepositoryInterpolator()
    {
        if (rootInterpolator == null) {
            this.rootInterpolator = createRepositoryInterpolator();
        }
        return rootInterpolator;
    }


    @Nonnull
    public FixedStringSearchInterpolator getMainProjectInterpolator()
    {
        if (mainProjectInterpolator == null) {
            this.mainProjectInterpolator = mainProjectInterpolator(getProject());
        }
        return mainProjectInterpolator;
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
    public String getTarLongFileMode() {
        return assemblyConfig.getTarLongFileMode() == null ? "warn" : assemblyConfig.getTarLongFileMode();
    }

    // X
    @Override
    public File getSiteDirectory() {
        return null;
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
        return params.getMavenFilterReader();
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
        return assemblyConfig != null ? assemblyConfig.isIgnorePermissions() : false;
    }

    // =======================================================================
    // Taken from AbstractAssemblyMojo

    private FixedStringSearchInterpolator mainProjectInterpolator(MavenProject mainProject)
    {
        if (mainProject != null) {
            // 5
            return FixedStringSearchInterpolator.create(
                new org.codehaus.plexus.interpolation.fixed.PrefixedObjectValueSource(
                    InterpolationConstants.PROJECT_PREFIXES, mainProject, true ),

                // 6
                new org.codehaus.plexus.interpolation.fixed.PrefixedPropertiesValueSource(
                    InterpolationConstants.PROJECT_PROPERTIES_PREFIXES, mainProject.getProperties(), true ) );
        }
        else {
            return FixedStringSearchInterpolator.empty();
        }
    }

    private FixedStringSearchInterpolator createRepositoryInterpolator()
    {
        final Properties settingsProperties = new Properties();
        final MavenSession session = getMavenSession();

        if (getLocalRepository() != null) {
            settingsProperties.setProperty("localRepository", getLocalRepository().getBasedir());
            settingsProperties.setProperty("settings.localRepository", getLocalRepository().getBasedir());
        }
        else if (session != null && session.getSettings() != null) {
            settingsProperties.setProperty("localRepository", session.getSettings().getLocalRepository() );
            settingsProperties.setProperty("settings.localRepository", getLocalRepository().getBasedir() );
        }
        return FixedStringSearchInterpolator.create(new PropertiesBasedValueSource(settingsProperties));
    }

    private FixedStringSearchInterpolator createCommandLinePropertiesInterpolator()
    {
        Properties commandLineProperties = System.getProperties();
        final MavenSession session = getMavenSession();

        if (session != null) {
            commandLineProperties = new Properties();
            if (session.getSystemProperties() != null) {
                commandLineProperties.putAll(session.getSystemProperties());
            }
            if (session.getUserProperties() != null) {
                commandLineProperties.putAll(session.getUserProperties());
            }
        }
        PropertiesBasedValueSource cliProps = new PropertiesBasedValueSource( commandLineProperties );
        return FixedStringSearchInterpolator.create( cliProps );
    }

    private FixedStringSearchInterpolator createEnvInterpolator() {
        PrefixedPropertiesValueSource envProps = new PrefixedPropertiesValueSource(Collections.singletonList("env."),
                                                                                   CommandLineUtils.getSystemEnvVars(false), true );
        return FixedStringSearchInterpolator.create( envProps );
    }
}
