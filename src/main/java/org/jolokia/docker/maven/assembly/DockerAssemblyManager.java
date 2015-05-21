package org.jolokia.docker.maven.assembly;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.assembly.AssemblerConfigurationSource;
import org.apache.maven.plugin.assembly.InvalidAssemblerConfigurationException;
import org.apache.maven.plugin.assembly.archive.ArchiveCreationException;
import org.apache.maven.plugin.assembly.archive.AssemblyArchiver;
import org.apache.maven.plugin.assembly.format.AssemblyFormattingException;
import org.apache.maven.plugin.assembly.io.AssemblyReadException;
import org.apache.maven.plugin.assembly.io.AssemblyReader;
import org.apache.maven.plugin.assembly.model.Assembly;
import org.codehaus.plexus.archiver.manager.ArchiverManager;
import org.codehaus.plexus.archiver.manager.NoSuchArchiverException;
import org.codehaus.plexus.archiver.tar.TarArchiver;
import org.codehaus.plexus.archiver.tar.TarLongFileMode;
import org.codehaus.plexus.archiver.util.DefaultArchivedFileSet;
import org.codehaus.plexus.archiver.util.DefaultFileSet;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.jolokia.docker.maven.config.*;
import org.jolokia.docker.maven.util.EnvUtil;
import org.jolokia.docker.maven.util.MojoParameters;

/**
 * Tool for creating a docker image tar ball including a Dockerfile for building
 * a docker image.
 *
 * @author roland
 * @since 08.05.14
 */
@Component(role = DockerAssemblyManager.class)
public class DockerAssemblyManager {

    public static final String DEFAULT_DATA_BASE_IMAGE = "busybox:latest";;

    @Requirement
    private AssemblyArchiver assemblyArchiver;

    @Requirement
    private AssemblyReader assemblyReader;

    @Requirement
    private ArchiverManager archiverManager;

    /**
     * Create an docker tar archive from the given configuration which can be send to the Docker host for
     * creating the image.
     *
     * @param imageName Name of the image to create (used for creating build directories)
     * @param params Mojos parameters (used for finding the directories)
     * @param buildConfig configuration for how to build the image
     * @return file holding the path to the created assembly tar file
     * @throws MojoExecutionException
     */
    public File createDockerTarArchive(String imageName, MojoParameters params, BuildImageConfiguration buildConfig) throws MojoExecutionException {
        AssemblyConfiguration assemblyConfig = buildConfig.getAssemblyConfiguration();
        BuildDirs buildDirs = createBuildDirs(imageName, params);

        try {
            if (assemblyConfig != null &&
                (assemblyConfig.getInline() != null ||
                 assemblyConfig.getDescriptor() != null ||
                 assemblyConfig.getDescriptorRef() != null)) {
                createAssemblyArchive(assemblyConfig, params, buildDirs);
            }

            File extraDir = null;
            String dockerFileDir = assemblyConfig != null ? assemblyConfig.getDockerFileDir() : null;
            if (dockerFileDir != null) {
                // Use specified docker directory which must include a Dockerfile.
                extraDir = validateDockerDir(params, dockerFileDir);
            } else {
                // Create custom docker file in output dir
                DockerFileBuilder builder = createDockerFileBuilder(buildConfig, assemblyConfig);
                builder.write(buildDirs.getOutputDirectory());
            }
            return createTarball(buildDirs,extraDir,assemblyConfig.getMode());

        } catch (IOException e) {
            throw new MojoExecutionException(String.format("Cannot create Dockerfile in %s", buildDirs.getOutputDirectory()), e);
        }
    }

    private BuildDirs createBuildDirs(String imageName, MojoParameters params) {
        BuildDirs buildDirs = new BuildDirs(params,imageName);
        buildDirs.createDirs();
        return buildDirs;
    }

    private File validateDockerDir(MojoParameters params, String dockerFileDir) throws MojoExecutionException {
        File dockerDir = EnvUtil.prepareAbsoluteSourceDirPath(params, dockerFileDir);
        if (! new File(dockerDir,"Dockerfile").exists()) {
            throw new MojoExecutionException("Specified dockerFileDir \"" + dockerFileDir + "\" (resolved to \"" + dockerDir + "\") " +
                                             " doesn't contain a 'Dockerfile'");
        }
        return dockerDir;
    }

    private File createTarball(BuildDirs buildDirs, File extraDir, AssemblyMode buildMode) throws MojoExecutionException {
        File archive = new File(buildDirs.getTemporaryRootDirectory(), "docker-build.tar");
        try {
            TarArchiver archiver = createArchiver(buildDirs.getOutputDirectory(), archive,  buildMode);
            if (extraDir != null) {
                // User Dockerfile from extra dir
                archiver.addFileSet(DefaultFileSet.fileSet(extraDir));
            } else {
                // Add own Dockerfile
                archiver.addFile(new File(buildDirs.getOutputDirectory(),"Dockerfile"), "Dockerfile");
            }

            archiver.createArchive();
            return archive;
        } catch (NoSuchArchiverException e) {
            throw new MojoExecutionException("No archiver for type 'tar' found", e);
        } catch (IOException e) {
            throw new MojoExecutionException("Cannot create archive " + archive, e);
        }
    }

    private TarArchiver createArchiver(File outputDir, File archive, AssemblyMode buildMode) throws NoSuchArchiverException {
        TarArchiver archiver = (TarArchiver) archiverManager.getArchiver("tar");
        archiver.setLongfile(TarLongFileMode.posix);

        if (buildMode.isArchive()) {
            DefaultArchivedFileSet archiveSet =
                    DefaultArchivedFileSet.archivedFileSet(new File(outputDir, "maven." + buildMode.getExtension()));
            archiveSet.setPrefix("maven/");
            archiveSet.setIncludingEmptyDirectories(true);
            archiver.addArchivedFileSet(archiveSet);
        } else {
            archiver.addFileSet(DefaultFileSet.fileSet(outputDir));
        }
        archiver.setDestFile(archive);
        return archiver;
    }

    // visible for testing
    DockerFileBuilder createDockerFileBuilder(BuildImageConfiguration buildConfig, AssemblyConfiguration assemblyConfig) {
        DockerFileBuilder builder =
                new DockerFileBuilder()
                        .env(buildConfig.getEnv())
                        .expose(buildConfig.getPorts())
                        .volumes(buildConfig.getVolumes());
        if (buildConfig.getMaintainer() != null) {
            builder.maintainer(buildConfig.getMaintainer());
        }
        if (assemblyConfig != null) {
            builder.add("maven", "")
                   .basedir(assemblyConfig.getBasedir())
                   .exportBasedir(assemblyConfig.exportBasedir());
        } else {
            builder.exportBasedir(false);
        }

        builder.baseImage(buildConfig.getFrom());
        builder.command((String[]) null); // Use command from base image (gets overwritten below if explicitly set)

        if (buildConfig.getCommand() != null) {
            builder.command(EnvUtil.splitWOnSpaceWithEscape(buildConfig.getCommand()));
        }

        return builder;
    }

    private void createAssemblyArchive(AssemblyConfiguration assemblyConfig, MojoParameters params, BuildDirs buildDirs) throws MojoExecutionException {
        DockerAssemblyConfigurationSource source =
                        new DockerAssemblyConfigurationSource(params, buildDirs, assemblyConfig);

        Assembly assembly = assemblyConfig.getInline();
        if (assembly == null) {
            assembly = extractAssembly(source);
        }

        AssemblyMode buildMode = assemblyConfig.getMode();
        try {
            assembly.setId("docker");
            assemblyArchiver.createArchive(assembly, "maven", buildMode.getExtension(), source, false);
        } catch (ArchiveCreationException | AssemblyFormattingException e) {
            throw new MojoExecutionException( "Failed to create assembly for docker image: " + e.getMessage() +
                                              " with mode " + buildMode, e );
        } catch (InvalidAssemblerConfigurationException e) {
            throw new MojoExecutionException(assembly, "Assembly is incorrectly configured: " + assembly.getId(),
                                            "Assembly: " + assembly.getId() + " is not configured correctly: "
                                            + e.getMessage());
        }
    }

    private Assembly extractAssembly(AssemblerConfigurationSource config) throws MojoExecutionException {
        try {
            List<Assembly> assemblies = assemblyReader.readAssemblies(config);
            if (assemblies.size() != 1) {
                throw new MojoExecutionException("Only one assembly can be used for creating a Docker base image (and not "
                        + assemblies.size() + ")");
            }
            return assemblies.get(0);
        }
        catch (AssemblyReadException e) {
            throw new MojoExecutionException("Error reading assembly: " + e.getMessage(), e);
        }
        catch (InvalidAssemblerConfigurationException e) {
            throw new MojoExecutionException(assemblyReader, e.getMessage(), "Docker assembly configuration is invalid: " + e.getMessage());
        }
    }
}
