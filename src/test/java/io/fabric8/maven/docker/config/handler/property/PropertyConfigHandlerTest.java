package io.fabric8.maven.docker.config.handler.property;
/*
 *
 * Copyright 2014 Roland Huss
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

import io.fabric8.maven.docker.config.Arguments;
import io.fabric8.maven.docker.config.AssemblyConfiguration;
import io.fabric8.maven.docker.config.BuildImageConfiguration;
import io.fabric8.maven.docker.config.CleanupMode;
import io.fabric8.maven.docker.config.ConfigHelper;
import io.fabric8.maven.docker.config.CopyConfiguration;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.config.LogConfiguration;
import io.fabric8.maven.docker.config.RestartPolicy;
import io.fabric8.maven.docker.config.RunImageConfiguration;
import io.fabric8.maven.docker.config.UlimitConfig;
import io.fabric8.maven.docker.config.WaitConfiguration;
import io.fabric8.maven.docker.config.handler.AbstractConfigHandlerTest;
import io.fabric8.maven.docker.util.Logger;

import org.apache.maven.plugins.assembly.model.Assembly;
import org.apache.maven.plugins.assembly.model.DependencySet;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

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

import static io.fabric8.maven.docker.config.BuildImageConfiguration.DEFAULT_CLEANUP;
import static io.fabric8.maven.docker.config.BuildImageConfiguration.DEFAULT_FILTER;

/**
 * @author roland
 * @since 05/12/14
 */
@ExtendWith(MockitoExtension.class)
class PropertyConfigHandlerTest extends AbstractConfigHandlerTest {

    private PropertyConfigHandler configHandler;
    private ImageConfiguration imageConfiguration;

    @Mock
    private MavenProject project;

    @BeforeEach
    void setUp() {
        configHandler = new PropertyConfigHandler();
        imageConfiguration = buildAnUnresolvedImage();
    }

    @Test
    void testSkipBuild() {
        Assertions.assertFalse(resolveExternalImageConfig(getSkipTestData(ConfigKey.SKIP_BUILD, false)).getBuildConfiguration().skip());
        Assertions.assertTrue(resolveExternalImageConfig(getSkipTestData(ConfigKey.SKIP_BUILD, true)).getBuildConfiguration().skip());

        Assertions.assertFalse(resolveExternalImageConfig(new String[] { k(ConfigKey.NAME), "image", k(ConfigKey.FROM), "busybox" }).getBuildConfiguration().skip());
    }

    @Test
    void testSkipPush() {
        Assertions.assertFalse(resolveExternalImageConfig(getSkipTestData(ConfigKey.SKIP_PUSH, false)).getBuildConfiguration().skipPush());
        Assertions.assertTrue(resolveExternalImageConfig(getSkipTestData(ConfigKey.SKIP_PUSH, true)).getBuildConfiguration().skipPush());

        Assertions.assertFalse(resolveExternalImageConfig(new String[] { k(ConfigKey.NAME), "image", k(ConfigKey.FROM), "busybox" }).getBuildConfiguration().skipPush());
    }

    @Test
    void testSkipRun() {
        Assertions.assertFalse(resolveExternalImageConfig(getSkipTestData(ConfigKey.SKIP_RUN, false)).getRunConfiguration().skip());
        Assertions.assertTrue(resolveExternalImageConfig(getSkipTestData(ConfigKey.SKIP_RUN, true)).getRunConfiguration().skip());

        Assertions.assertFalse(resolveExternalImageConfig(new String[] { k(ConfigKey.NAME), "image" }).getRunConfiguration().skip());
    }

    @Test
    void testType() {
        Assertions.assertNotNull(configHandler.getType());
    }

    @Test
    void testEmpty() {
        Properties properties = props();
        Assertions.assertThrows(IllegalArgumentException.class, () -> resolveImage(imageConfiguration, properties));
    }

    @Test
    void testPorts() {
        List<ImageConfiguration> configs = resolveImage(
            imageConfiguration, props(
                "docker.name", "demo",
                "docker.ports.1", "jolokia.port:8080",
                "docker.ports.2", "9090",
                "docker.ports.3", "0.0.0.0:80:80",
                "docker.from", "busybox"
            ));
        Assertions.assertEquals(1, configs.size());
        RunImageConfiguration runConfig = configs.get(0).getRunConfiguration();
        List<String> portsAsList = runConfig.getPorts();
        String[] ports = new ArrayList<>(portsAsList).toArray(new String[portsAsList.size()]);
        Assertions.assertArrayEquals(new String[] {
            "jolokia.port:8080",
            "9090",
            "0.0.0.0:80:80"
        }, ports);
        BuildImageConfiguration buildConfig = configs.get(0).getBuildConfiguration();
        ports = new ArrayList<>(buildConfig.getPorts()).toArray(new String[buildConfig.getPorts().size()]);
        Assertions.assertArrayEquals(new String[] { "8080", "9090", "80" }, ports);
    }

    @Test
    void testPortsFromConfigAndProperties() {
        imageConfiguration = new ImageConfiguration.Builder()
            .externalConfig(new HashMap<>())
            .buildConfig(new BuildImageConfiguration.Builder()
                .ports(Collections.singletonList("1234"))
                .cacheFrom((Collections.singletonList("foo/bar:latest")))
                .build()
            )
            .runConfig(new RunImageConfiguration.Builder()
                .ports(Collections.singletonList("jolokia.port:1234"))
                .build()
            )
            .build();

        makeExternalConfigUse(PropertyMode.Override);

        List<ImageConfiguration> configs = resolveImage(
            imageConfiguration, props(
                "docker.name", "demo",
                "docker.ports.1", "9090",
                "docker.ports.2", "0.0.0.0:80:80",
                "docker.from", "busybox"
            ));
        Assertions.assertEquals(1, configs.size());
        RunImageConfiguration runConfig = configs.get(0).getRunConfiguration();
        List<String> portsAsList = runConfig.getPorts();
        String[] ports = new ArrayList<>(portsAsList).toArray(new String[portsAsList.size()]);
        Assertions.assertArrayEquals(new String[] {
            "9090",
            "0.0.0.0:80:80",
            "jolokia.port:1234"
        }, ports);
        BuildImageConfiguration buildConfig = configs.get(0).getBuildConfiguration();
        ports = new ArrayList<>(buildConfig.getPorts()).toArray(new String[buildConfig.getPorts().size()]);
        Assertions.assertArrayEquals(new String[] { "9090", "80", "1234" }, ports);
    }

    @Test
    void testInvalidPropertyMode() {
        makeExternalConfigUse(PropertyMode.Override);
        imageConfiguration.getExternalConfig().put("mode", "invalid");

        Properties properties = props();
        Assertions.assertThrows(IllegalArgumentException.class, () -> resolveImage(imageConfiguration, properties));
    }

    @Test
    void testRunCommands() {
        List<ImageConfiguration> configs = resolveImage(
            imageConfiguration, props(
                "docker.from", "base",
                "docker.name", "demo",
                "docker.run.1", "foo",
                "docker.run.2", "bar",
                "docker.run.3", "wibble")
        );

        Assertions.assertEquals(1, configs.size());

        BuildImageConfiguration buildConfig = configs.get(0).getBuildConfiguration();
        String[] runCommands = new ArrayList<>(buildConfig.getRunCmds()).toArray(new String[buildConfig.getRunCmds().size()]);
        Assertions.assertArrayEquals(new String[] { "foo", "bar", "wibble" }, runCommands);
    }

    @Test
    void testShell() {
        List<ImageConfiguration> configs = resolveImage(
            imageConfiguration, props(
                "docker.from", "base",
                "docker.name", "demo",
                "docker.shell", "/bin/sh -c")
        );

        Assertions.assertEquals(1, configs.size());

        BuildImageConfiguration buildConfig = configs.get(0).getBuildConfiguration();
        String[] shell = new ArrayList<>(buildConfig.getShell().asStrings()).toArray(new String[buildConfig.getShell().asStrings().size()]);
        Assertions.assertArrayEquals(new String[] { "/bin/sh", "-c" }, shell);
    }

    @Test
    void testRunCommandsFromPropertiesAndConfig() {
        imageConfiguration = new ImageConfiguration.Builder()
            .externalConfig(new HashMap<>())
            .buildConfig(new BuildImageConfiguration.Builder()
                .runCmds(Arrays.asList("some", "ignored", "value"))
                .cacheFrom((Collections.singletonList("foo/bar:latest")))
                .build()
            )
            .build();

        makeExternalConfigUse(PropertyMode.Override);

        List<ImageConfiguration> configs = resolveImage(
            imageConfiguration, props(
                "docker.from", "base",
                "docker.name", "demo",
                "docker.run.1", "propconf",
                "docker.run.2", "withrun",
                "docker.run.3", "used")
        );

        Assertions.assertEquals(1, configs.size());

        BuildImageConfiguration buildConfig = configs.get(0).getBuildConfiguration();
        String[] runCommands = new ArrayList<>(buildConfig.getRunCmds()).toArray(new String[buildConfig.getRunCmds().size()]);
        Assertions.assertArrayEquals(new String[] { "propconf", "withrun", "used" }, runCommands);
    }

    @Test
    void testShellFromPropertiesAndConfig() {
        imageConfiguration = new ImageConfiguration.Builder()
            .externalConfig(new HashMap<>())
            .buildConfig(new BuildImageConfiguration.Builder()
                .shell(new Arguments(Arrays.asList("some", "ignored", "value")))
                .cacheFrom((Collections.singletonList("foo/bar:latest")))
                .build()
            )
            .build();

        makeExternalConfigUse(PropertyMode.Override);

        List<ImageConfiguration> configs = resolveImage(
            imageConfiguration, props(
                "docker.from", "base",
                "docker.name", "demo",
                "docker.shell", "propconf withrun used")
        );

        Assertions.assertEquals(1, configs.size());

        BuildImageConfiguration buildConfig = configs.get(0).getBuildConfiguration();
        String[] shell = new ArrayList<>(buildConfig.getShell().asStrings()).toArray(new String[buildConfig.getShell().asStrings().size()]);
        Assertions.assertArrayEquals(new String[] { "propconf", "withrun", "used" }, shell);
    }

    @Test
    void testRunCommandsFromConfigAndProperties() {
        imageConfiguration = new ImageConfiguration.Builder()
            .externalConfig(externalConfigMode(PropertyMode.Fallback))
            .buildConfig(new BuildImageConfiguration.Builder()
                .runCmds(Arrays.asList("some", "configured", "value"))
                .cacheFrom((Collections.singletonList("foo/bar:latest")))
                .build()
            )
            .build();

        List<ImageConfiguration> configs = resolveImage(
            imageConfiguration, props(
                "docker.from", "base",
                "docker.name", "demo",
                "docker.run.1", "this",
                "docker.run.2", "is",
                "docker.run.3", "ignored")
        );

        Assertions.assertEquals(1, configs.size());

        BuildImageConfiguration buildConfig = configs.get(0).getBuildConfiguration();
        String[] runCommands = new ArrayList<>(buildConfig.getRunCmds()).toArray(new String[buildConfig.getRunCmds().size()]);
        Assertions.assertArrayEquals(new String[] { "some", "configured", "value" }, runCommands);
    }

    @Test
    void testEntrypoint() {
        List<ImageConfiguration> configs = resolveImage(
            imageConfiguration, props(
                "docker.from", "base",
                "docker.name", "demo",
                "docker.entrypoint", "/entrypoint.sh --from-property")
        );

        Assertions.assertEquals(1, configs.size());

        BuildImageConfiguration buildConfig = configs.get(0).getBuildConfiguration();
        Assertions.assertArrayEquals(new String[] { "/entrypoint.sh", "--from-property" }, buildConfig.getEntryPoint().asStrings().toArray());
    }

    @Test
    void testEntrypointExecFromConfig() {
        imageConfiguration = new ImageConfiguration.Builder()
            .externalConfig(externalConfigMode(PropertyMode.Fallback))
            .buildConfig(new BuildImageConfiguration.Builder()
                .entryPoint(new Arguments(Arrays.asList("/entrypoint.sh", "--from-property")))
                .cacheFrom((Collections.singletonList("foo/bar:latest")))
                .build()
            )
            .build();

        List<ImageConfiguration> configs = resolveImage(
            imageConfiguration, props(
                "docker.from", "base",
                "docker.name", "demo")
        );

        Assertions.assertEquals(1, configs.size());

        BuildImageConfiguration buildConfig = configs.get(0).getBuildConfiguration();
        Assertions.assertArrayEquals(new String[] { "/entrypoint.sh", "--from-property" }, buildConfig.getEntryPoint().asStrings().toArray());
    }

    @Test
    void testDefaultLogEnabledConfiguration() {
        imageConfiguration = new ImageConfiguration.Builder()
            .externalConfig(externalConfigMode(PropertyMode.Override))
            .buildConfig(new BuildImageConfiguration.Builder()
                .cacheFrom((Collections.singletonList("foo/bar:latest")))
                .build()
            )
            .build();

        List<ImageConfiguration> configs = resolveImage(
            imageConfiguration, props(
                "docker.from", "base",
                "docker.name", "demo")
        );

        Assertions.assertEquals(1, configs.size());

        RunImageConfiguration runConfiguration = configs.get(0).getRunConfiguration();
        Assertions.assertNull(runConfiguration.getLogConfiguration().isEnabled());
        Assertions.assertFalse(runConfiguration.getLogConfiguration().isActivated());

        // If any log property is set, enabled shall be true by default
        configs = resolveImage(
            imageConfiguration, props(
                "docker.from", "base",
                "docker.name", "demo",
                "docker.log.color", "green")
        );

        runConfiguration = getRunImageConfiguration(configs);
        Assertions.assertNull(runConfiguration.getLogConfiguration().isEnabled());
        Assertions.assertTrue(runConfiguration.getLogConfiguration().isActivated());
        Assertions.assertEquals("green", runConfiguration.getLogConfiguration().getColor());

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
        Assertions.assertNull(runConfiguration.getLogConfiguration().isEnabled());
        Assertions.assertTrue(runConfiguration.getLogConfiguration().isActivated());
        Assertions.assertEquals("red", runConfiguration.getLogConfiguration().getColor());

        // and if set by property, still enabled but overrides
        configs = resolveImage(
            imageConfiguration, props(
                "docker.from", "base",
                "docker.name", "demo",
                "docker.log.color", "yellow")
        );

        runConfiguration = getRunImageConfiguration(configs);
        Assertions.assertNull(runConfiguration.getLogConfiguration().isEnabled());
        Assertions.assertTrue(runConfiguration.getLogConfiguration().isActivated());
        Assertions.assertEquals("yellow", runConfiguration.getLogConfiguration().getColor());

        // Fallback works as well
        makeExternalConfigUse(PropertyMode.Fallback);
        configs = resolveImage(
            imageConfiguration, props(
                "docker.from", "base",
                "docker.name", "demo",
                "docker.log.color", "yellow")
        );

        runConfiguration = getRunImageConfiguration(configs);
        Assertions.assertNull(runConfiguration.getLogConfiguration().isEnabled());
        Assertions.assertTrue(runConfiguration.getLogConfiguration().isActivated());
        Assertions.assertEquals("red", runConfiguration.getLogConfiguration().getColor());
    }

    @Test
    void testExplicitLogEnabledConfiguration() {
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
        Assertions.assertTrue(runConfiguration.getLogConfiguration().isEnabled());
        Assertions.assertTrue(runConfiguration.getLogConfiguration().isActivated());
        Assertions.assertEquals("red", runConfiguration.getLogConfiguration().getColor());

        // Explicitly disabled
        makeExternalConfigUse(PropertyMode.Override);
        configs = resolveImage(
            imageConfiguration, props(
                "docker.from", "base",
                "docker.name", "demo",
                "docker.log.color", "yellow",
                "docker.log.enabled", "false")
        );

        runConfiguration = getRunImageConfiguration(configs);
        Assertions.assertFalse(runConfiguration.getLogConfiguration().isEnabled());
        Assertions.assertFalse(runConfiguration.getLogConfiguration().isActivated());
        Assertions.assertEquals("yellow", runConfiguration.getLogConfiguration().getColor());

        // Disabled by config
        imageConfiguration = new ImageConfiguration.Builder()
            .externalConfig(externalConfigMode(PropertyMode.Fallback))
            .runConfig(new RunImageConfiguration.Builder()
                .log(new LogConfiguration.Builder().enabled(false).color("red").build())
                .build()
            )
            .build();

        configs = resolveImage(
            imageConfiguration, props(
                "docker.from", "base",
                "docker.name", "demo")
        );

        runConfiguration = getRunImageConfiguration(configs);
        Assertions.assertFalse(runConfiguration.getLogConfiguration().isEnabled());
        Assertions.assertFalse(runConfiguration.getLogConfiguration().isActivated());
        Assertions.assertEquals("red", runConfiguration.getLogConfiguration().getColor());

        // Enabled by property, with override
        makeExternalConfigUse(PropertyMode.Override);
        configs = resolveImage(
            imageConfiguration, props(
                "docker.from", "base",
                "docker.name", "demo",
                "docker.log.enabled", "true")
        );

        runConfiguration = getRunImageConfiguration(configs);
        Assertions.assertTrue(runConfiguration.getLogConfiguration().isEnabled());
        Assertions.assertTrue(runConfiguration.getLogConfiguration().isActivated());
        Assertions.assertEquals("red", runConfiguration.getLogConfiguration().getColor());

        // Disabled with property too
        configs = resolveImage(
            imageConfiguration, props(
                "docker.from", "base",
                "docker.name", "demo",
                "docker.log.enabled", "false")
        );

        runConfiguration = getRunImageConfiguration(configs);
        Assertions.assertFalse(runConfiguration.getLogConfiguration().isEnabled());
        Assertions.assertFalse(runConfiguration.getLogConfiguration().isActivated());
        Assertions.assertEquals("red", runConfiguration.getLogConfiguration().getColor());
    }

    @Test
    void testLogFile() {
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

        Assertions.assertEquals(1, configs.size());

        RunImageConfiguration runConfiguration = configs.get(0).getRunConfiguration();
        Assertions.assertNull(runConfiguration.getLogConfiguration().isEnabled());
        Assertions.assertTrue(runConfiguration.getLogConfiguration().isActivated());
        Assertions.assertEquals("myfile", runConfiguration.getLogConfiguration().getFileLocation());

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

        Assertions.assertEquals(1, configs.size());

        runConfiguration = configs.get(0).getRunConfiguration();
        Assertions.assertNull(runConfiguration.getLogConfiguration().isEnabled());
        Assertions.assertTrue(runConfiguration.getLogConfiguration().isActivated());
        Assertions.assertEquals("myfilefromprop", runConfiguration.getLogConfiguration().getFileLocation());
    }

    private RunImageConfiguration getRunImageConfiguration(List<ImageConfiguration> configs) {
        Assertions.assertEquals(1, configs.size());
        return configs.get(0).getRunConfiguration();
    }

    @Test
    void testBuildFromDockerFileMerged() {
        imageConfiguration = new ImageConfiguration.Builder()
            .name("myimage")
            .externalConfig(externalConfigMode(PropertyMode.Override))
            .buildConfig(new BuildImageConfiguration.Builder()
                .dockerFile("/some/path")
                .cacheFrom((Collections.singletonList("foo/bar:latest")))
                .build()
            )
            .build();

        List<ImageConfiguration> configs = resolveImage(
            imageConfiguration, props()
        );

        Assertions.assertEquals(1, configs.size());

        BuildImageConfiguration buildConfiguration = configs.get(0).getBuildConfiguration();
        Assertions.assertNotNull(buildConfiguration);
        buildConfiguration.initAndValidate(null);

        Path absolutePath = Paths.get(".").toAbsolutePath();
        String expectedPath = absolutePath.getRoot() + "some" + File.separator + "path";
        Assertions.assertEquals(expectedPath, buildConfiguration.getDockerFile().getAbsolutePath());
    }

    @Test
    void testEnvAndLabels() {
        List<ImageConfiguration> configs = resolveImage(
            imageConfiguration, props(
                "docker.from", "baase",
                "docker.name", "demo",
                "docker.env.HOME", "/tmp",
                "docker.env.root.dir", "/bla",
                "docker.labels.version", "1.0.0",
                "docker.labels.blub.bla.foobar", "yep"
            ));

        Assertions.assertEquals(1, configs.size());
        ImageConfiguration calcConfig = configs.get(0);
        for (Map<String,String> env : new Map[] { calcConfig.getBuildConfiguration().getEnv(),
            calcConfig.getRunConfiguration().getEnv() }) {
            Assertions.assertEquals(2, env.size());
            Assertions.assertEquals("/tmp", env.get("HOME"));
            Assertions.assertEquals("/bla", env.get("root.dir"));
        }
        for (Map<String,String> labels : new Map[] { calcConfig.getBuildConfiguration().getLabels(),
            calcConfig.getRunConfiguration().getLabels() }) {
            Assertions.assertEquals(2, labels.size());
            Assertions.assertEquals("1.0.0", labels.get("version"));
            Assertions.assertEquals("yep", labels.get("blub.bla.foobar"));
        }
    }

    @Test
    void testSpecificEnv() {
        List<ImageConfiguration> configs = resolveImage(
            imageConfiguration, props(
                "docker.from", "baase",
                "docker.name", "demo",
                "docker.envBuild.HOME", "/tmp",
                "docker.envRun.root.dir", "/bla"
            ));

        Assertions.assertEquals(1, configs.size());
        ImageConfiguration calcConfig = configs.get(0);

        Map<String, String> env = calcConfig.getBuildConfiguration().getEnv();
        Assertions.assertEquals(1, env.size());
        Assertions.assertEquals("/tmp", env.get("HOME"));

        env = calcConfig.getRunConfiguration().getEnv();
        Assertions.assertEquals(1, env.size());
        Assertions.assertEquals("/bla", env.get("root.dir"));
    }

    @Test
    void testMergedEnv() {
        List<ImageConfiguration> configs = resolveImage(
            imageConfiguration, props(
                "docker.from", "baase",
                "docker.name", "demo",
                "docker.env.HOME", "/tmp",
                "docker.envBuild.HOME", "/var/tmp",
                "docker.envRun.root.dir", "/bla"
            ));

        Assertions.assertEquals(1, configs.size());
        ImageConfiguration calcConfig = configs.get(0);

        Map<String, String> env = calcConfig.getBuildConfiguration().getEnv();
        Assertions.assertEquals(1, env.size());
        Assertions.assertEquals("/var/tmp", env.get("HOME"));

        env = calcConfig.getRunConfiguration().getEnv();
        Assertions.assertEquals(2, env.size());
        Assertions.assertEquals("/tmp", env.get("HOME"));
        Assertions.assertEquals("/bla", env.get("root.dir"));
    }

    @Test
    void testAssembly() {
        List<ImageConfiguration> configs = resolveImage(imageConfiguration, props(getTestAssemblyData()));
        Assertions.assertEquals(1, configs.size());
        configs.get(0).initAndValidate(ConfigHelper.NameFormatter.IDENTITY, null);

        List<AssemblyConfiguration> assemblies = configs.get(0).getBuildConfiguration().getAllAssemblyConfigurations();
        Assertions.assertEquals(1, assemblies.size());

        AssemblyConfiguration config = assemblies.get(0);
        Assertions.assertEquals("user", config.getUser());
        Assertions.assertEquals("project", config.getDescriptorRef());
        Assertions.assertFalse(config.exportTargetDir());
        Assertions.assertTrue(config.isIgnorePermissions());
    }

    @Test
    void testMultipleAssemblies() {
        List<ImageConfiguration> configs = resolveImage(imageConfiguration, props(getTestMultipleAssemblyData()));
        Assertions.assertEquals(1, configs.size());
        configs.get(0).initAndValidate(ConfigHelper.NameFormatter.IDENTITY, null);

        List<AssemblyConfiguration> assemblies = configs.get(0).getBuildConfiguration().getAllAssemblyConfigurations();
        Assertions.assertEquals(2, assemblies.size());

        AssemblyConfiguration config = assemblies.get(0);
        Assertions.assertEquals("user", config.getUser());
        Assertions.assertEquals("project", config.getDescriptorRef());
        Assertions.assertFalse(config.exportTargetDir());
        Assertions.assertTrue(config.isIgnorePermissions());

        config = assemblies.get(1);
        Assertions.assertEquals("user", config.getUser());
        Assertions.assertEquals("artifact", config.getDescriptorRef());
        Assertions.assertEquals("art", config.getName());
        Assertions.assertFalse(config.exportTargetDir());
        Assertions.assertTrue(config.isIgnorePermissions());
    }

    @Test
    void testAssemblyInline() {
        Assembly assembly = new Assembly();
        assembly.addDependencySet(new DependencySet());

        imageConfiguration = new ImageConfiguration.Builder()
            .registry("docker.io")
            .name("test")
            .buildConfig(new BuildImageConfiguration.Builder()
                .assembly(new AssemblyConfiguration.Builder().assemblyDef(assembly).build())
                .build())
            .externalConfig(externalConfigMode(PropertyMode.Only))
            .build();

        List<ImageConfiguration> configs = resolveImage(imageConfiguration, props(getTestAssemblyData()));
        Assertions.assertEquals(1, configs.size());
        configs.get(0).initAndValidate(ConfigHelper.NameFormatter.IDENTITY, null);

        List<AssemblyConfiguration> assemblyConfigurations = configs.get(0).getBuildConfiguration().getAllAssemblyConfigurations();
        Assertions.assertEquals(1, assemblyConfigurations.size());
        AssemblyConfiguration assemblyConfiguration = assemblyConfigurations.get(0);
        Assertions.assertNotNull(assemblyConfiguration.getInline());
        Assertions.assertEquals(1, assemblyConfiguration.getInline().getDependencySets().size());
    }

    @Test
    void testNoCleanup() {
        String[] testData = new String[] { k(ConfigKey.NAME), "image", k(ConfigKey.CLEANUP), "none", k(ConfigKey.FROM), "base" };

        ImageConfiguration config = resolveExternalImageConfig(testData);
        Assertions.assertEquals(CleanupMode.NONE, config.getBuildConfiguration().cleanupMode());
    }

    @Test
    void testNoBuildConfig() {
        String[] testData = new String[] { k(ConfigKey.NAME), "image" };

        ImageConfiguration config = resolveExternalImageConfig(testData);
        Assertions.assertNull(config.getBuildConfiguration());
    }

    @Test
    void testDockerfile() {
        String[] testData = new String[] { k(ConfigKey.NAME), "image", k(ConfigKey.DOCKER_FILE_DIR), "src/main/docker/", k(ConfigKey.FROM), "busybox" };
        ImageConfiguration config = resolveExternalImageConfig(testData);
        config.initAndValidate(ConfigHelper.NameFormatter.IDENTITY, null);
        Assertions.assertTrue(config.getBuildConfiguration().isDockerFileMode());
        Assertions.assertEquals(new File("src/main/docker/Dockerfile"), config.getBuildConfiguration().getDockerFile());
    }

    @Test
    void testDockerArchive() {
        String[] testData = new String[] { k(ConfigKey.NAME), "image", k(ConfigKey.DOCKER_ARCHIVE), "dockerLoad.tar", k(ConfigKey.FROM), "busybox" };
        ImageConfiguration config = resolveExternalImageConfig(testData);
        config.initAndValidate(ConfigHelper.NameFormatter.IDENTITY, null);
        Assertions.assertFalse(config.getBuildConfiguration().isDockerFileMode());
        Assertions.assertEquals(new File("dockerLoad.tar"), config.getBuildConfiguration().getDockerArchive());
    }

    @Test
    void testInvalidDockerFileArchiveConfig() {
        String[] testData = new String[] { k(ConfigKey.NAME), "image", k(ConfigKey.DOCKER_FILE_DIR), "src/main/docker/", k(ConfigKey.DOCKER_ARCHIVE), "dockerLoad.tar",
            k(ConfigKey.FROM), "base" };
        ImageConfiguration config = resolveExternalImageConfig(testData);

        Assertions.assertThrows(IllegalArgumentException.class, () -> config.initAndValidate(ConfigHelper.NameFormatter.IDENTITY, null));
    }

    @Test
    void testNoCacheDisabled() {
        String[] testData = new String[] { k(ConfigKey.NAME), "image", k(ConfigKey.NO_CACHE), "false", k(ConfigKey.FROM), "base" };

        ImageConfiguration config = resolveExternalImageConfig(testData);
        Assertions.assertFalse(config.getBuildConfiguration().noCache());
    }

    @Test
    void testNoCacheEnabled() {
        String[] testData = new String[] { k(ConfigKey.NAME), "image", k(ConfigKey.NO_CACHE), "true", k(ConfigKey.FROM), "base" };

        ImageConfiguration config = resolveExternalImageConfig(testData);
        Assertions.assertTrue(config.getBuildConfiguration().noCache());
    }

    @Test
    void testCacheFrom() {
        String[] testData = new String[] { k(ConfigKey.NAME), "image", k(ConfigKey.CACHE_FROM) + ".1", "foo/bar:latest", k(ConfigKey.FROM), "base" };

        ImageConfiguration config = resolveExternalImageConfig(testData);
        Assertions.assertEquals(Collections.singletonList("foo/bar:latest"), config.getBuildConfiguration().getCacheFrom());
    }

    @Test
    void testCacheFromIsNullInBuildConfig() {
        imageConfiguration = new ImageConfiguration.Builder()
            .externalConfig(new HashMap<>())
            .buildConfig(new BuildImageConfiguration.Builder().build())
            .build();

        List<ImageConfiguration> configs = resolveImage(
            imageConfiguration, props(
                "docker.name", "demo",
                "docker.from", "busybox"
            ));

        Assertions.assertNull(configs.get(0).getBuildConfiguration().getCacheFrom());
    }

    @Test
    void testNoOptimise() {
        String[] testData = new String[] { k(ConfigKey.NAME), "image", k(ConfigKey.OPTIMISE), "false", k(ConfigKey.FROM), "base" };

        ImageConfiguration config = resolveExternalImageConfig(testData);
        Assertions.assertFalse(config.getBuildConfiguration().optimise());
    }

    @Test
    void testDockerFile() {
        String[] testData = new String[] { k(ConfigKey.NAME), "image", k(ConfigKey.DOCKER_FILE), "file" };

        ImageConfiguration config = resolveExternalImageConfig(testData);
        Assertions.assertNotNull(config.getBuildConfiguration());
    }

    @Test
    void testDockerFileDir() {
        String[] testData = new String[] { k(ConfigKey.NAME), "image", k(ConfigKey.DOCKER_FILE_DIR), "dir" };

        ImageConfiguration config = resolveExternalImageConfig(testData);
        Assertions.assertNotNull(config.getBuildConfiguration());
    }

    @Test
    void testContextDir() {
        String[] testData = new String[] { k(ConfigKey.NAME), "image", k(ConfigKey.CONTEXT_DIR), "dir" };

        ImageConfiguration config = resolveExternalImageConfig(testData);
        Assertions.assertNotNull(config.getBuildConfiguration());
    }

    @Test
    void testFilterDefault() {
        String[] testData = new String[] { k(ConfigKey.NAME), "image", k(ConfigKey.FROM), "base" };

        ImageConfiguration config = resolveExternalImageConfig(testData);
        Assertions.assertEquals(DEFAULT_FILTER, config.getBuildConfiguration().getFilter());
    }

    @Test
    void testFilter() {
        String filter = "@";
        String[] testData = new String[] { k(ConfigKey.NAME), "image", k(ConfigKey.FROM), "base", k(ConfigKey.FILTER), filter };

        ImageConfiguration config = resolveExternalImageConfig(testData);
        Assertions.assertEquals(filter, config.getBuildConfiguration().getFilter());
    }

    @Test
    void testCleanupDefault() {
        String[] testData = new String[] { k(ConfigKey.NAME), "image", k(ConfigKey.FROM), "base" };

        ImageConfiguration config = resolveExternalImageConfig(testData);
        Assertions.assertEquals(DEFAULT_CLEANUP, config.getBuildConfiguration().cleanupMode().toParameter());
    }

    @Test
    void testCleanup() {
        CleanupMode mode = CleanupMode.REMOVE;
        String[] testData = new String[] { k(ConfigKey.NAME), "image", k(ConfigKey.FROM), "base", k(ConfigKey.CLEANUP), mode.toParameter() };

        ImageConfiguration config = resolveExternalImageConfig(testData);
        Assertions.assertEquals(mode, config.getBuildConfiguration().cleanupMode());
    }

    @Test
    void testUlimit() {
        imageConfiguration = new ImageConfiguration.Builder()
            .externalConfig(new HashMap<>())
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
            imageConfiguration, props(
                k(ConfigKey.NAME), "image",
                k(ConfigKey.FROM), "base",
                k(ConfigKey.ULIMITS) + ".1", "memlock=10:10",
                k(ConfigKey.ULIMITS) + ".2", "memlock=:-1",
                k(ConfigKey.ULIMITS) + ".3", "memlock=1024:",
                k(ConfigKey.ULIMITS) + ".4", "memlock=2048"
            ));

        Assertions.assertEquals(1, configs.size());
        RunImageConfiguration runConfig = configs.get(0).getRunConfiguration();
        List<UlimitConfig> ulimits = runConfig.getUlimits();

        Assertions.assertEquals(4, ulimits.size());
        assertUlimitEquals(ulimit("memlock", 10, 10), runConfig.getUlimits().get(0));
        assertUlimitEquals(ulimit("memlock", null, -1), runConfig.getUlimits().get(1));
        assertUlimitEquals(ulimit("memlock", 1024, null), runConfig.getUlimits().get(2));
        assertUlimitEquals(ulimit("memlock", 2048, null), runConfig.getUlimits().get(3));
    }

    @Test
    void testCopyConfiguration() {
        imageConfiguration = new ImageConfiguration.Builder()
            .externalConfig(new HashMap<>())
            .copyConfig(new CopyConfiguration.Builder()
                .entries(Collections.singletonList(new CopyConfiguration.Entry("/test4/path", "project/dir")))
                .build())
            .build();

        makeExternalConfigUse(PropertyMode.Override);

        final List<ImageConfiguration> configs = resolveImage(
            imageConfiguration, props(
                k(ConfigKey.NAME), "image",
                k(ConfigKey.FROM), "base",
                k(ConfigKey.COPY_ENTRIES) + ".1", "/test1",
                k(ConfigKey.COPY_ENTRIES) + ".2." + CopyConfiguration.CONTAINER_PATH_PROPERTY, "/test2",
                k(ConfigKey.COPY_ENTRIES) + ".2." + CopyConfiguration.HOST_DIRECTORY_PROPERTY, "/root/dir",
                k(ConfigKey.COPY_ENTRIES) + ".3." + CopyConfiguration.CONTAINER_PATH_PROPERTY, "/test3/path",
                k(ConfigKey.COPY_ENTRIES) + ".3." + CopyConfiguration.HOST_DIRECTORY_PROPERTY, "project/dir"
            ));

        Assertions.assertEquals(1, configs.size());
        final CopyConfiguration copyConfig = configs.get(0).getCopyConfiguration();
        final List<CopyConfiguration.Entry> copyEntries = copyConfig.getEntries();

        Assertions.assertEquals(3, copyEntries.size());
        assertCopyEntryEquals(new CopyConfiguration.Entry("/test1", null), copyEntries.get(0));
        assertCopyEntryEquals(new CopyConfiguration.Entry("/test2", "/root/dir"), copyEntries.get(1));
        assertCopyEntryEquals(new CopyConfiguration.Entry("/test3/path", "project/dir"), copyEntries.get(2));
    }

    @Test
    void testResolve() {
        ImageConfiguration resolved = resolveExternalImageConfig(getTestData());

        validateBuildConfiguration(resolved.getBuildConfiguration());
        validateRunConfiguration(resolved.getRunConfiguration());
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
        Assertions.assertTrue(env.containsKey("HOME"));
        Assertions.assertEquals("/Users/roland", env.get("HOME"));
    }

    private ImageConfiguration buildAnUnresolvedImage() {
        return new ImageConfiguration.Builder()
            .externalConfig(new HashMap<>())
            .build();
    }

    private Map<String, String> externalConfigMode(PropertyMode mode) {
        Map<String, String> externalConfig = new HashMap<>();
        if (mode != null) {
            externalConfig.put("type", "properties");
            externalConfig.put("mode", mode.name());
        }
        return externalConfig;
    }

    private void makeExternalConfigUse(PropertyMode mode) {
        Map<String, String> externalConfig = imageConfiguration.getExternalConfig();
        externalConfig.put("type", "properties");
        if (mode != null) {
            externalConfig.put("mode", mode.name());
        } else {
            externalConfig.remove("mode");
        }
    }

    private List<ImageConfiguration> resolveImage(ImageConfiguration image, final Properties properties) {
        //MavenProject project = mock(MavenProject.class);
        //when(project.getProperties()).thenReturn(properties);

        Mockito.doReturn(properties).when(project).getProperties();
        Mockito.lenient().doReturn(new File("./")).when(project).getBasedir();

        return configHandler.resolve(image, project, null);
    }

    private ImageConfiguration resolveExternalImageConfig(String[] testData) {
        Map<String, String> external = new HashMap<>();
        external.put("type", "properties");

        ImageConfiguration config = new ImageConfiguration.Builder().name("image").alias("alias").externalConfig(external).build();

        List<ImageConfiguration> resolvedImageConfigs = resolveImage(config, props(testData));
        Assertions.assertEquals(1, resolvedImageConfigs.size());

        return resolvedImageConfigs.get(0);
    }

    private void validateBuildConfiguration(BuildImageConfiguration buildConfig) {
        Assertions.assertEquals(CleanupMode.TRY_TO_REMOVE, buildConfig.cleanupMode());
        Assertions.assertEquals("command.sh", buildConfig.getCmd().getShell());
        Assertions.assertEquals("image", buildConfig.getFrom());
        Assertions.assertEquals("image-ext", buildConfig.getFromExt().get("name"));
        Assertions.assertEquals(a("8080"), buildConfig.getPorts());
        Assertions.assertEquals("registry", buildConfig.getRegistry());
        Assertions.assertEquals(a("/foo"), buildConfig.getVolumes());
        Assertions.assertEquals("fabric8io@redhat.com", buildConfig.getMaintainer());
        Assertions.assertFalse(buildConfig.noCache());
        Assertions.assertEquals("Always", buildConfig.getImagePullPolicy());

        validateEnv(buildConfig.getEnv());
        validateLabels(buildConfig.getLabels());
        validateArgs(buildConfig.getArgs());
        validateBuildOptions(buildConfig.getBuildOptions());
        /*
         * validate only the descriptor is required and defaults are all used, 'testAssembly' validates
         * all options can be set
         */
        List<AssemblyConfiguration> assemblyConfigurations = buildConfig.getAllAssemblyConfigurations();
        Assertions.assertEquals(1, assemblyConfigurations.size());

        AssemblyConfiguration assemblyConfig = assemblyConfigurations.get(0);

        Assertions.assertEquals("/maven", assemblyConfig.getTargetDir());
        Assertions.assertEquals("assembly.xml", assemblyConfig.getDescriptor());
        Assertions.assertNull(assemblyConfig.getUser());
        Assertions.assertNull(assemblyConfig.exportTargetDir());
        Assertions.assertFalse(assemblyConfig.isIgnorePermissions());
    }

    private void validateArgs(Map<String, String> args) {
        Assertions.assertEquals("http://proxy", args.get("PROXY"));
    }

    private void validateLabels(Map<String, String> labels) {
        Assertions.assertEquals("Hello\"World", labels.get("com.acme.label"));
    }

    private void validateBuildOptions(Map<String, String> buildOptions) {
        Assertions.assertEquals("2147483648", buildOptions.get("shmsize"));
    }

    protected void validateRunConfiguration(RunImageConfiguration runConfig) {
        Assertions.assertEquals(a("/foo", "/tmp:/tmp"), runConfig.getVolumeConfiguration().getBind());
        Assertions.assertEquals(a("CAP"), runConfig.getCapAdd());
        Assertions.assertEquals(a("CAP"), runConfig.getCapDrop());
        Assertions.assertEquals(Collections.singletonMap("key", "value"), runConfig.getSysctls());
        Assertions.assertEquals(a("seccomp=unconfined"), runConfig.getSecurityOpts());
        Assertions.assertEquals("command.sh", runConfig.getCmd().getShell());
        Assertions.assertEquals(a("8.8.8.8"), runConfig.getDns());
        Assertions.assertEquals("host", runConfig.getNetworkingConfig().getStandardMode(null));
        Assertions.assertEquals(a("example.com"), runConfig.getDnsSearch());
        Assertions.assertEquals("domain.com", runConfig.getDomainname());
        Assertions.assertEquals("entrypoint.sh", runConfig.getEntrypoint().getShell());
        Assertions.assertEquals(a("localhost:127.0.0.1"), runConfig.getExtraHosts());
        Assertions.assertEquals("subdomain", runConfig.getHostname());
        Assertions.assertEquals(a("redis"), runConfig.getLinks());
        Assertions.assertEquals((Long) 1L, runConfig.getMemory());
        Assertions.assertEquals((Long) 1L, runConfig.getMemorySwap());
        Assertions.assertEquals(1.5, runConfig.getCpus());
        Assertions.assertEquals("default", runConfig.getIsolation());
        Assertions.assertEquals((Long) 1L, runConfig.getCpuShares());
        Assertions.assertEquals("0,1", runConfig.getCpuSet());
        Assertions.assertEquals("/tmp/envProps.txt", runConfig.getEnvPropertyFile());
        Assertions.assertEquals("/tmp/props.txt", runConfig.getPortPropertyFile());
        Assertions.assertEquals(a("8081:8080"), runConfig.getPorts());
        Assertions.assertEquals(true, runConfig.getPrivileged());
        Assertions.assertEquals("tomcat", runConfig.getUser());
        Assertions.assertEquals(a("from"), runConfig.getVolumeConfiguration().getFrom());
        Assertions.assertEquals("foo", runConfig.getWorkingDir());
        Assertions.assertNotNull(runConfig.getUlimits());
        Assertions.assertEquals(4, runConfig.getUlimits().size());
        assertUlimitEquals(ulimit("memlock", 10, 10), runConfig.getUlimits().get(0));
        assertUlimitEquals(ulimit("memlock", null, -1), runConfig.getUlimits().get(1));
        assertUlimitEquals(ulimit("memlock", 1024, null), runConfig.getUlimits().get(2));
        assertUlimitEquals(ulimit("memlock", 2048, null), runConfig.getUlimits().get(3));
        Assertions.assertEquals("/var/lib/mysql:10m", runConfig.getTmpfs().get(0));
        Assertions.assertEquals(1, runConfig.getTmpfs().size());
        Assertions.assertEquals("Never", runConfig.getImagePullPolicy());
        Assertions.assertEquals(true, runConfig.getReadOnly());
        Assertions.assertEquals(true, runConfig.getAutoRemove());

        validateEnv(runConfig.getEnv());

        // not sure it's worth it to implement 'equals/hashcode' for these
        RestartPolicy policy = runConfig.getRestartPolicy();
        Assertions.assertEquals("on-failure", policy.getName());
        Assertions.assertEquals(1, policy.getRetry());

        WaitConfiguration wait = runConfig.getWaitConfiguration();
        Assertions.assertEquals("http://foo.com", wait.getUrl());
        Assertions.assertEquals("pattern", wait.getLog());
        Assertions.assertEquals("post_start_command", wait.getExec().getPostStart());
        Assertions.assertEquals("pre_stop_command", wait.getExec().getPreStop());
        Assertions.assertTrue(wait.getExec().isBreakOnError());
        Assertions.assertEquals(5, wait.getTime().intValue());
        Assertions.assertTrue(wait.getHealthy());
        Assertions.assertEquals(0, wait.getExit().intValue());

        LogConfiguration config = runConfig.getLogConfiguration();
        Assertions.assertEquals("green", config.getColor());
        Assertions.assertTrue(config.isEnabled());
        Assertions.assertEquals("SRV", config.getPrefix());
        Assertions.assertEquals("iso8601", config.getDate());
        Assertions.assertEquals("json", config.getDriver().getName());
        Assertions.assertEquals(2, config.getDriver().getOpts().size());
        Assertions.assertEquals("1024", config.getDriver().getOpts().get("max-size"));
        Assertions.assertEquals("10", config.getDriver().getOpts().get("max-file"));
    }

    private UlimitConfig ulimit(String name, Integer hard, Integer soft) {
        return new UlimitConfig(name, hard, soft);
    }

    private Properties props(String... args) {
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

    private String[] getTestMultipleAssemblyData() {
        return new String[] {
            k(ConfigKey.FROM), "busybox",
            k(ConfigKey.ASSEMBLIES) + ".1." + k(ConfigKey.ASSEMBLY_BASEDIR), "/basedir",
            k(ConfigKey.ASSEMBLIES) + ".1." + k(ConfigKey.ASSEMBLY_DESCRIPTOR_REF), "project",
            k(ConfigKey.ASSEMBLIES) + ".1." + k(ConfigKey.ASSEMBLY_EXPORT_BASEDIR), "false",
            k(ConfigKey.ASSEMBLIES) + ".1." + k(ConfigKey.ASSEMBLY_IGNORE_PERMISSIONS), "true",
            k(ConfigKey.ASSEMBLIES) + ".1." + k(ConfigKey.ASSEMBLY_USER), "user",
            k(ConfigKey.ASSEMBLIES) + ".2." + k(ConfigKey.ASSEMBLY_BASEDIR), "/basedir",
            k(ConfigKey.ASSEMBLIES) + ".2." + k(ConfigKey.ASSEMBLY_DESCRIPTOR_REF), "artifact",
            k(ConfigKey.ASSEMBLIES) + ".2." + k(ConfigKey.ASSEMBLY_EXPORT_BASEDIR), "false",
            k(ConfigKey.ASSEMBLIES) + ".2." + k(ConfigKey.ASSEMBLY_IGNORE_PERMISSIONS), "true",
            k(ConfigKey.ASSEMBLIES) + ".2." + k(ConfigKey.ASSEMBLY_USER), "user",
            k(ConfigKey.ASSEMBLIES) + ".2." + k(ConfigKey.ASSEMBLY_NAME), "art",
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
            k(ConfigKey.SYSCTLS) + ".key", "value",
            k(ConfigKey.SECURITY_OPTS) + ".1", "seccomp=unconfined",
            k(ConfigKey.CPUS), "1.5",
            k(ConfigKey.CPUSET), "0,1",
            k(ConfigKey.ISOLATION), "default",
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
            k(ConfigKey.ULIMITS) + ".1", "memlock=10:10",
            k(ConfigKey.ULIMITS) + ".2", "memlock=:-1",
            k(ConfigKey.ULIMITS) + ".3", "memlock=1024:",
            k(ConfigKey.ULIMITS) + ".4", "memlock=2048",
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
        return new String[] { k(ConfigKey.NAME), "image", k(key), String.valueOf(value), k(ConfigKey.FROM), "busybox" };
    }

    private String k(ConfigKey from) {
        return from.asPropertyKey();
    }

    private void assertUlimitEquals(UlimitConfig expected, UlimitConfig actual) {
        Assertions.assertEquals(expected.getName(), actual.getName());
        Assertions.assertEquals(expected.getSoft(), actual.getSoft());
        Assertions.assertEquals(expected.getHard(), actual.getHard());
    }

    private void assertCopyEntryEquals(CopyConfiguration.Entry expected, CopyConfiguration.Entry actual) {
        Assertions.assertEquals(expected.getContainerPath(), actual.getContainerPath());
        Assertions.assertEquals(expected.getHostDirectory(), actual.getHostDirectory());
    }
}
