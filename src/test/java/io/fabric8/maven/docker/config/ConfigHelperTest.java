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
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author roland
 * @since 17/05/16
 */
public class ConfigHelperTest {

    private boolean resolverCalled;
    private boolean customizerCalled;

    @Before
    public void setUp() throws Exception {
        resolverCalled = false;
        customizerCalled = false;
    }

    @Test
    public void noName() throws Exception {
        try {
            List<ImageConfiguration> configs = Arrays.asList(new ImageConfiguration.Builder().build());
            ConfigHelper.resolveImages(null, configs, createResolver(), null, createCustomizer());
            fail();
        } catch (IllegalArgumentException exp) {
            assertTrue(exp.getMessage().contains("name"));
        }
    }

    @Test
    public void externalPropertyActivation() throws MojoFailureException {
        MavenProject project = new MavenProject();
        project.getProperties().put(ConfigHelper.EXTERNALCONFIG_ACTIVATION_PROPERTY, "anything");

        List<ImageConfiguration> images = Arrays.asList(new ImageConfiguration.Builder().name("test").build());
        ConfigHelper.validateExternalPropertyActivation(project, images);

        images = Arrays.asList(
                new ImageConfiguration.Builder().name("test").build(),
                new ImageConfiguration.Builder().name("test2").build());

        try {
            ConfigHelper.validateExternalPropertyActivation(project, images);
            fail();
        }catch(MojoFailureException ex) {
            assertTrue(ex.getMessage().contains("Cannot use property " + ConfigHelper.EXTERNALCONFIG_ACTIVATION_PROPERTY + " on projects with multiple images"));
        }

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

        try {
            ConfigHelper.validateExternalPropertyActivation(project, images);
            fail();
        }catch(MojoFailureException ex) {
            assertTrue(ex.getMessage().contains("Cannot use property " + ConfigHelper.EXTERNALCONFIG_ACTIVATION_PROPERTY + " on projects with multiple images"));
        }

        // With no external properly, it works.
        project.getProperties().clear();
        ConfigHelper.validateExternalPropertyActivation(project, images);

        // And if explicitly set to "skip" it works too.
        project.getProperties().put(ConfigHelper.EXTERNALCONFIG_ACTIVATION_PROPERTY, "skip");
        ConfigHelper.validateExternalPropertyActivation(project, images);
    }

    @Test
    public void simple() throws Exception {
        List<ImageConfiguration> configs = Arrays.asList(new ImageConfiguration.Builder().name("test").build());
        List<ImageConfiguration> result = ConfigHelper.resolveImages(null, configs, createResolver(), null, createCustomizer());
        assertEquals(1,result.size());
        assertTrue(resolverCalled);
        assertTrue(customizerCalled);
    }

    @Test
    public void registry() throws Exception {
        List<ImageConfiguration> configs = Arrays.asList(new ImageConfiguration.Builder().registry("docker.io").name("test").build());
        List<ImageConfiguration> result = ConfigHelper.resolveImages(null, configs, createResolver(), null, createCustomizer());
        assertEquals(1,result.size());
        assertTrue(resolverCalled);
        assertTrue(customizerCalled);
        assertEquals("docker.io", configs.get(0).getRegistry());
    }

    @Test
    public void filter() throws Exception {
        List<ImageConfiguration> configs = Arrays.asList(new ImageConfiguration.Builder().name("test").build());
        CatchingLog logCatcher = new CatchingLog();
        List<ImageConfiguration> result = ConfigHelper.resolveImages(
            new AnsiLogger(logCatcher, true, "build"),
            configs, createResolver(), "bla", createCustomizer());
        assertEquals(0,result.size());
        assertTrue(resolverCalled);
        assertTrue(customizerCalled);
        assertTrue(logCatcher.getWarnMessage().contains("test"));
        assertTrue(logCatcher.getWarnMessage().contains("bla"));
    }

    @Test
    public void initAndValidate() throws Exception {
        List<ImageConfiguration> configs = Arrays.asList(new ImageConfiguration.Builder().name("test").build());
        String api = ConfigHelper.initAndValidate(configs, "v1.16", ConfigHelper.NameFormatter.IDENTITY, null);
        assertEquals("v1.16",api);
    }

    private ConfigHelper.Customizer createCustomizer() {
        return new ConfigHelper.Customizer() {
            @Override
            public List<ImageConfiguration> customizeConfig(List<ImageConfiguration> configs) {
                customizerCalled = true;
                return configs;
            }
        };
    }

    private ConfigHelper.Resolver createResolver() {
        return new ConfigHelper.Resolver() {
            @Override
            public List<ImageConfiguration> resolve(ImageConfiguration image) {
                resolverCalled = true;
                return Collections.singletonList(image);
            }
        };
    }

    private class CatchingLog extends SystemStreamLog {
        private String warnMessage;

        @Override
        public void warn(CharSequence content) {
            this.warnMessage = content.toString();
            super.warn(content);
        }

        void reset() {
            warnMessage = null;
        }

        public String getWarnMessage() {
            return warnMessage;
        }
    }

}
