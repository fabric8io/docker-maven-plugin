package io.fabric8.maven.docker.config.handler.property;/*
 *
 * Copyright 2014 Roland Huss
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import io.fabric8.maven.docker.config.Arguments;
import io.fabric8.maven.docker.config.AssemblyConfiguration;
import io.fabric8.maven.docker.config.BuildImageConfiguration;
import io.fabric8.maven.docker.config.CleanupMode;
import io.fabric8.maven.docker.config.ConfigHelper;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.config.LogConfiguration;
import io.fabric8.maven.docker.config.RestartPolicy;
import io.fabric8.maven.docker.config.RunImageConfiguration;
import io.fabric8.maven.docker.config.UlimitConfig;
import io.fabric8.maven.docker.config.WaitConfiguration;
import io.fabric8.maven.docker.config.handler.AbstractConfigHandlerTest;
import mockit.Expectations;
import mockit.Mocked;
import org.apache.maven.project.MavenProject;
import org.junit.Before;
import org.junit.Test;

import static io.fabric8.maven.docker.config.BuildImageConfiguration.DEFAULT_CLEANUP;
import static io.fabric8.maven.docker.config.BuildImageConfiguration.DEFAULT_FILTER;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * @author roland
 * @since 05/12/14
 */

public class PropertyConfigHandlerTest extends AbstractConfigHandlerTest {

    private PropertyConfigHandler configHandler;
    private ImageConfiguration imageConfiguration;

    @Mocked
    private MavenProject project;

    @Before
    public void setUp() throws Exception {
        configHandler = new PropertyConfigHandler();
        imageConfiguration = buildAnUnresolvedImage();
    }

    @Test
    public void testSkipBuild() {
        assertFalse(resolveExternalImageConfig(getSkipTestData(ConfigKey.SKIP_BUILD, false)).getBuildConfiguration().skip());
        assertTrue(resolveExternalImageConfig(getSkipTestData(ConfigKey.SKIP_BUILD, true)).getBuildConfiguration().skip());

        assertFalse(resolveExternalImageConfig(new String[] {k(ConfigKey.NAME), "image", k(ConfigKey.FROM), "busybox"}).getBuildConfiguration().skip());
    }

    @Test
    public void testSkipRun() {
        assertFalse(resolveExternalImageConfig(getSkipTestData(ConfigKey.SKIP_RUN, false)).getRunConfiguration().skip());
        assertTrue(resolveExternalImageConfig(getSkipTestData(ConfigKey.SKIP_RUN, true)).getRunConfiguration().skip());

        assertFalse(resolveExternalImageConfig(new String[] {k(ConfigKey.NAME), "image"}).getRunConfiguration().skip());
    }

    @Test
    public void testType() throws Exception {
        assertNotNull(configHandler.getType());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testEmpty() throws Exception {
        resolveImage(imageConfiguration, props());
    }

    @Test
    public void testPorts() {
        List<ImageConfiguration> configs = resolveImage(
                imageConfiguration,props(
                        "docker.name","demo",
                        "docker.ports.1", "jolokia.port:8080",
                        "docker.ports.2", "9090",
                        "docker.ports.3", "0.0.0.0:80:80",
                        "docker.from", "busybox"
                                        ));
        assertEquals(1,configs.size());
        RunImageConfiguration runConfig = configs.get(0).getRunConfiguration();
        List<String> portsAsList = runConfig.getPorts();
        String[] ports = new ArrayList<>(portsAsList).toArray(new String[portsAsList.size()]);
        assertArrayEquals(new String[] {
                "jolokia.port:8080",
                "9090",
                "0.0.0.0:80:80"
        },ports);
        BuildImageConfiguration buildConfig = configs.get(0).getBuildConfiguration();
        ports = new ArrayList<>(buildConfig.getPorts()).toArray(new String[buildConfig.getPorts().size()]);
        assertArrayEquals(new String[]{"8080", "9090", "80"}, ports);
    }


    @Test
    public void testPortsFromConfigAndProperties() {
        imageConfiguration = new ImageConfiguration.Builder()
                .externalConfig(new HashMap<String, String>())
                .buildConfig(new BuildImageConfiguration.Builder()
                        .ports(Arrays.asList("1234"))
                        .cacheFrom((Arrays.asList("foo/bar:latest")))
                        .build()
                )
                .runConfig(new RunImageConfiguration.Builder()
                    .ports(Arrays.asList("jolokia.port:1234"))
                    .build()
                )
                .build();

        makeExternalConfigUse(PropertyMode.Override);

        List<ImageConfiguration> configs = resolveImage(
                imageConfiguration,props(
                        "docker.name","demo",
                        "docker.ports.1", "9090",
                        "docker.ports.2", "0.0.0.0:80:80",
                        "docker.from", "busybox"
                ));
        assertEquals(1,configs.size());
        RunImageConfiguration runConfig = configs.get(0).getRunConfiguration();
        List<String> portsAsList = runConfig.getPorts();
        String[] ports = new ArrayList<>(portsAsList).toArray(new String[portsAsList.size()]);
        assertArrayEquals(new String[] {
                "9090",
                "0.0.0.0:80:80",
                "jolokia.port:1234"
        },ports);
        BuildImageConfiguration buildConfig = configs.get(0).getBuildConfiguration();
        ports = new ArrayList<>(buildConfig.getPorts()).toArray(new String[buildConfig.getPorts().size()]);
        assertArrayEquals(new String[]{"9090", "80", "1234"}, ports);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidPropertyMode() {
        makeExternalConfigUse(PropertyMode.Override);
        imageConfiguration.getExternalConfig().put("mode", "invalid");

        resolveImage(imageConfiguration,props());
    }

    @Test
    public void testRunCommands() {
        List<ImageConfiguration> configs = resolveImage(
            imageConfiguration,props(
                "docker.from", "base",
                "docker.name","demo",
                "docker.run.1", "foo",
                "docker.run.2", "bar",
                "docker.run.3", "wibble")
        );

        assertEquals(1, configs.size());

        BuildImageConfiguration buildConfig = configs.get(0).getBuildConfiguration();
        String[] runCommands = new ArrayList<>(buildConfig.getRunCmds()).toArray(new String[buildConfig.getRunCmds().size()]);
        assertArrayEquals(new String[]{"foo", "bar", "wibble"}, runCommands);
    }

    @Test
    public void testShell() {
        List<ImageConfiguration> configs = resolveImage(
                imageConfiguration,props(
                        "docker.from", "base",
                        "docker.name","demo",
                        "docker.shell", "/bin/sh -c")
        );

        assertEquals(1, configs.size());

        BuildImageConfiguration buildConfig = configs.get(0).getBuildConfiguration();
        String[] shell = new ArrayList<>(buildConfig.getShell().asStrings()).toArray(new String[buildConfig.getShell().asStrings().size()]);
        assertArrayEquals(new String[]{"/bin/sh", "-c"}, shell);
    }

    @Test
    public void testRunCommandsFromPropertiesAndConfig() {
        imageConfiguration = new ImageConfiguration.Builder()
                .externalConfig(new HashMap<String, String>())
                .buildConfig(new BuildImageConfiguration.Builder()
                        .runCmds(Arrays.asList("some","ignored","value"))
                        .cacheFrom((Arrays.asList("foo/bar:latest")))
                        .build()
                )
                .build();

        makeExternalConfigUse(PropertyMode.Override);

        List<ImageConfiguration> configs = resolveImage(
                imageConfiguration,props(
                        "docker.from", "base",
                        "docker.name","demo",
                        "docker.run.1", "propconf",
                        "docker.run.2", "withrun",
                        "docker.run.3", "used")
        );

        assertEquals(1, configs.size());

        BuildImageConfiguration buildConfig = configs.get(0).getBuildConfiguration();
        String[] runCommands = new ArrayList<>(buildConfig.getRunCmds()).toArray(new String[buildConfig.getRunCmds().size()]);
        assertArrayEquals(new String[]{"propconf", "withrun", "used"}, runCommands);
    }

    @Test
    public void testShellFromPropertiesAndConfig() {
        imageConfiguration = new ImageConfiguration.Builder()
                .externalConfig(new HashMap<String, String>())
                .buildConfig(new BuildImageConfiguration.Builder()
                        .shell(new Arguments(Arrays.asList("some","ignored","value")))
                        .cacheFrom((Arrays.asList("foo/bar:latest")))
                        .build()
                )
                .build();

        makeExternalConfigUse(PropertyMode.Override);

        List<ImageConfiguration> configs = resolveImage(
                imageConfiguration,props(
                        "docker.from", "base",
                        "docker.name","demo",
                        "docker.shell", "propconf withrun used")
        );

        assertEquals(1, configs.size());

        BuildImageConfiguration buildConfig = configs.get(0).getBuildConfiguration();
        String[] shell = new ArrayList<>(buildConfig.getShell().asStrings()).toArray(new String[buildConfig.getShell().asStrings().size()]);
        assertArrayEquals(new String[]{"propconf", "withrun", "used"}, shell);
    }

    @Test
    public void testRunCommandsFromConfigAndProperties() {
        imageConfiguration = new ImageConfiguration.Builder()
                .externalConfig(externalConfigMode(PropertyMode.Fallback))
                .buildConfig(new BuildImageConfiguration.Builder()
                        .runCmds(Arrays.asList("some","configured","value"))
                        .cacheFrom((Arrays.asList("foo/bar:latest")))
                        .build()
                )
                .build();

        List<ImageConfiguration> configs = resolveImage(
                imageConfiguration,props(
                        "docker.from", "base",
                        "docker.name","demo",
                        "docker.run.1", "this",
                        "docker.run.2", "is",
                        "docker.run.3", "ignored")
        );

        assertEquals(1, configs.size());

        BuildImageConfiguration buildConfig = configs.get(0).getBuildConfiguration();
        String[] runCommands = new ArrayList<>(buildConfig.getRunCmds()).toArray(new String[buildConfig.getRunCmds().size()]);
        assertArrayEquals(new String[]{"some", "configured", "value"}, runCommands);
    }

    @Test
    public void testEntrypoint() {
        List<ImageConfiguration> configs = resolveImage(
                imageConfiguration,props(
                        "docker.from", "base",
                        "docker.name","demo",
                        "docker.entrypoint", "/entrypoint.sh --from-property")
        );

        assertEquals(1, configs.size());

        BuildImageConfiguration buildConfig = configs.get(0).getBuildConfiguration();
        assertArrayEquals(new String[]{"/entrypoint.sh", "--from-property"}, buildConfig.getEntryPoint().asStrings().toArray());
    }

    @Test
    public void testEntrypointExecFromConfig() {
        imageConfiguration = new ImageConfiguration.Builder()
                .externalConfig(externalConfigMode(PropertyMode.Fallback))
                .buildConfig(new BuildImageConfiguration.Builder()
                        .entryPoint(new Arguments(Arrays.asList("/entrypoint.sh", "--from-property")))
                        .cacheFrom((Arrays.asList("foo/bar:latest")))
                        .build()
                )
                .build();

        List<ImageConfiguration> configs = resolveImage(
                imageConfiguration,props(
                        "docker.from", "base",
                        "docker.name","demo")
        );

        assertEquals(1, configs.size());

        BuildImageConfiguration buildConfig = configs.get(0).getBuildConfiguration();
        assertArrayEquals(new String[]{"/entrypoint.sh", "--from-property"}, buildConfig.getEntryPoint().asStrings().toArray());
    }

    @Test
    public void testDefaultLogEnabledConfiguration() {
        imageConfiguration = new ImageConfiguration.Builder()
                .externalConfig(externalConfigMode(PropertyMode.Override))
                .buildConfig(new BuildImageConfiguration.Builder()
                        .cacheFrom((Arrays.asList("foo/bar:latest")))
                        .build()
                )
                .build();

        List<ImageConfiguration> configs = resolveImage(
                imageConfiguration, props(
                        "docker.from", "base",
                        "docker.name", "demo")
        );

        assertEquals(1, configs.size());

        RunImageConfiguration runConfiguration = configs.get(0).getRunConfiguration();
        assertNull(runConfiguration.getLogConfiguration().isEnabled());
        assertFalse(runConfiguration.getLogConfiguration().isActivated());

        // If any log property is set, enabled shall be true by default
        configs = resolveImage(
                imageConfiguration, props(
                        "docker.from", "base",
                        "docker.name", "demo",
                        "docker.log.color", "green")
        );

        runConfiguration = getRunImageConfiguration(configs);
        assertNull(runConfiguration.getLogConfiguration().isEnabled());
        assertTrue(runConfiguration.getLogConfiguration().isActivated());
        assertEquals("green", runConfiguration.getLogConfiguration().getColor());


        // If image configuration has non-blank log configuration, it should become enabled
        imageConfiguration = new ImageConfiguration.Builder()
                .externalConfig(externalConfigMode(PropertyMode.Override))
                .runConfig(new RunImageConfiguration.Builder()
                        .log(new LogConfiguration.Builder().color("red").build())
                        .build()
                )
                .build();

        configs = resolveImage(
                imageConfiguration, props(
                        "docker.from", "base",
                        "docker.name", "demo")
        );

        runConfiguration = getRunImageConfiguration(configs);
        assertNull(runConfiguration.getLogConfiguration().isEnabled());
        assertTrue(runConfiguration.getLogConfiguration().isActivated());
        assertEquals("red", runConfiguration.getLogConfiguration().getColor());


        // and if set by property, still enabled but overrides
        configs = resolveImage(
                imageConfiguration, props(
                        "docker.from", "base",
                        "docker.name", "demo",
                        "docker.log.color", "yellow")
        );

        runConfiguration = getRunImageConfiguration(configs);
        assertNull(runConfiguration.getLogConfiguration().isEnabled());
        assertTrue(runConfiguration.getLogConfiguration().isActivated());
        assertEquals("yellow", runConfiguration.getLogConfiguration().getColor());


        // Fallback works as well
        makeExternalConfigUse(PropertyMode.Fallback);
        configs = resolveImage(
                imageConfiguration, props(
                        "docker.from", "base",
                        "docker.name", "demo",
                        "docker.log.color", "yellow")
        );

        runConfiguration = getRunImageConfiguration(configs);
        assertNull(runConfiguration.getLogConfiguration().isEnabled());
        assertTrue(runConfiguration.getLogConfiguration().isActivated());
        assertEquals("red", runConfiguration.getLogConfiguration().getColor());
    }

    @Test
    public void testExplicitLogEnabledConfiguration() {
        imageConfiguration = new ImageConfiguration.Builder()
                .externalConfig(externalConfigMode(PropertyMode.Override))
                .runConfig(new RunImageConfiguration.Builder()
                        .log(new LogConfiguration.Builder().color("red").build())
                        .build()
                )
                .build();

        // Explicitly enabled
        List<ImageConfiguration> configs = resolveImage(
                imageConfiguration, props(
                        "docker.from", "base",
                        "docker.name", "demo",
                        "docker.log.enabled", "true")
        );

        RunImageConfiguration runConfiguration = getRunImageConfiguration(configs);
        assertTrue(runConfiguration.getLogConfiguration().isEnabled());
        assertTrue(runConfiguration.getLogConfiguration().isActivated());
        assertEquals("red", runConfiguration.getLogConfiguration().getColor());

        // Explicitly disabled
        makeExternalConfigUse(PropertyMode.Override);
        configs = resolveImage(
                imageConfiguration,props(
                        "docker.from", "base",
                        "docker.name","demo",
                        "docker.log.color", "yellow",
                        "docker.log.enabled", "false")
        );

        runConfiguration = getRunImageConfiguration(configs);
        assertFalse(runConfiguration.getLogConfiguration().isEnabled());
        assertFalse(runConfiguration.getLogConfiguration().isActivated());
        assertEquals("yellow", runConfiguration.getLogConfiguration().getColor());


        // Disabled by config
        imageConfiguration = new ImageConfiguration.Builder()
                .externalConfig(externalConfigMode(PropertyMode.Fallback))
                .runConfig(new RunImageConfiguration.Builder()
                        .log(new LogConfiguration.Builder().enabled(false).color("red").build())
                        .build()
                )
                .build();

        configs = resolveImage(
                imageConfiguration,props(
                        "docker.from", "base",
                        "docker.name","demo")
        );

        runConfiguration = getRunImageConfiguration(configs);
        assertFalse(runConfiguration.getLogConfiguration().isEnabled());
        assertFalse(runConfiguration.getLogConfiguration().isActivated());
        assertEquals("red", runConfiguration.getLogConfiguration().getColor());

        // Enabled by property, with override
        makeExternalConfigUse(PropertyMode.Override);
        configs = resolveImage(
                imageConfiguration,props(
                        "docker.from", "base",
                        "docker.name","demo",
                        "docker.log.enabled", "true")
        );

        runConfiguration = getRunImageConfiguration(configs);
        assertTrue(runConfiguration.getLogConfiguration().isEnabled());
        assertTrue(runConfiguration.getLogConfiguration().isActivated());
        assertEquals("red", runConfiguration.getLogConfiguration().getColor());

        // Disabled with property too
        configs = resolveImage(
                imageConfiguration,props(
                        "docker.from", "base",
                        "docker.name","demo",
                        "docker.log.enabled", "false")
        );

        runConfiguration = getRunImageConfiguration(configs);
        assertFalse(runConfiguration.getLogConfiguration().isEnabled());
        assertFalse(runConfiguration.getLogConfiguration().isActivated());
        assertEquals("red", runConfiguration.getLogConfiguration().getColor());
    }

    @Test
    public void testLogFile() {
        imageConfiguration = new ImageConfiguration.Builder()
                .externalConfig(externalConfigMode(PropertyMode.Override))
                .runConfig(new RunImageConfiguration.Builder()
                        .log(new LogConfiguration.Builder().file("myfile").build())
                        .build()
                )
                .build();

        List<ImageConfiguration> configs = resolveImage(
                imageConfiguration, props(
                        "docker.from", "base",
                        "docker.name", "demo")
        );

        assertEquals(1, configs.size());

        RunImageConfiguration runConfiguration = configs.get(0).getRunConfiguration();
        assertNull(runConfiguration.getLogConfiguration().isEnabled());
        assertTrue(runConfiguration.getLogConfiguration().isActivated());
        assertEquals("myfile", runConfiguration.getLogConfiguration().getFileLocation());

        imageConfiguration = new ImageConfiguration.Builder()
                .externalConfig(externalConfigMode(PropertyMode.Override))
                .runConfig(new RunImageConfiguration.Builder()
                        .build()
                )
                .build();

        configs = resolveImage(
                imageConfiguration, props(
                        "docker.from", "base",
                        "docker.name", "demo",
                        "docker.log.file", "myfilefromprop")
        );

        assertEquals(1, configs.size());

        runConfiguration = configs.get(0).getRunConfiguration();
        assertNull(runConfiguration.getLogConfiguration().isEnabled());
        assertTrue(runConfiguration.getLogConfiguration().isActivated());
        assertEquals("myfilefromprop", runConfiguration.getLogConfiguration().getFileLocation());
    }

    private RunImageConfiguration getRunImageConfiguration(List<ImageConfiguration> configs) {
        assertEquals(1, configs.size());
        return configs.get(0).getRunConfiguration();
    }

    @Test
    public void testBuildFromDockerFileMerged() {
        imageConfiguration = new ImageConfiguration.Builder()
                .name("myimage")
                .externalConfig(externalConfigMode(PropertyMode.Override))
                .buildConfig(new BuildImageConfiguration.Builder()
                        .dockerFile("/some/path")
                        .cacheFrom((Arrays.asList("foo/bar:latest")))
                        .build()
                )
                .build();

        List<ImageConfiguration> configs = resolveImage(
                imageConfiguration, props()
        );

        assertEquals(1, configs.size());

        BuildImageConfiguration buildConfiguration = configs.get(0).getBuildConfiguration();
        assertNotNull(buildConfiguration);
        buildConfiguration.initAndValidate(null);

        Path absolutePath = Paths.get(".").toAbsolutePath();
        String expectedPath = absolutePath.getRoot() + "some" + File.separator + "path";
        assertEquals(expectedPath, buildConfiguration.getDockerFile().getAbsolutePath());
    }

    @Test
    public void testEnvAndLabels() throws Exception {
        List<ImageConfiguration> configs = resolveImage(
                imageConfiguration,props(
                    "docker.from", "baase",
                        "docker.name","demo",
                        "docker.env.HOME", "/tmp",
                        "docker.env.root.dir", "/bla",
                        "docker.labels.version", "1.0.0",
                        "docker.labels.blub.bla.foobar", "yep"
                                        ));

        assertEquals(1,configs.size());
        ImageConfiguration calcConfig = configs.get(0);
        for (Map env : new Map[] { calcConfig.getBuildConfiguration().getEnv(),
                                   calcConfig.getRunConfiguration().getEnv()}) {
            assertEquals(2,env.size());
            assertEquals("/tmp",env.get("HOME"));
            assertEquals("/bla",env.get("root.dir"));
        }
        for (Map labels : new Map[] { calcConfig.getBuildConfiguration().getLabels(),
                                      calcConfig.getRunConfiguration().getLabels()}) {
            assertEquals(2, labels.size());
            assertEquals("1.0.0", labels.get("version"));
            assertEquals("yep", labels.get("blub.bla.foobar"));
        }
    }


    @Test
    public void testSpecificEnv() throws Exception {
        List<ImageConfiguration> configs = resolveImage(
                imageConfiguration,props(
                        "docker.from", "baase",
                        "docker.name","demo",
                        "docker.envBuild.HOME", "/tmp",
                        "docker.envRun.root.dir", "/bla"
                ));

        assertEquals(1,configs.size());
        ImageConfiguration calcConfig = configs.get(0);

        Map<String, String> env;

        env = calcConfig.getBuildConfiguration().getEnv();
        assertEquals(1,env.size());
        assertEquals("/tmp",env.get("HOME"));

        env = calcConfig.getRunConfiguration().getEnv();
        assertEquals(1,env.size());
        assertEquals("/bla",env.get("root.dir"));
    }

    @Test
    public void testMergedEnv() throws Exception {
        List<ImageConfiguration> configs = resolveImage(
                imageConfiguration,props(
                        "docker.from", "baase",
                        "docker.name","demo",
                        "docker.env.HOME", "/tmp",
                        "docker.envBuild.HOME", "/var/tmp",
                        "docker.envRun.root.dir", "/bla"
                ));

        assertEquals(1,configs.size());
        ImageConfiguration calcConfig = configs.get(0);

        Map<String, String> env;

        env = calcConfig.getBuildConfiguration().getEnv();
        assertEquals(1,env.size());
        assertEquals("/var/tmp",env.get("HOME"));

        env = calcConfig.getRunConfiguration().getEnv();
        assertEquals(2,env.size());
        assertEquals("/tmp",env.get("HOME"));
        assertEquals("/bla",env.get("root.dir"));
    }

    @Test
    public void testAssembly() throws Exception {
        List<ImageConfiguration> configs = resolveImage(imageConfiguration, props(getTestAssemblyData()));
        assertEquals(1, configs.size());

        AssemblyConfiguration config = configs.get(0).getBuildConfiguration().getAssemblyConfiguration();
        assertEquals("user", config.getUser());
        assertEquals("project", config.getDescriptorRef());
        assertFalse(config.exportTargetDir());
        assertTrue(config.isIgnorePermissions());
    }

    @Test
    public void testNoCleanup() throws Exception {
        String[] testData = new String[] {k(ConfigKey.NAME), "image", k(ConfigKey.CLEANUP), "none", k(ConfigKey.FROM), "base" };

        ImageConfiguration config = resolveExternalImageConfig(testData);
        assertEquals(CleanupMode.NONE, config.getBuildConfiguration().cleanupMode());
    }

    @Test
    public void testNoBuildConfig() throws Exception {
        String[] testData = new String[] {k(ConfigKey.NAME), "image" };

        ImageConfiguration config = resolveExternalImageConfig(testData);
        assertNull(config.getBuildConfiguration());
    }

    @Test
    public void testDockerfile() throws Exception {
        String[] testData = new String[] { k(ConfigKey.NAME), "image", k(ConfigKey.DOCKER_FILE_DIR), "src/main/docker/", k(ConfigKey.FROM), "busybox" };
        ImageConfiguration config = resolveExternalImageConfig(testData);
        config.initAndValidate(ConfigHelper.NameFormatter.IDENTITY, null);
        assertTrue(config.getBuildConfiguration().isDockerFileMode());
        assertEquals(new File("src/main/docker/Dockerfile"), config.getBuildConfiguration().getDockerFile());
    }

    @Test
    public void testDockerArchive() {
        String[] testData = new String[] { k(ConfigKey.NAME), "image", k(ConfigKey.DOCKER_ARCHIVE), "dockerLoad.tar", k(ConfigKey.FROM), "busybox" };
        ImageConfiguration config = resolveExternalImageConfig(testData);
        config.initAndValidate(ConfigHelper.NameFormatter.IDENTITY, null);
        assertFalse(config.getBuildConfiguration().isDockerFileMode());
        assertEquals(new File("dockerLoad.tar"), config.getBuildConfiguration().getDockerArchive());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidDockerFileArchiveConfig() {
        String[] testData = new String[] { k(ConfigKey.NAME), "image", k(ConfigKey.DOCKER_FILE_DIR),  "src/main/docker/", k(ConfigKey.DOCKER_ARCHIVE), "dockerLoad.tar", k(ConfigKey.FROM), "base" };
        ImageConfiguration config = resolveExternalImageConfig(testData);
        config.initAndValidate(ConfigHelper.NameFormatter.IDENTITY, null);
    }

    @Test
    public void testNoCacheDisabled() throws Exception {
        String[] testData = new String[] {k(ConfigKey.NAME), "image", k(ConfigKey.NO_CACHE), "false", k(ConfigKey.FROM), "base" };

        ImageConfiguration config = resolveExternalImageConfig(testData);
        assertEquals(false, config.getBuildConfiguration().noCache());
    }

    @Test
    public void testNoCacheEnabled() throws Exception {
        String[] testData = new String[] {k(ConfigKey.NAME), "image", k(ConfigKey.NO_CACHE), "true", k(ConfigKey.FROM), "base" };

        ImageConfiguration config = resolveExternalImageConfig(testData);
        assertEquals(true, config.getBuildConfiguration().noCache());
    }

    @Test
    public void testCacheFrom() {
        String[] testData = new String[] {k(ConfigKey.NAME), "image", k(ConfigKey.CACHE_FROM), "foo/bar:latest", k(ConfigKey.FROM), "base"};

        ImageConfiguration config = resolveExternalImageConfig(testData);
        assertEquals(Collections.singletonList("foo/bar:latest"), config.getBuildConfiguration().getCacheFrom());
    }

    @Test
    public void testNoOptimise() throws Exception {
        String[] testData = new String[] {k(ConfigKey.NAME), "image", k(ConfigKey.OPTIMISE), "false", k(ConfigKey.FROM), "base" };

        ImageConfiguration config = resolveExternalImageConfig(testData);
        assertEquals(false, config.getBuildConfiguration().optimise());
    }

    @Test
    public void testDockerFile() {
        String[] testData = new String[] {k(ConfigKey.NAME), "image", k(ConfigKey.DOCKER_FILE), "file" };

        ImageConfiguration config = resolveExternalImageConfig(testData);
        assertNotNull(config.getBuildConfiguration());
    }

    @Test
    public void testDockerFileDir() {
        String[] testData = new String[] {k(ConfigKey.NAME), "image", k(ConfigKey.DOCKER_FILE_DIR), "dir" };

        ImageConfiguration config = resolveExternalImageConfig(testData);
        assertNotNull(config.getBuildConfiguration());
    }

    @Test
    public void testContextDir() {
        String[] testData = new String[] {k(ConfigKey.NAME), "image", k(ConfigKey.CONTEXT_DIR), "dir" };

        ImageConfiguration config = resolveExternalImageConfig(testData);
        assertNotNull(config.getBuildConfiguration());
    }

    @Test
    public void testFilterDefault() {
        String[] testData = new String[] {k(ConfigKey.NAME), "image", k(ConfigKey.FROM), "base" };

        ImageConfiguration config = resolveExternalImageConfig(testData);
        assertEquals(DEFAULT_FILTER, config.getBuildConfiguration().getFilter());
    }

    @Test
    public void testFilter() {
        String filter = "@";
        String[] testData = new String[] {k(ConfigKey.NAME), "image", k(ConfigKey.FROM), "base", k(ConfigKey.FILTER), filter };

        ImageConfiguration config = resolveExternalImageConfig(testData);
        assertEquals(filter, config.getBuildConfiguration().getFilter());
    }

    @Test
    public void testCleanupDefault() {
        String[] testData = new String[] {k(ConfigKey.NAME), "image", k(ConfigKey.FROM), "base" };

        ImageConfiguration config = resolveExternalImageConfig(testData);
        assertEquals(DEFAULT_CLEANUP, config.getBuildConfiguration().cleanupMode().toParameter());
    }

    @Test
    public void testCleanup() {
        CleanupMode mode = CleanupMode.REMOVE;
        String[] testData = new String[] {k(ConfigKey.NAME), "image", k(ConfigKey.FROM), "base", k(ConfigKey.CLEANUP), mode.toParameter() };

        ImageConfiguration config = resolveExternalImageConfig(testData);
        assertEquals(mode, config.getBuildConfiguration().cleanupMode());
    }


    @Test
    public void testUlimit() {
        imageConfiguration = new ImageConfiguration.Builder()
                .externalConfig(new HashMap<String, String>())
                .runConfig(new RunImageConfiguration.Builder()
                        .ulimits(Arrays.asList(
                                new UlimitConfig("memlock", 100, 50),
                                new UlimitConfig("nfile", 1024, 512)
                        ))
                        .build()
                )
                .build();

        makeExternalConfigUse(PropertyMode.Override);

        // TODO: Does Replace make sense here or should we Merge?
        // If merge, it should probably have some more smarts on the ulimit name?
        List<ImageConfiguration> configs = resolveImage(
                imageConfiguration,props(
                        k(ConfigKey.NAME), "image",
                        k(ConfigKey.FROM), "base",
                        k(ConfigKey.ULIMITS)+".1", "memlock=10:10",
                        k(ConfigKey.ULIMITS)+".2", "memlock=:-1",
                        k(ConfigKey.ULIMITS)+".3", "memlock=1024:",
                        k(ConfigKey.ULIMITS)+".4", "memlock=2048"
                ));

        assertEquals(1,configs.size());
        RunImageConfiguration runConfig = configs.get(0).getRunConfiguration();
        List<UlimitConfig> ulimits = runConfig.getUlimits();

        assertEquals(4, ulimits.size());
        assertUlimitEquals(ulimit("memlock",10,10),runConfig.getUlimits().get(0));
        assertUlimitEquals(ulimit("memlock",null,-1),runConfig.getUlimits().get(1));
        assertUlimitEquals(ulimit("memlock",1024,null),runConfig.getUlimits().get(2));
        assertUlimitEquals(ulimit("memlock",2048,null),runConfig.getUlimits().get(3));
    }

    @Test
    public void testNoAssembly() throws Exception {
        Properties props = props(k(ConfigKey.NAME), "image");
        //List<ImageConfiguration> configs = configHandler.resolve(imageConfiguration, props);
        //assertEquals(1, configs.size());

        //AssemblyConfiguration config = configs.get(0).getBuildConfiguration().getAssemblyConfiguration();
        //assertNull(config);
    }

    @Test
    public void testResolve() {
        ImageConfiguration resolved = resolveExternalImageConfig(getTestData());

        validateBuildConfiguration(resolved.getBuildConfiguration());
        validateRunConfiguration(resolved.getRunConfiguration());
        //validateWaitConfiguraion(resolved.getRunConfiguration().getWaitConfiguration());
    }

    @Override
    protected String getEnvPropertyFile() {
        return "/tmp/envProps.txt";
    }

    @Override
    protected RunImageConfiguration.NamingStrategy getRunNamingStrategy() {
        return RunImageConfiguration.NamingStrategy.none;
    }

    @Override
    protected void validateEnv(Map<String, String> env) {
        assertTrue(env.containsKey("HOME"));
        assertEquals("/Users/roland", env.get("HOME"));
    }

    private ImageConfiguration buildAnUnresolvedImage() {
        return new ImageConfiguration.Builder()
                .externalConfig(new HashMap<String, String>())
                .build();
    }

    private Map<String, String> externalConfigMode(PropertyMode mode) {
        Map<String, String> externalConfig = new HashMap<>();
        if(mode != null) {
            externalConfig.put("type", "properties");
            externalConfig.put("mode", mode.name());
        }
        return externalConfig;
    }

    private void makeExternalConfigUse(PropertyMode mode) {
        Map<String, String> externalConfig = imageConfiguration.getExternalConfig();
        externalConfig.put("type", "properties");
        if(mode != null) {
            externalConfig.put("mode", mode.name());
        } else {
            externalConfig.remove("mode");
        }
    }

    private List<ImageConfiguration> resolveImage(ImageConfiguration image, final Properties properties) {
        //MavenProject project = mock(MavenProject.class);
        //when(project.getProperties()).thenReturn(properties);
        new Expectations() {{
            project.getProperties(); result = properties;
            project.getBasedir(); minTimes = 0; maxTimes = 1; result = new File("./");
        }};

        return configHandler.resolve(image, project, null);
    }

    private ImageConfiguration resolveExternalImageConfig(String[] testData) {
        Map<String, String> external = new HashMap<>();
        external.put("type", "props");

        ImageConfiguration config = new ImageConfiguration.Builder().name("image").alias("alias").externalConfig(external).build();

        List<ImageConfiguration> resolvedImageConfigs = resolveImage(config, props(testData));
        assertEquals(1, resolvedImageConfigs.size());

        return resolvedImageConfigs.get(0);
    }

    private void validateBuildConfiguration(BuildImageConfiguration buildConfig) {
        assertEquals(CleanupMode.TRY_TO_REMOVE, buildConfig.cleanupMode());
        assertEquals("command.sh", buildConfig.getCmd().getShell());
        assertEquals("image", buildConfig.getFrom());
        assertEquals("image-ext", buildConfig.getFromExt().get("name"));
        assertEquals(a("8080"), buildConfig.getPorts());
        assertEquals("registry", buildConfig.getRegistry());
        assertEquals(a("/foo"), buildConfig.getVolumes());
        assertEquals("fabric8io@redhat.com",buildConfig.getMaintainer());
        assertEquals(false, buildConfig.noCache());
        assertEquals("Always", buildConfig.getImagePullPolicy());

        validateEnv(buildConfig.getEnv());
        validateLabels(buildConfig.getLabels());
        validateArgs(buildConfig.getArgs());
        validateBuildOptions(buildConfig.getBuildOptions());
        /*
         * validate only the descriptor is required and defaults are all used, 'testAssembly' validates
         * all options can be set
         */
        AssemblyConfiguration assemblyConfig = buildConfig.getAssemblyConfiguration();

        assertEquals("/maven", assemblyConfig.getTargetDir());
        assertEquals("assembly.xml", assemblyConfig.getDescriptor());
        assertNull(assemblyConfig.getUser());
        assertNull(assemblyConfig.exportTargetDir());
        assertFalse(assemblyConfig.isIgnorePermissions());
    }

    private void validateArgs(Map<String, String> args) {
        assertEquals("http://proxy",args.get("PROXY"));
    }

    private void validateLabels(Map<String, String> labels) {
        assertEquals("Hello\"World",labels.get("com.acme.label"));
    }

    private void validateBuildOptions(Map<String,String> buildOptions) {
        assertEquals("2147483648", buildOptions.get("shmsize"));
    }

    protected void validateRunConfiguration(RunImageConfiguration runConfig) {
        assertEquals(a("/foo", "/tmp:/tmp"), runConfig.getVolumeConfiguration().getBind());
        assertEquals(a("CAP"), runConfig.getCapAdd());
        assertEquals(a("CAP"), runConfig.getCapDrop());
        assertEquals(a("seccomp=unconfined"), runConfig.getSecurityOpts());
        assertEquals("command.sh", runConfig.getCmd().getShell());
        assertEquals(a("8.8.8.8"), runConfig.getDns());
        assertEquals("host",runConfig.getNetworkingConfig().getStandardMode(null));
        assertEquals(a("example.com"), runConfig.getDnsSearch());
        assertEquals("domain.com", runConfig.getDomainname());
        assertEquals("entrypoint.sh", runConfig.getEntrypoint().getShell());
        assertEquals(a("localhost:127.0.0.1"), runConfig.getExtraHosts());
        assertEquals("subdomain", runConfig.getHostname());
        assertEquals(a("redis"), runConfig.getLinks());
        assertEquals((Long) 1L, runConfig.getMemory());
        assertEquals((Long) 1L, runConfig.getMemorySwap());
        assertEquals((Long) 1000000000L, runConfig.getCpus());
        assertEquals((Long) 1L, runConfig.getCpuShares());
        assertEquals("0,1", runConfig.getCpuSet());
        assertEquals("/tmp/envProps.txt",runConfig.getEnvPropertyFile());
        assertEquals("/tmp/props.txt", runConfig.getPortPropertyFile());
        assertEquals(a("8081:8080"), runConfig.getPorts());
        assertEquals(true, runConfig.getPrivileged());
        assertEquals("tomcat", runConfig.getUser());
        assertEquals(a("from"), runConfig.getVolumeConfiguration().getFrom());
        assertEquals("foo", runConfig.getWorkingDir());
        assertNotNull( runConfig.getUlimits());
        assertEquals(4, runConfig.getUlimits().size());
        assertUlimitEquals(ulimit("memlock",10,10),runConfig.getUlimits().get(0));
        assertUlimitEquals(ulimit("memlock",null,-1),runConfig.getUlimits().get(1));
        assertUlimitEquals(ulimit("memlock",1024,null),runConfig.getUlimits().get(2));
        assertUlimitEquals(ulimit("memlock",2048,null),runConfig.getUlimits().get(3));
        assertEquals("/var/lib/mysql:10m", runConfig.getTmpfs().get(0));
        assertEquals(1, runConfig.getTmpfs().size());
        assertEquals("Never", runConfig.getImagePullPolicy());
        assertEquals(true, runConfig.getReadOnly());
        assertEquals(true, runConfig.getAutoRemove());

        validateEnv(runConfig.getEnv());

        // not sure it's worth it to implement 'equals/hashcode' for these
        RestartPolicy policy = runConfig.getRestartPolicy();
        assertEquals("on-failure", policy.getName());
        assertEquals(1, policy.getRetry());

        WaitConfiguration wait = runConfig.getWaitConfiguration();
        assertEquals("http://foo.com", wait.getUrl());
        assertEquals("pattern", wait.getLog());
        assertEquals("post_start_command", wait.getExec().getPostStart());
        assertEquals("pre_stop_command", wait.getExec().getPreStop());
        assertTrue(wait.getExec().isBreakOnError());
        assertEquals(5, wait.getTime().intValue());
        assertTrue(wait.getHealthy());
        assertEquals(0, wait.getExit().intValue());

        LogConfiguration config = runConfig.getLogConfiguration();
        assertEquals("green", config.getColor());
        assertTrue(config.isEnabled());
        assertEquals("SRV", config.getPrefix());
        assertEquals("iso8601", config.getDate());
        assertEquals("json",config.getDriver().getName());
        assertEquals(2, config.getDriver().getOpts().size());
        assertEquals("1024", config.getDriver().getOpts().get("max-size"));
        assertEquals("10", config.getDriver().getOpts().get("max-file"));
    }

    private UlimitConfig ulimit(String name, Integer hard, Integer soft) {
        return new UlimitConfig(name, hard, soft);
    }

    private Properties props(String ... args) {
        Properties ret = new Properties();
        for (int i = 0; i < args.length; i += 2) {
            ret.setProperty(args[i], args[i + 1]);
        }
        return ret;
    }

    private String[] getTestAssemblyData() {
        return new String[] {
            k(ConfigKey.FROM), "busybox",
            k(ConfigKey.ASSEMBLY_BASEDIR), "/basedir",
            k(ConfigKey.ASSEMBLY_DESCRIPTOR_REF), "project",
            k(ConfigKey.ASSEMBLY_EXPORT_BASEDIR), "false",
            k(ConfigKey.ASSEMBLY_IGNORE_PERMISSIONS), "true",
            k(ConfigKey.ASSEMBLY_USER), "user",
            k(ConfigKey.NAME), "image",
            };
    }

    private String[] getTestData() {
        return new String[] {
            k(ConfigKey.ALIAS), "alias",
            k(ConfigKey.ASSEMBLY_DESCRIPTOR), "assembly.xml",
            k(ConfigKey.BIND) + ".1", "/foo",
            k(ConfigKey.BIND) + ".2", "/tmp:/tmp",
            k(ConfigKey.CAP_ADD) + ".1", "CAP",
            k(ConfigKey.CAP_DROP) + ".1", "CAP",
            k(ConfigKey.SECURITY_OPTS) + ".1", "seccomp=unconfined",
            k(ConfigKey.CPUS), "1000000000",
            k(ConfigKey.CPUSET), "0,1",
            k(ConfigKey.CPUSHARES), "1",
            k(ConfigKey.CMD), "command.sh",
            k(ConfigKey.DNS) + ".1", "8.8.8.8",
            k(ConfigKey.NET), "host",
            k(ConfigKey.DNS_SEARCH) + ".1", "example.com",
            k(ConfigKey.DOMAINNAME), "domain.com",
            k(ConfigKey.ENTRYPOINT), "entrypoint.sh",
            k(ConfigKey.ENV) + ".HOME", "/Users/roland",
            k(ConfigKey.ARGS) + ".PROXY", "http://proxy",
            k(ConfigKey.LABELS) + ".com.acme.label", "Hello\"World",
            k(ConfigKey.BUILD_OPTIONS) + ".shmsize", "2147483648",
            k(ConfigKey.ENV_PROPERTY_FILE), "/tmp/envProps.txt",
            k(ConfigKey.EXTRA_HOSTS) + ".1", "localhost:127.0.0.1",
            k(ConfigKey.FROM), "image",
            k(ConfigKey.FROM_EXT) + ".name", "image-ext",
            k(ConfigKey.FROM_EXT) + ".kind", "kind",
            k(ConfigKey.HOSTNAME), "subdomain",
            k(ConfigKey.LINKS) + ".1", "redis",
            k(ConfigKey.MAINTAINER), "fabric8io@redhat.com",
            k(ConfigKey.MEMORY), "1",
            k(ConfigKey.MEMORY_SWAP), "1",
            k(ConfigKey.NAME), "image",
            k(ConfigKey.PORT_PROPERTY_FILE), "/tmp/props.txt",
            k(ConfigKey.PORTS) + ".1", "8081:8080",
            k(ConfigKey.PRIVILEGED), "true",
            k(ConfigKey.REGISTRY), "registry",
            k(ConfigKey.RESTART_POLICY_NAME), "on-failure",
            k(ConfigKey.RESTART_POLICY_RETRY), "1",
            k(ConfigKey.USER), "tomcat",
            k(ConfigKey.ULIMITS)+".1", "memlock=10:10",
            k(ConfigKey.ULIMITS)+".2", "memlock=:-1",
            k(ConfigKey.ULIMITS)+".3", "memlock=1024:",
            k(ConfigKey.ULIMITS)+".4", "memlock=2048",
            k(ConfigKey.VOLUMES) + ".1", "/foo",
            k(ConfigKey.VOLUMES_FROM) + ".1", "from",
            k(ConfigKey.WAIT_EXEC_PRE_STOP), "pre_stop_command",
            k(ConfigKey.WAIT_EXEC_POST_START), "post_start_command",
            k(ConfigKey.WAIT_EXEC_BREAK_ON_ERROR), "true",
            k(ConfigKey.WAIT_LOG), "pattern",
            k(ConfigKey.WAIT_HEALTHY), "true",
            k(ConfigKey.WAIT_TIME), "5",
            k(ConfigKey.WAIT_EXIT), "0",
            k(ConfigKey.WAIT_URL), "http://foo.com",
            k(ConfigKey.LOG_PREFIX), "SRV",
            k(ConfigKey.LOG_COLOR), "green",
            k(ConfigKey.LOG_ENABLED), "true",
            k(ConfigKey.LOG_DATE), "iso8601",
            k(ConfigKey.LOG_DRIVER_NAME), "json",
            k(ConfigKey.LOG_DRIVER_OPTS) + ".max-size", "1024",
            k(ConfigKey.LOG_DRIVER_OPTS) + ".max-file", "10",
            k(ConfigKey.WORKING_DIR), "foo",
            k(ConfigKey.TMPFS) + ".1", "/var/lib/mysql:10m",
            k(ConfigKey.IMAGE_PULL_POLICY_BUILD), "Always",
            k(ConfigKey.IMAGE_PULL_POLICY_RUN), "Never",
            k(ConfigKey.READ_ONLY), "true",
            k(ConfigKey.AUTO_REMOVE), "true",
        };
    }

    private String[] getSkipTestData(ConfigKey key, boolean value) {
        return new String[] {k(ConfigKey.NAME), "image", k(key), String.valueOf(value), k(ConfigKey.FROM), "busybox" };
    }

    private String k(ConfigKey from) {
        return from.asPropertyKey();
    }
    private void assertUlimitEquals(UlimitConfig expected, UlimitConfig actual){
    	assertEquals(expected.getName(), actual.getName());
        assertEquals(expected.getSoft(), actual.getSoft());
        assertEquals(expected.getHard(), actual.getHard());
    }
}
