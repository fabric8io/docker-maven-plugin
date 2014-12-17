package org.jolokia.docker.maven.assembly;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
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
import org.codehaus.plexus.archiver.util.DefaultFileSet;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.jolokia.docker.maven.config.AssemblyConfiguration;
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
@Component(role = DockerAssemblyManager.class)
public class DockerAssemblyManager {

    @Requirement
    private AssemblyArchiver assemblyArchiver;

    @Requirement
    private AssemblyReader assemblyReader;

    @Requirement
    private ArchiverManager archiverManager;

    public File create(MojoParameters params, BuildImageConfiguration buildConfig) throws MojoExecutionException {
        AssemblyConfiguration assemblyConfig = buildConfig.getAssemblyConfiguration();
        DockerAssemblyConfigurationSource source = new DockerAssemblyConfigurationSource(params, assemblyConfig);
        
        File dockerDir = source.getOutputDirectory();
        File tarball = new File(source.getTemporaryRootDirectory(), "docker-build.tar");
        
        try {
            createArchiveFromAssembly(source);
            createDockerFile(buildConfig, assemblyConfig).write(dockerDir);
            fillTarball(tarball, dockerDir);    

            return tarball;
        }
        catch (IOException e) {
            throw new MojoExecutionException(String.format("Cannot create DockerFile in %s", dockerDir, e));
        }
    }
    
    private File fillTarball(File archive, File dockerDir) throws MojoExecutionException {
        try {
            Archiver archiver = archiverManager.getArchiver("tar");
            archiver.addFileSet(DefaultFileSet.fileSet(dockerDir));
            archiver.setDestFile(archive);
            archiver.createArchive();
            return archive;
        } catch (NoSuchArchiverException e) {
            throw new MojoExecutionException("No archiver for type 'tar' found", e);
        } catch (IOException e) {
            throw new MojoExecutionException("Cannot create archive " + archive, e);
        }
    }

    private DockerFileBuilder createDockerFile(BuildImageConfiguration buildConfig, AssemblyConfiguration assemblyConfig) {
        String dockerfile = assemblyConfig.getDockerfile();
        if (dockerfile != null) {
            //return DockerFileBuilder.fromDockerfile();
            
        }
        
        List<String> volumes = buildConfig.getVolumes(); 
        if (assemblyConfig.exportBasedir()) {
            volumes = new ArrayList<>(volumes);
            volumes.add(assemblyConfig.getBasedir());
        }
        
        DockerFileBuilder builder =
                new DockerFileBuilder().basedir(assemblyConfig.getBasedir())
                        .add("maven", "")
                        .expose(buildConfig.getPorts())
                        .env(buildConfig.getEnv())
                        .volumes(volumes);
        
        if (buildConfig.getFrom() != null) {
            builder.baseImage(buildConfig.getFrom());
            builder.command((String[]) null); // Use command from base image (gets overwritten below if explicitely set)
        }
        
        if (buildConfig.getCommand() != null) {
            builder.command(EnvUtil.splitWOnSpaceWithEscape(buildConfig.getCommand()));
        }
        
        return builder;
    }

    private void createArchiveFromAssembly(DockerAssemblyConfigurationSource source) throws MojoExecutionException {
        Assembly assembly = extractAssembly(source);

        try {
            assembly.setId("docker");
            assemblyArchiver.createArchive(assembly, "maven", "dir", source, false);
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
