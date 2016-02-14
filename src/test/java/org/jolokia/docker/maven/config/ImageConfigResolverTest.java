package org.jolokia.docker.maven.config;/*
                                         * Copyright 2014 Roland Huss Licensed under the Apache License, Version 2.0 (the "License"); you
                                         * may not use this file except in compliance with the License. You may obtain a copy of the License
                                         * at http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law or agreed to in
                                         * writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
                                         * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific
                                         * language governing permissions and limitations under the License.
                                         */

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

import java.util.HashMap;
import java.util.Map;

import org.jolokia.docker.maven.config.external.DockerComposeConfiguration;
import org.jolokia.docker.maven.config.external.ExternalImageConfiguration;
import org.jolokia.docker.maven.config.external.PropertiesConfiguration;
import org.jolokia.docker.maven.config.handler.ExternalConfigHandler;
import org.jolokia.docker.maven.config.handler.ImageConfigResolver;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import edu.emory.mathcs.backport.java.util.Collections;

/**
 * @author roland
 * @since 18/11/14
 */
public class ImageConfigResolverTest {

    @Mock
    private ExternalConfigHandler composeHandler;

    private Exception exception;

    @Mock
    private ExternalConfigHandler propertiesHandler;

    private ImageConfigResolver resolver;

    private ImageConfiguration unresolved;

    @Before
    public void setUp() throws Exception {
        resolver = new ImageConfigResolver();
        resolver.setResolvers(createHandlers());
    }

    @Test
    public void testComposeHandlerCalled() {
        givenAnUnresolvedDockerComposeImage();
        whenResolveImage();
        thenDockerComposeHandlerInvoked();
    }

    @Test
    public void testHandlerNotFound() {
        // image type doesn't really matter
        givenAnUnresolvedDockerComposeImage();
        givenNoHandlerIsFound();
        whenResolveImage();
        thenIllegalArgumentExceptionThrown();
    }

    @Test
    public void testNoExternalHandlersCalled() {
        givenAnPomConfiguredImage();
        whenResolveImage();
        thenNoExternalHanldersInovked();
    }

    @Test
    public void testPropertiesHandlerCalled() {
        givenAnUnresolvedPropertiesImage();
        whenResolveImage();
        thenPropertiesHandlerInvoked();
    }
   
    private Map<String, ExternalConfigHandler> createHandlers() {
        MockitoAnnotations.initMocks(this);

        Map<String, ExternalConfigHandler> handlers = new HashMap<>(3);

        handlers.put(ImageConfigResolver.COMPOSE, composeHandler);
        handlers.put(ImageConfigResolver.PROPERTIES, propertiesHandler);

        return handlers;
    }

    private void givenAnPomConfiguredImage() {
        unresolved = new ImageConfiguration.Builder().name("pom").build();
    }

    private void givenAnUnresolvedDockerComposeImage() {
        DockerComposeConfiguration composeConfig = new DockerComposeConfiguration.Builder().build();
        ExternalImageConfiguration externalConfig = new ExternalImageConfiguration.Builder().compose(composeConfig).build();

        unresolved = new ImageConfiguration.Builder().externalConfig(externalConfig).build();
    }

    private void givenAnUnresolvedPropertiesImage() {
        PropertiesConfiguration propsConfig = new PropertiesConfiguration.Builder().build();
        ExternalImageConfiguration externalConfig = new ExternalImageConfiguration.Builder().properties(propsConfig).build();

        unresolved = new ImageConfiguration.Builder().externalConfig(externalConfig).build();
    }

    private void givenNoHandlerIsFound() {
        // a map w/ incorrect keys would work as well
        resolver.setResolvers(Collections.emptyMap());
    }

    private void thenDockerComposeHandlerInvoked() {
        verify(composeHandler).resolve(unresolved, null);
    }
    
    private void thenIllegalArgumentExceptionThrown() {
        assertNotNull(exception);
        assertTrue(exception instanceof IllegalArgumentException);
    }

    private void thenNoExternalHanldersInovked() {
        verifyZeroInteractions(composeHandler, propertiesHandler);
    }

    private void thenPropertiesHandlerInvoked() {
        verify(propertiesHandler).resolve(unresolved, null);
    }

    private void whenResolveImage() {
        try {
            resolver.resolve(unresolved, null);
        }
        catch (Exception e) {
            exception = e;
        }
    }
}
