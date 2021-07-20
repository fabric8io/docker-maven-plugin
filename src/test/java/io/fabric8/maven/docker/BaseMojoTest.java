package io.fabric8.maven.docker;

import static io.fabric8.maven.docker.AbstractDockerMojo.CONTEXT_KEY_LOG_DISPATCHER;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;

import org.apache.maven.model.Build;
import org.apache.maven.monitor.logging.DefaultLog;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.codehaus.plexus.logging.console.ConsoleLogger;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

import io.fabric8.maven.docker.access.DockerAccess;
import io.fabric8.maven.docker.config.BuildImageConfiguration;
import io.fabric8.maven.docker.config.CopyConfiguration;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.config.ImageConfiguration.Builder;
import io.fabric8.maven.docker.config.RunImageConfiguration;
import io.fabric8.maven.docker.log.LogDispatcher;
import io.fabric8.maven.docker.service.ArchiveService;
import io.fabric8.maven.docker.service.QueryService;
import io.fabric8.maven.docker.service.RegistryService;
import io.fabric8.maven.docker.service.RunService;
import io.fabric8.maven.docker.service.ServiceHub;
import io.fabric8.maven.docker.util.AnsiLogger;
import io.fabric8.maven.docker.util.GavLabel;
import io.fabric8.maven.docker.util.Logger;
import mockit.Deencapsulation;
import mockit.Expectations;
import mockit.Injectable;
import mockit.Mocked;

public class BaseMojoTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Injectable
    protected AnsiLogger log;

    @Mocked
    protected ServiceHub serviceHub;

    @Mocked
    protected QueryService queryService;

    @Mocked
    protected RegistryService registryService;

    @Mocked
    protected RunService runService;

    @Mocked
    protected DockerAccess dockerAccess;

    @Mocked
    protected ArchiveService archiveService;

    @Mocked
    protected MavenProject mavenProject;

    @Mocked
    protected Build mavenBuild;

    @Mocked
    protected MavenProjectHelper mavenProjectHelper;

    @Mocked
    private LogDispatcher logDispatcher;

    protected String projectGroupId;
    protected String projectArtifactId;
    protected String projectVersion;
    protected String projectBaseDirectory;
    protected String projectBuildDirectory;

    protected GavLabel projectGavLabel;

    protected ConsoleLogger consoleLogger;
    protected AnsiLogger ansiLogger;

    protected BaseMojoTest() {
        this.consoleLogger = new ConsoleLogger(1, "console");
        this.ansiLogger = new AnsiLogger(new DefaultLog(this.consoleLogger), false, null);
    }

    protected ImageConfiguration singleImageWithoutBuildOrRun() {
        return new ImageConfiguration.Builder()
                .name("example:latest")
                .build();
    }

    protected ImageConfiguration singleImageWithBuild() {
        return new ImageConfiguration.Builder()
                .name("example:latest")
                .buildConfig(new BuildImageConfiguration.Builder()
                        .from("scratch")
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
        try {
            projectBaseDirectory = temporaryFolder.newFolder("mock-base").getAbsolutePath();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        projectBuildDirectory = new File(projectBaseDirectory, "mock-target").getAbsolutePath();
        projectGavLabel = new GavLabel(projectGroupId, projectArtifactId, projectVersion);

        new Expectations() {{
            mavenProject.getProperties(); result = new Properties(); minTimes = 0;
            mavenProject.getBuild(); result = mavenBuild; minTimes = 0;
            mavenProject.getGroupId(); result = projectGroupId; minTimes = 0;
            mavenProject.getArtifactId(); result = projectArtifactId; minTimes = 0;
            mavenProject.getVersion(); result = projectVersion; minTimes = 0;
            mavenProject.getBasedir(); result = projectBaseDirectory; minTimes = 0;
            mavenBuild.getDirectory(); result = projectBuildDirectory; minTimes = 0;
        }};

        Deencapsulation.setField(mojo, "images", Collections.emptyList());
        Deencapsulation.setField(mojo, "resolvedImages", Collections.emptyList());
        Deencapsulation.setField(mojo, "project", mavenProject);
        Deencapsulation.setField(mojo, "log", this.ansiLogger);

        mojo.setPluginContext(new HashMap());
        mojo.getPluginContext().put(CONTEXT_KEY_LOG_DISPATCHER, logDispatcher);
    }

    protected void givenResolvedImages(AbstractDockerMojo mojo, List<ImageConfiguration> resolvedImages) {
        Deencapsulation.setField(mojo, "images", resolvedImages);
        Deencapsulation.setField(mojo, "resolvedImages", resolvedImages);
    }

    protected void givenPluginContext(AbstractDockerMojo mojo, Object key, Object value) {
        mojo.getPluginContext().put(key, value);
    }

    protected void givenCopyAll(AbstractDockerMojo mojo) {
        Deencapsulation.setField(mojo, "copyAll", true);
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
            assertNull(actual);
        } else {
            assertNotNull(actual);
            try {
                assertEquals(expected.getCanonicalPath(), actual.getCanonicalPath());
            } catch (IOException e) {
                throw new AssertionError(e);
            }
        }
    }
}
