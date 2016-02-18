package org.jolokia.docker.maven.assembly;

import mockit.Injectable;
import mockit.NonStrictExpectations;
import mockit.Tested;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.assembly.AssemblerConfigurationSource;
import org.apache.maven.plugin.assembly.InvalidAssemblerConfigurationException;
import org.apache.maven.plugin.assembly.archive.ArchiveCreationException;
import org.apache.maven.plugin.assembly.archive.AssemblyArchiver;
import org.apache.maven.plugin.assembly.format.AssemblyFormattingException;
import org.apache.maven.plugin.assembly.io.AssemblyReadException;
import org.apache.maven.plugin.assembly.io.AssemblyReader;
import org.apache.maven.plugin.assembly.model.Assembly;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.archiver.manager.ArchiverManager;
import org.codehaus.plexus.util.ReflectionUtils;
import org.jolokia.docker.maven.config.AssemblyConfiguration;
import org.jolokia.docker.maven.config.BuildImageConfiguration;
import org.jolokia.docker.maven.util.AnsiLogger;
import org.jolokia.docker.maven.util.MojoParameters;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DockerAssemblyManagerTest {

    @Tested
    private DockerAssemblyManager assemblyManager;

    @Injectable
    private AssemblyArchiver assemblyArchiver;

    @Injectable
    private AssemblyReader assemblyReader;

    @Injectable
    private ArchiverManager archiverManager;

    @Injectable
    private MappingTrackArchiver trackArchiver;

    @Test
    public void testNoAssembly() {
        BuildImageConfiguration buildConfig = new BuildImageConfiguration();
        AssemblyConfiguration assemblyConfig = buildConfig.getAssemblyConfiguration();

        DockerFileBuilder builder = assemblyManager.createDockerFileBuilder(buildConfig, assemblyConfig, null);
        String content = builder.content();

        assertFalse(content.contains("COPY"));
        assertFalse(content.contains("VOLUME"));
    }

    @Test
    public void testAssembly_Basedir() {
        AssemblyConfiguration assemblyConfig = new AssemblyConfiguration();
        BuildImageConfiguration buildConfig = new BuildImageConfiguration.Builder().assembly(assemblyConfig).build();

        DockerFileBuilder builder = assemblyManager.createDockerFileBuilder(buildConfig, assemblyConfig, null);
        String content = builder.content();

        assertTrue(content, content.contains("COPY maven /maven"));

        assemblyConfig = new AssemblyConfiguration.Builder().basedir("/test").build();
        buildConfig = new BuildImageConfiguration.Builder().assembly(assemblyConfig).build();

        builder = assemblyManager.createDockerFileBuilder(buildConfig, assemblyConfig, null);
        content = builder.content();

        assertTrue(content, content.contains("COPY maven /test"));

        Assembly assembly = new Assembly();

        builder = assemblyManager.createDockerFileBuilder(buildConfig, assemblyConfig, assembly);
        content = builder.content();

        assertTrue(content, content.contains("COPY maven /test"));

        assembly.setBaseDirectory("/newbasedir");

        builder = assemblyManager.createDockerFileBuilder(buildConfig, assemblyConfig, assembly);
        content = builder.content();

        assertTrue(content, content.contains("COPY maven /newbasedir"));


    }

    @Test
    public void assemblyFiles(@Injectable final MojoParameters mojoParams,
                              @Injectable final MavenProject project,
                              @Injectable final Assembly assembly) throws AssemblyFormattingException, ArchiveCreationException, InvalidAssemblerConfigurationException, MojoExecutionException, AssemblyReadException, IllegalAccessException {

        ReflectionUtils.setVariableValueInObject(assemblyManager, "trackArchiver", trackArchiver);

        new NonStrictExpectations() {{
            mojoParams.getOutputDirectory();
            result = "target/"; times = 3;

            mojoParams.getProject();
            result = project;

            project.getBasedir();
            result = ".";

            assemblyReader.readAssemblies((AssemblerConfigurationSource) any);
            result = Arrays.asList(assembly);

        }};

        BuildImageConfiguration buildConfig =
                new BuildImageConfiguration.Builder()
                        .assembly(new AssemblyConfiguration.Builder()
                                          .descriptorRef("artifact")
                                          .build())
                        .build();

        assemblyManager.getAssemblyFiles("testImage", buildConfig, mojoParams, new AnsiLogger(new SystemStreamLog(),true,true));
    }
}
