package io.fabric8.maven.docker.config;/*
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

import java.util.*;

import io.fabric8.maven.docker.config.handler.ExternalConfigHandler;
import io.fabric8.maven.docker.config.handler.ImageConfigResolver;
import io.fabric8.maven.docker.util.Logger;
import mockit.Mocked;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.InitializationException;
import org.codehaus.plexus.util.ReflectionUtils;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author roland
 * @since 18/11/14
 */
public class ImageConfigResolverTest {

    private ImageConfigResolver resolver;

    @Mocked
    private Logger log;

    @Before
    public void setUp() throws Exception {
        resolver = new ImageConfigResolver();
        ReflectionUtils.setVariableValueInObject(resolver, "propertyConfigHandler", new TestHandler(3));
        resolver.initialize();
        resolver.setLog(log);
    }

    @Test
    public void direct() throws IllegalAccessException, InitializationException {
        List<ImageConfiguration> rest = resolver.resolve(getImageConfiguration("vanilla"), new MavenProject(), null);
        assertEquals(1, rest.size());
        assertEquals("vanilla", rest.get(0).getName());
    }

    @Test
    public void withReference() throws Exception {
        Map<String,String> refConfig = Collections.singletonMap("type", "test");
        ImageConfiguration config = new ImageConfiguration.Builder().name("reference").externalConfig(refConfig).build();
        List<ImageConfiguration> rest = resolver.resolve(config,new MavenProject(), null);
        assertEquals(3,rest.size());
        for (int i = 0; i < 3;i++) {
            assertEquals("image " + i,rest.get(i).getName());
        }
    }

    @Test
    public void withExternalConfigActivation() throws Exception {
        MavenProject project = new MavenProject();
        // Value is not verified since we're only using our TestHandler
        project.getProperties().put(ConfigHelper.EXTERNALCONFIG_ACTIVATION_PROPERTY, "notactuallyverified");

        List<ImageConfiguration> rest = resolver.resolve(getImageConfiguration("vanilla"), project, null);
        assertEquals(3,rest.size());
        for (int i = 0; i < 3;i++) {
            assertEquals("image " + i,rest.get(i).getName());
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void noType() {
        Map<String,String> refConfig = Collections.singletonMap("notAType","test");
        ImageConfiguration config = new ImageConfiguration.Builder()
                .name("reference")
                .externalConfig(refConfig).build();
        resolver.resolve(config,new MavenProject(), null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void unknownType() {
        Map<String,String> refConfig = Collections.singletonMap("type","unknown");
        ImageConfiguration config = new ImageConfiguration.Builder()
                .name("reference")
                .externalConfig(refConfig).build();
        resolver.resolve(config,new MavenProject(), null);
    }

    private static class TestHandler implements ExternalConfigHandler {

        int nr;

        public TestHandler(int nr) {
            this.nr = nr;
        }

        @Override
        public String getType() {
            return "test";
        }

        @Override
        public List<ImageConfiguration> resolve(ImageConfiguration referenceConfig, MavenProject project, MavenSession session) {
            List<ImageConfiguration> ret = new ArrayList<>();
            for (int i = 0; i < nr;i++) {
                ImageConfiguration config = getImageConfiguration("image " + i);
                ret.add(config);
            }
            return ret;
        }
    }

    private static ImageConfiguration getImageConfiguration(String name) {
        return new ImageConfiguration.Builder().name(name).build();
    }
}
