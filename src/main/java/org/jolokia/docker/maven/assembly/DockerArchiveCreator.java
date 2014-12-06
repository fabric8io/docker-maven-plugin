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
import org.codehaus.plexus.archiver.Archiver;
import org.codehaus.plexus.archiver.manager.ArchiverManager;
import org.codehaus.plexus.archiver.manager.NoSuchArchiverException;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.jolokia.docker.maven.config.BuildImageConfiguration;
import org.jolokia.docker.maven.util.EnvUtil;
import org.jolokia.docker.maven.util.MojoParameters;

/**
 * Tool for creating a docker image tar ball including a Dockerfile for building
 * a data-only docker image which can be linked into other images, too.
 *
 * @author roland
 * @since 08.05.14
 */
@Component(role = DockerArchiveCreator.class)
public class DockerArchiveCreator {

    @Requirement
    private AssemblyArchiver assemblyArchiver;

    @Requirement
    private AssemblyReader assemblyReader;

    @Requirement
    private ArchiverManager archiverManager;

    public File create(MojoParameters params, BuildImageConfiguration config)
            throws MojoExecutionException {
        validate(config);

        File target = new File(params.getProject().getBasedir(),"target/");
        File dockerDir = new File(target,"docker");
        File destFile = new File(target,"docker-tmp/docker-build.tar");

        createAssembly(params,config);
        writeDockerFile(config,dockerDir);
        return createDockerBuildArchive(destFile,dockerDir);
    }

    private void validate(BuildImageConfiguration buildConfig) throws MojoExecutionException {
        String assemblyDescriptor = buildConfig.getAssemblyDescriptor();
        String assemblyDescriptorRef = buildConfig.getAssemblyDescriptorRef();
        if (assemblyDescriptor != null && assemblyDescriptorRef != null) {
            throw new MojoExecutionException("No assemblyDescriptor or assemblyDescriptorRef has been given");
        }
    }

    private File createDockerBuildArchive(File archive, File dockerDir) throws MojoExecutionException {
        try {
            Archiver archiver = archiverManager.getArchiver("tar");
            archiver.addDirectory(dockerDir);
            archiver.setDestFile(archive);
            archiver.createArchive();
            return archive;
        } catch (NoSuchArchiverException e) {
            throw new MojoExecutionException("No archiver for type 'tar' found: " + e,e);
        } catch (IOException e) {
            throw new MojoExecutionException("Cannot create archive " + archive + ": " + e,e);
        }

    }

    private File writeDockerFile(BuildImageConfiguration config,File destDir) throws MojoExecutionException {
        try {
            DockerFileBuilder builder =
                    new DockerFileBuilder()
                            .exportDir(config.getExportDir())
                            .add("maven", "")
                            .expose(config.getPorts())
                            .env(config.getEnv())
                            .volumes(config.getVolumes());
            if (config.getFrom() != null) {
                builder.baseImage(config.getFrom());
                builder.command((String[]) null); // Use command from base image (gets overwritten below if explicitely set)
            }
            if (config.getCommand() != null) {
                builder.command(EnvUtil.splitWOnSpaceWithEscape(config.getCommand()));
            }
            return builder.create(destDir);
        } catch (IOException e) {
            throw new MojoExecutionException("Cannot create DockerFile in " + destDir + ": " + e,e);
        }
    }

    private void createAssembly(MojoParameters params, BuildImageConfiguration buildConfig)
            throws MojoExecutionException {

        AssemblerConfigurationSource config =
                new DockerArchiveConfigurationSource(params, buildConfig.getAssemblyDescriptor(), buildConfig.getAssemblyDescriptorRef());
        Assembly assembly = extractAssembly(config);

        try {
            assembly.setId("docker");
            assemblyArchiver.createArchive(assembly, "maven", "dir", config, false);
        } catch (ArchiveCreationException | AssemblyFormattingException e) {
            throw new MojoExecutionException( "Failed to create assembly for docker image: " + e.getMessage(), e );
        } catch (InvalidAssemblerConfigurationException e) {
            throw new MojoExecutionException(assembly, "Assembly is incorrectly configured: " + assembly.getId(),
                                            "Assembly: " + assembly.getId() + " is not configured correctly: "
                                            + e.getMessage());
        }
    }

    private Assembly extractAssembly(AssemblerConfigurationSource config) throws MojoExecutionException {
        try
        {
            List<Assembly> assemblies = assemblyReader.readAssemblies(config);
            if (assemblies.size() != 1) {
                throw new MojoExecutionException("Only one assembly can be used for creating a Docker base image (and not " + assemblies.size() +")");
            }
            return assemblies.get(0);
        }
        catch (AssemblyReadException e)
        {
            throw new MojoExecutionException( "Error reading assembly: " + e.getMessage(), e );
        }
        catch (InvalidAssemblerConfigurationException e)
        {
            throw new MojoExecutionException(assemblyReader, e.getMessage(), "Docker assembly configuration is invalid: " + e.getMessage());
        }
    }
}
