package io.fabric8.maven.docker;

import static io.fabric8.maven.docker.AbstractDockerMojo.CONTEXT_KEY_LOG_DISPATCHER;

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

import io.fabric8.maven.docker.access.DockerAccess;
import io.fabric8.maven.docker.config.BuildImageConfiguration;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.config.RunImageConfiguration;
import io.fabric8.maven.docker.log.LogDispatcher;
import io.fabric8.maven.docker.service.QueryService;
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

    @Injectable
    protected Logger log;

    @Mocked
    protected ServiceHub serviceHub;

    @Mocked
    protected QueryService queryService;

    @Mocked
    protected RunService runService;

    @Mocked
    protected DockerAccess dockerAccess;

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
        projectBuildDirectory = "mock-target";
        projectGavLabel = new GavLabel(projectGroupId, projectArtifactId, projectVersion);

        new Expectations() {{
            mavenProject.getProperties(); result = new Properties(); minTimes = 0;
            mavenProject.getBuild(); result = mavenBuild; minTimes = 0;
            mavenProject.getGroupId(); result = projectGroupId; minTimes = 0;
            mavenProject.getArtifactId(); result = projectArtifactId; minTimes = 0;
            mavenProject.getVersion(); result = projectVersion; minTimes = 0;
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
}
