package org.jolokia.maven.docker;

import java.io.File;
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
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.jolokia.maven.docker.util.DockerAssemblerConfigurationSource;
import org.jolokia.maven.docker.util.MojoParameters;

/**
 * @author roland
 * @since 08.05.14
 */
@Component(role = DockerAssemblyCreator.class)
public class DockerAssemblyCreator {

    @Requirement
    private AssemblyArchiver assemblyArchiver;

    @Requirement
    private AssemblyReader assemblyReader;

    public File create(MojoParameters params, String descriptor, String descriptorRef) throws MojoFailureException, MojoExecutionException {

        AssemblerConfigurationSource config =
                new DockerAssemblerConfigurationSource(params, descriptor, descriptorRef);
        List<Assembly> assemblies = extractAssemblies(config);
        if (assemblies.size() != 1) {
            throw new MojoFailureException("Only one assembly can be used for creating a Docker base image (and not " + assemblies.size() +")");
        }
        createAssemblies(assemblies.get(0),config);

        return config.getOutputDirectory();
    }

    private void createAssemblies(Assembly assembly,AssemblerConfigurationSource config) throws MojoFailureException, MojoExecutionException {
        try {
            assembly.setId("docker");
            assemblyArchiver.createArchive(assembly, ".", "dir", config, false);
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

    private List<Assembly> extractAssemblies(AssemblerConfigurationSource config) throws MojoExecutionException, MojoFailureException {
        try
        {
            return assemblyReader.readAssemblies(config);
        }
        catch (AssemblyReadException e)
        {
            throw new MojoExecutionException( "Error reading assemblies: " + e.getMessage(), e );
        }
        catch (InvalidAssemblerConfigurationException e)
        {
            throw new MojoFailureException( assemblyReader, e.getMessage(), "Mojo configuration is invalid: "
                                                                            + e.getMessage() );
        }

    }


}
