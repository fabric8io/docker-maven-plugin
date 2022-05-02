package io.fabric8.maven.docker.config;
/*
 *
 * Copyright 2016 Roland Huss
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

import java.util.*;

import io.fabric8.maven.docker.util.AnsiLogger;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * @author roland
 * @since 17/05/16
 */
class ConfigHelperTest {

    private boolean resolverCalled;
    private boolean customizerCalled;

    @BeforeEach
    void setUp() {
        resolverCalled = false;
        customizerCalled = false;
    }

    @Test
    void noName() {
        List<ImageConfiguration> configs = Collections.singletonList(new ImageConfiguration.Builder().build());
        ConfigHelper.Resolver resolver = createResolver();
        ConfigHelper.Customizer customizer = createCustomizer();
        IllegalArgumentException exp = Assertions.assertThrows(IllegalArgumentException.class,
            () -> ConfigHelper.resolveImages(null, configs, resolver, null, customizer));
        Assertions.assertTrue(exp.getMessage().contains("name"));
    }

    @Test
    void externalPropertyActivation() throws MojoFailureException {
        MavenProject project = new MavenProject();
        project.getProperties().put(ConfigHelper.EXTERNALCONFIG_ACTIVATION_PROPERTY, "anything");

        List<ImageConfiguration> images = Collections.singletonList(new ImageConfiguration.Builder().name("test").build());
        ConfigHelper.validateExternalPropertyActivation(project, images);

        images = Arrays.asList(
            new ImageConfiguration.Builder().name("test").build(),
            new ImageConfiguration.Builder().name("test2").build());

        List<ImageConfiguration> finalImages = images;
        MojoFailureException ex = Assertions.assertThrows(MojoFailureException.class,
            () -> ConfigHelper.validateExternalPropertyActivation(project, finalImages));
        Assertions.assertTrue(ex.getMessage().contains("Cannot use property " + ConfigHelper.EXTERNALCONFIG_ACTIVATION_PROPERTY + " on projects with multiple images"));

        // When one of the images are configured externally from other source, it is OK with two images.
        Map<String, String> externalConfig = new HashMap<>();
        images.get(0).setExternalConfiguration(externalConfig);
        externalConfig.put("type", "othermagic");

        ConfigHelper.validateExternalPropertyActivation(project, images);

        // Or if prefix is set explicitly
        externalConfig.put("type", "properties");
        externalConfig.put("prefix", "docker");

        ConfigHelper.validateExternalPropertyActivation(project, images);

        // But with default prefix it fails
        externalConfig.remove("prefix");

        List<ImageConfiguration> finalImages1 = images;
        ex = Assertions.assertThrows(MojoFailureException.class,
            () -> ConfigHelper.validateExternalPropertyActivation(project, finalImages1));
        Assertions.assertTrue(ex.getMessage().contains("Cannot use property " + ConfigHelper.EXTERNALCONFIG_ACTIVATION_PROPERTY + " on projects with multiple images"));

        // With no external properly, it works.
        project.getProperties().clear();
        ConfigHelper.validateExternalPropertyActivation(project, images);

        // And if explicitly set to "skip" it works too.
        project.getProperties().put(ConfigHelper.EXTERNALCONFIG_ACTIVATION_PROPERTY, "skip");
        ConfigHelper.validateExternalPropertyActivation(project, images);
    }

    @Test
    void simple() {
        List<ImageConfiguration> configs = Collections.singletonList(new ImageConfiguration.Builder().name("test").build());
        List<ImageConfiguration> result = ConfigHelper.resolveImages(null, configs, createResolver(), null, createCustomizer());
        Assertions.assertEquals(1, result.size());
        Assertions.assertTrue(resolverCalled);
        Assertions.assertTrue(customizerCalled);
    }

    @Test
    void registry() {
        List<ImageConfiguration> configs = Collections.singletonList(new ImageConfiguration.Builder().registry("docker.io").name("test").build());
        List<ImageConfiguration> result = ConfigHelper.resolveImages(null, configs, createResolver(), null, createCustomizer());
        Assertions.assertEquals(1, result.size());
        Assertions.assertTrue(resolverCalled);
        Assertions.assertTrue(customizerCalled);
        Assertions.assertEquals("docker.io", configs.get(0).getRegistry());
    }

    @Test
    void filter() {
        List<ImageConfiguration> configs = Collections.singletonList(new ImageConfiguration.Builder().name("test").build());
        CatchingLog logCatcher = new CatchingLog();
        List<ImageConfiguration> result = ConfigHelper.resolveImages(
            new AnsiLogger(logCatcher, true, "build"),
            configs, createResolver(), "bla", createCustomizer());
        Assertions.assertEquals(0, result.size());
        Assertions.assertTrue(resolverCalled);
        Assertions.assertTrue(customizerCalled);
        Assertions.assertTrue(logCatcher.getWarnMessage().contains("test"));
        Assertions.assertTrue(logCatcher.getWarnMessage().contains("bla"));
    }

    @Test
    void initAndValidate() {
        List<ImageConfiguration> configs = Collections.singletonList(new ImageConfiguration.Builder().name("test").build());
        String api = ConfigHelper.initAndValidate(configs, "v1.16", ConfigHelper.NameFormatter.IDENTITY, null);
        Assertions.assertEquals("v1.16", api);
    }

    private ConfigHelper.Customizer createCustomizer() {
        return configs -> {
            customizerCalled = true;
            return configs;
        };
    }

    private ConfigHelper.Resolver createResolver() {
        return image -> {
            resolverCalled = true;
            return Collections.singletonList(image);
        };
    }

    private static class CatchingLog extends SystemStreamLog {
        private String warnMessage;

        @Override
        public void warn(CharSequence content) {
            this.warnMessage = content.toString();
            super.warn(content);
        }

        public String getWarnMessage() {
            return warnMessage;
        }
    }

}
