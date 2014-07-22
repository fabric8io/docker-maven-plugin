package org.jolokia.docker.maven.assembly;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
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

    public File create(MojoParameters params, String baseImage, String exportDir, String assemblyDescriptor, String assemblyDescriptorRef)
            throws MojoFailureException, MojoExecutionException {
        File target = new File(params.getProject().getBasedir(),"target/");
        File dockerDir = new File(target,"docker");
        File destFile = new File(target,"docker-tmp/docker-build.tar");

        createAssembly(params,assemblyDescriptor,assemblyDescriptorRef);
        writeDockerFile(baseImage,exportDir,dockerDir);
        return createDockerBuildArchive(destFile,dockerDir);
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

    private File writeDockerFile(String baseImage, String exportDir, File destDir) throws MojoExecutionException {
        try {
            DockerFileBuilder builder = new DockerFileBuilder().exportDir(exportDir).add("maven","");
            if (baseImage != null) {
                builder.baseImage(baseImage);
                builder.command(null); // Use command from base image
            }
            return builder.create(destDir);
        } catch (IOException e) {
            throw new MojoExecutionException("Cannot create DockerFile in " + destDir + ": " + e,e);
        }
    }

    private void createAssembly(MojoParameters params, String descriptor, String descriptorRef)
            throws MojoFailureException, MojoExecutionException {

        AssemblerConfigurationSource config =
                new DockerArchiveConfigurationSource(params, descriptor, descriptorRef);
        Assembly assembly = extractAssembly(config);

        try {
            assembly.setId("docker");
            assemblyArchiver.createArchive(assembly, "maven", "dir", config, false);
        } catch (ArchiveCreationException e) {
            throw new MojoExecutionException( "Failed to create assembly for docker image: " + e.getMessage(), e );
        }
        catch (AssemblyFormattingException e) {
            throw new MojoExecutionException( "Failed to create assembly for docker image: " + e.getMessage(), e );
        }
        catch (InvalidAssemblerConfigurationException e) {
            throw new MojoFailureException( assembly, "Assembly is incorrectly configured: " + assembly.getId(),
                                            "Assembly: " + assembly.getId() + " is not configured correctly: "
                                            + e.getMessage() );
        }
    }

    private Assembly extractAssembly(AssemblerConfigurationSource config) throws MojoExecutionException, MojoFailureException {
        try
        {
            List<Assembly> assemblies = assemblyReader.readAssemblies(config);
            if (assemblies.size() != 1) {
                throw new MojoFailureException("Only one assembly can be used for creating a Docker base image (and not " + assemblies.size() +")");
            }
            return assemblies.get(0);
        }
        catch (AssemblyReadException e)
        {
            throw new MojoExecutionException( "Error reading assembly: " + e.getMessage(), e );
        }
        catch (InvalidAssemblerConfigurationException e)
        {
            throw new MojoFailureException(assemblyReader, e.getMessage(), "Docker assembly configuration is invalid: " + e.getMessage());
        }
    }
}
