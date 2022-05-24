package io.fabric8.maven.docker;

import io.fabric8.maven.docker.access.DockerAccess;
import io.fabric8.maven.docker.config.BuildImageConfiguration;
import io.fabric8.maven.docker.config.BuildXConfiguration;
import io.fabric8.maven.docker.config.CopyConfiguration;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.config.ImageConfiguration.Builder;
import io.fabric8.maven.docker.config.RunImageConfiguration;
import io.fabric8.maven.docker.log.LogDispatcher;
import io.fabric8.maven.docker.service.ArchiveService;
import io.fabric8.maven.docker.service.BuildService;
import io.fabric8.maven.docker.service.QueryService;
import io.fabric8.maven.docker.service.RegistryService;
import io.fabric8.maven.docker.service.RunService;
import io.fabric8.maven.docker.service.ServiceHub;
import io.fabric8.maven.docker.util.AnsiLogger;
import io.fabric8.maven.docker.util.AuthConfigFactory;
import io.fabric8.maven.docker.util.GavLabel;
import org.apache.maven.model.Build;
import org.apache.maven.monitor.logging.DefaultLog;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.codehaus.plexus.logging.console.ConsoleLogger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.Mockito;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;

import static io.fabric8.maven.docker.AbstractDockerMojo.CONTEXT_KEY_LOG_DISPATCHER;

abstract class MojoTestBase {

    @TempDir
    public Path temporaryFolder;

    @Mock
    protected AnsiLogger log;

    @Mock
    protected ServiceHub serviceHub;

    @Mock
    protected QueryService queryService;

    @Mock
    protected RegistryService registryService;

    @Mock
    protected RunService runService;

    @Mock
    protected DockerAccess dockerAccess;

    @Mock
    protected ArchiveService archiveService;

    @Mock
    protected BuildService buildService;

    @Mock
    protected MavenProject mavenProject;

    @Mock
    protected Build mavenBuild;

    @Mock
    protected MavenProjectHelper mavenProjectHelper;

    @Mock
    private LogDispatcher logDispatcher;

    @Mock
    protected AuthConfigFactory authConfigFactory;

    protected String projectGroupId;
    protected String projectArtifactId;
    protected String projectVersion;
    protected File projectBaseDirectory;
    protected String projectBuildDirectory;

    protected GavLabel projectGavLabel;

    protected ConsoleLogger consoleLogger;
    protected AnsiLogger ansiLogger;

    protected MojoTestBase() {
        this.consoleLogger = new ConsoleLogger(1, "console");
        this.ansiLogger = new AnsiLogger(new DefaultLog(this.consoleLogger), false, null);
    }

    protected ImageConfiguration singleImageWithoutBuildOrRun() {
        return new ImageConfiguration.Builder()
                .name("example:latest")
                .build();
    }

    protected ImageConfiguration singleImageWithBuild() {
        return singleImageConfiguration(null);
    }

    protected ImageConfiguration singleBuildXImage(String configFile) {
        return singleImageConfiguration(new BuildXConfiguration.Builder()
            .configFile(configFile)
            .platforms(Arrays.asList("linux/amd64", "linux/arm64"))
            .build());
    }

    private ImageConfiguration singleImageConfiguration(BuildXConfiguration buildx) {
        return new Builder()
            .name("example:latest")
            .buildConfig(new BuildImageConfiguration.Builder()
                .from("scratch")
                .buildx(buildx)
                .args(Collections.singletonMap("foo", "bar"))
                .build())
            .build();
    }

    protected ImageConfiguration singleImageWithBuildWithTags(String... tags) {
        return new ImageConfiguration.Builder()
                .name("example:latest")
                .buildConfig(new BuildImageConfiguration.Builder()
                        .from("scratch")
                        .tags(Arrays.asList(tags))
                        .build())
                .build();
    }

    protected ImageConfiguration singleImageWithBuildAndRun() {
        return new ImageConfiguration.Builder()
                .name("example:latest")
                .buildConfig(new BuildImageConfiguration.Builder()
                        .from("scratch")
                        .build())
                .runConfig(new RunImageConfiguration.Builder()
                        .cmd("echo")
                        .build())
                .build();
    }

    protected ImageConfiguration singleImageWithRun() {
        return new ImageConfiguration.Builder()
                .name("example:latest")
                .runConfig(new RunImageConfiguration.Builder()
                        .cmd("echo")
                        .build())
                .build();
    }

    protected ImageConfiguration singleImageWithRunAndAlias(String alias) {
        return new ImageConfiguration.Builder()
                .name("example:latest")
                .alias(alias)
                .runConfig(new RunImageConfiguration.Builder()
                        .cmd("echo")
                        .build())
                .build();
    }

    protected ImageConfiguration singleImageWithCopy(List<CopyConfiguration.Entry> entries) {
        return singleImageWithCopyNamePatternAndCopyEntries(null, entries);
    }

    protected ImageConfiguration singleImageWithCopyNamePatternAndCopyEntries(String copyNamePattern,
            List<CopyConfiguration.Entry> entries) {
        final CopyConfiguration.Builder copyConfigBuilder = new CopyConfiguration.Builder();
        if (entries != null) {
            copyConfigBuilder.entries(entries);
        }
        final Builder builder = new Builder().name("example:latest");
        if (copyNamePattern != null) {
            builder.copyNamePattern(copyNamePattern);
        }
        return builder.copyConfig(copyConfigBuilder.build()).build();
    }

    protected List<ImageConfiguration> twoImagesWithBuild() {
        ImageConfiguration image1 = new ImageConfiguration.Builder()
                .name("example1:latest")
                .alias("example1")
                .buildConfig(new BuildImageConfiguration.Builder()
                        .from("scratch")
                        .build())
                .build();

        ImageConfiguration image2 = new ImageConfiguration.Builder()
                .name("example2:latest")
                .alias("example2")
                .buildConfig(new BuildImageConfiguration.Builder()
                        .from("scratch")
                        .build())
                .build();

        return Arrays.asList(image1, image2);
    }

    protected void givenMavenProject(AbstractDockerMojo mojo) {
        projectGroupId = "mock.group";
        projectArtifactId = "mock-artifact";
        projectVersion = "1.0.0-MOCK";
        Path mockBase = temporaryFolder.resolve("mock-base").toAbsolutePath();
        projectBaseDirectory = mockBase.toFile();
        projectBuildDirectory = mockBase.resolve("mock-target").toString();
        projectGavLabel = new GavLabel(projectGroupId, projectArtifactId, projectVersion);

        Mockito.lenient().when(mavenProject.getProperties()).thenReturn(new Properties());
        Mockito.lenient().when(mavenProject.getBuild()).thenReturn(mavenBuild);
        Mockito.lenient().when(mavenProject.getGroupId()).thenReturn(projectGroupId);
        Mockito.lenient().when(mavenProject.getArtifactId()).thenReturn(projectArtifactId);
        Mockito.lenient().when(mavenProject.getVersion()).thenReturn(projectVersion);
        Mockito.lenient().when(mavenProject.getBasedir()).thenReturn(projectBaseDirectory);
        Mockito.lenient().when(mavenBuild.getDirectory()).thenReturn(projectBuildDirectory);

        mojo.images = Collections.emptyList();
        mojo.resolvedImages = Collections.emptyList();
        mojo.project = mavenProject;
        mojo.log = ansiLogger;
        mojo.outputDirectory= "target/docker";
        mojo.authConfigFactory= authConfigFactory;

        mojo.setPluginContext(new HashMap<>());
        mojo.getPluginContext().put(CONTEXT_KEY_LOG_DISPATCHER, logDispatcher);

        Mockito.lenient().doReturn(queryService).when(serviceHub).getQueryService();
        Mockito.lenient().doReturn(registryService).when(serviceHub).getRegistryService();
        Mockito.lenient().doReturn(runService).when(serviceHub).getRunService();
        Mockito.lenient().doReturn(dockerAccess).when(serviceHub).getDockerAccess();
        Mockito.lenient().doReturn(archiveService).when(serviceHub).getArchiveService();
        Mockito.lenient().doReturn(buildService).when(serviceHub).getBuildService();
    }

    protected void givenResolvedImages(AbstractDockerMojo mojo, List<ImageConfiguration> resolvedImages) {
        mojo.images=resolvedImages;
        mojo.resolvedImages= resolvedImages;
    }

    protected void givenPluginContext(AbstractDockerMojo mojo, Object key, Object value) {
        mojo.getPluginContext().put(key, value);
    }


    protected File resolveMavenProjectPath(String path) {
        if (path == null) {
            return null;
        }
        File file = new File(path);
        if (file.isAbsolute()) {
            return file;
        }
        return new File(projectBaseDirectory, path);
    }

    protected void assertAbsolutePathEquals(final File expected, final File actual) {
        if (expected == null) {
            Assertions.assertNull(actual);
        } else {
            Assertions.assertNotNull(actual);
            try {
                Assertions.assertEquals(expected.getCanonicalPath(), actual.getCanonicalPath());
            } catch (IOException e) {
                throw new AssertionError(e);
            }
        }
    }
}
