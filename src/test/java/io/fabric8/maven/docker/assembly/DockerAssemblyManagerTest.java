package io.fabric8.maven.docker.assembly;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;

import io.fabric8.maven.docker.config.AssemblyConfiguration;
import io.fabric8.maven.docker.config.BuildImageConfiguration;
import io.fabric8.maven.docker.util.AnsiLogger;
import io.fabric8.maven.docker.util.DockerFileUtil;
import io.fabric8.maven.docker.util.Logger;
import io.fabric8.maven.docker.util.MojoParameters;
import mockit.Expectations;
import mockit.Injectable;
import mockit.Mock;
import mockit.MockUp;
import mockit.Tested;
import mockit.Verifications;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.MavenArtifactRepository;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.assembly.AssemblerConfigurationSource;
import org.apache.maven.plugins.assembly.InvalidAssemblerConfigurationException;
import org.apache.maven.plugins.assembly.archive.ArchiveCreationException;
import org.apache.maven.plugins.assembly.archive.AssemblyArchiver;
import org.apache.maven.plugins.assembly.format.AssemblyFormattingException;
import org.apache.maven.plugins.assembly.io.AssemblyReadException;
import org.apache.maven.plugins.assembly.io.AssemblyReader;
import org.apache.maven.plugins.assembly.model.Assembly;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.apache.maven.project.MavenProject;
import org.apache.maven.settings.Settings;
import org.codehaus.plexus.archiver.manager.ArchiverManager;
import org.codehaus.plexus.interpolation.fixed.FixedStringSearchInterpolator;
import org.codehaus.plexus.util.ReflectionUtils;
import org.junit.Test;

import static org.junit.Assert.assertFalse;

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

        DockerFileBuilder builder = assemblyManager.createDockerFileBuilder(buildConfig, assemblyConfig);
        String content = builder.content();

        assertFalse(content.contains("COPY"));
        assertFalse(content.contains("VOLUME"));
    }

    @Test
    public void assemblyFiles(@Injectable final MojoParameters mojoParams,
                              @Injectable final MavenProject project,
                              @Injectable final Assembly assembly) throws AssemblyFormattingException, ArchiveCreationException, InvalidAssemblerConfigurationException, MojoExecutionException, AssemblyReadException, IllegalAccessException {

        ReflectionUtils.setVariableValueInObject(assemblyManager, "trackArchiver", trackArchiver);

        new Expectations() {{
            mojoParams.getOutputDirectory();
            result = "target/"; times = 3;

            mojoParams.getProject();
            project.getBasedir();
            result = ".";

            assemblyReader.readAssemblies((AssemblerConfigurationSource) any);
            result = Arrays.asList(assembly);

        }};

        BuildImageConfiguration buildConfig = createBuildConfig();

        assemblyManager.getAssemblyFiles("testImage", buildConfig, mojoParams, new AnsiLogger(new SystemStreamLog(),true,"build"));
    }

    @Test
    public void testCopyValidVerifyGivenDockerfile(@Injectable final Logger logger) throws IOException {
        BuildImageConfiguration buildConfig = createBuildConfig();

        assemblyManager.verifyGivenDockerfile(
            new File(getClass().getResource("/docker/Dockerfile_assembly_verify_copy_valid.test").getPath()),
            buildConfig,
            createInterpolator(buildConfig),
            logger);

        new Verifications() {{
            logger.warn(anyString, (Object []) any); times = 0;
        }};

    }

    @Test
    public void testCopyInvalidVerifyGivenDockerfile(@Injectable final Logger logger) throws IOException {
        BuildImageConfiguration buildConfig = createBuildConfig();

        assemblyManager.verifyGivenDockerfile(
            new File(getClass().getResource("/docker/Dockerfile_assembly_verify_copy_invalid.test").getPath()),
            buildConfig, createInterpolator(buildConfig),
            logger);

        new Verifications() {{
            logger.warn(anyString, (Object []) any); times = 1;
        }};

    }

    @Test
    public void testCopyChownValidVerifyGivenDockerfile(@Injectable final Logger logger) throws IOException {
        BuildImageConfiguration buildConfig = createBuildConfig();

        assemblyManager.verifyGivenDockerfile(
            new File(getClass().getResource("/docker/Dockerfile_assembly_verify_copy_chown_valid.test").getPath()),
            buildConfig,
            createInterpolator(buildConfig),
            logger);

        new Verifications() {{
            logger.warn(anyString, (Object []) any); times = 0;
        }};

    }

    private BuildImageConfiguration createBuildConfig() {
        return new BuildImageConfiguration.Builder()
                .assembly(new AssemblyConfiguration.Builder()
                        .descriptorRef("artifact")
                        .build())
                .build();
    }

    private FixedStringSearchInterpolator createInterpolator(BuildImageConfiguration buildConfig) {
        MavenProject project = new MavenProject();
        project.setArtifactId("docker-maven-plugin");

        return DockerFileUtil.createInterpolator(mockMojoParams(project), buildConfig.getFilter());
    }


    private MojoParameters mockMojoParams(MavenProject project) {
        Settings settings = new Settings();
        ArtifactRepository localRepository = new MavenArtifactRepository() {
            @Mock
            public String getBasedir() {
                return "repository";
            }
        };
        @SuppressWarnings("deprecation")
        MavenSession session = new MavenSession(null, settings, localRepository, null, null, Collections.<String>emptyList(), ".", null, null, new Date());
        return new MojoParameters(session, project, null, null, null, settings, "src", "target", Collections.singletonList(project));
    }

}
