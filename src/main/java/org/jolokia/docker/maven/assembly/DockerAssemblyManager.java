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

        File outputDir = source.getOutputDirectory();
        File tarball = new File(source.getTemporaryRootDirectory(), "docker-build.tar");
        
        try {
            createArchiveFromAssembly(source);
            
            String dockerFileDir = assemblyConfig.getDockerFileDir();
            if (dockerFileDir != null) {
               dockerFileDir = params.getSourceDirectory() + File.separator + dockerFileDir;  
            } else {
                createDockerFile(buildConfig, assemblyConfig, outputDir);
            }
                
            fillTarball(tarball, new File(dockerFileDir), outputDir);    
        }
        catch (IOException e) {
            throw new MojoExecutionException(String.format("Cannot create DockerFile in %s", outputDir, e));
        }

        return tarball;
    }
    
    private File fillTarball(File archive, File dockerFileDir, File outputDir) throws MojoExecutionException {
        try {
            Archiver archiver = archiverManager.getArchiver("tar");
            archiver.addFileSet(DefaultFileSet.fileSet(outputDir));
            if (dockerFileDir != null) {
                archiver.addFileSet(DefaultFileSet.fileSet(dockerFileDir));
            }    
            archiver.setDestFile(archive);
            archiver.createArchive();
            return archive;
        } catch (NoSuchArchiverException e) {
            throw new MojoExecutionException("No archiver for type 'tar' found", e);
        } catch (IOException e) {
            throw new MojoExecutionException("Cannot create archive " + archive, e);
        }
    }

    private void createDockerFile(BuildImageConfiguration buildConfig, AssemblyConfiguration assemblyConfig, File outputDir) 
        throws IOException {
        DockerFileBuilder builder =
                new DockerFileBuilder().basedir(assemblyConfig.getBasedir())
                        .add("maven", "")
                        .env(buildConfig.getEnv())
                        .exportBasedir(assemblyConfig.exportBasedir())
                        .expose(buildConfig.getPorts())
                        .volumes(buildConfig.getVolumes());
        
        if (buildConfig.getFrom() != null) {
            builder.baseImage(buildConfig.getFrom());
            builder.command((String[]) null); // Use command from base image (gets overwritten below if explicitely set)
        }
        
        if (buildConfig.getCommand() != null) {
            builder.command(EnvUtil.splitWOnSpaceWithEscape(buildConfig.getCommand()));
        }
        
        builder.write(outputDir);
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
