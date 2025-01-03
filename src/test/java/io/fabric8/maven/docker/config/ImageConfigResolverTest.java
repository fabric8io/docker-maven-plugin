package io.fabric8.maven.docker.config;

/*
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

import io.fabric8.maven.docker.config.handler.ExternalConfigHandler;
import io.fabric8.maven.docker.config.handler.ImageConfigResolver;
import io.fabric8.maven.docker.config.handler.property.PropertyConfigHandler;
import io.fabric8.maven.docker.util.Logger;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author roland
 * @since 18/11/14
 */
@ExtendWith(MockitoExtension.class)
class ImageConfigResolverTest {

    private ImageConfigResolver resolver;

    @Mock
    private Logger log;

    @BeforeEach
    void setUp() throws Exception {
        resolver = new ImageConfigResolver(Arrays.asList(new TestHandler(PropertyConfigHandler.TYPE_NAME, 3), new TestHandler("test", 3)));
        resolver.setLog(log);
    }

    @Test
    void direct() {
        List<ImageConfiguration> rest = resolver.resolve(getImageConfiguration("vanilla"), new MavenProject(), null);
        Assertions.assertEquals(1, rest.size());
        Assertions.assertEquals("vanilla", rest.get(0).getName());
    }

    @Test
    void withReference() {
        Map<String, String> refConfig = Collections.singletonMap("type", "test");
        ImageConfiguration config = new ImageConfiguration.Builder().name("reference").externalConfig(refConfig).build();
        List<ImageConfiguration> rest = resolver.resolve(config, new MavenProject(), null);
        Assertions.assertEquals(3, rest.size());
        for (int i = 0; i < 3; i++) {
            Assertions.assertEquals("image " + i, rest.get(i).getName());
        }
    }

    @Test
    void withExternalConfigActivation() {
        MavenProject project = new MavenProject();
        // Value is not verified since we're only using our TestHandler
        project.getProperties().put(ConfigHelper.EXTERNALCONFIG_ACTIVATION_PROPERTY, "notactuallyverified");

        List<ImageConfiguration> rest = resolver.resolve(getImageConfiguration("vanilla"), project, null);
        Assertions.assertEquals(3, rest.size());
        for (int i = 0; i < 3; i++) {
            Assertions.assertEquals("image " + i, rest.get(i).getName());
        }
    }

    @Test
    void noType() {
        Map<String, String> refConfig = Collections.singletonMap("notAType", "test");
        ImageConfiguration config = new ImageConfiguration.Builder()
            .name("reference")
            .externalConfig(refConfig).build();
        MavenProject project = new MavenProject();
        Assertions.assertThrows(IllegalArgumentException.class, () -> resolver.resolve(config, project, null));
    }

    @Test
    void unknownType() {
        Map<String, String> refConfig = Collections.singletonMap("type", "unknown");
        ImageConfiguration config = new ImageConfiguration.Builder()
            .name("reference")
            .externalConfig(refConfig).build();
        MavenProject project = new MavenProject();
        Assertions.assertThrows(IllegalArgumentException.class, () -> resolver.resolve(config, project, null));
    }

    private static class TestHandler implements ExternalConfigHandler {

        final String typeName;
        final int nr;

        public TestHandler(String typeName, int nr) {
            this.typeName = typeName;
            this.nr = nr;
        }

        @Override
        public String getType() {
            return typeName;
        }

        @Override
        public List<ImageConfiguration> resolve(ImageConfiguration referenceConfig, MavenProject project, MavenSession session) {
            List<ImageConfiguration> ret = new ArrayList<>();
            for (int i = 0; i < nr; i++) {
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
