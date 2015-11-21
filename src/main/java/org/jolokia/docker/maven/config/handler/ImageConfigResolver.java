package org.jolokia.docker.maven.config.handler;
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

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.maven.project.MavenProject;
import org.jolokia.docker.maven.config.ImageConfiguration;
import org.jolokia.docker.maven.config.external.ExternalImageConfiguration;

/**
 * Manager holding all config handlers for external configuration
 *
 * @author roland
 * @since 18/11/14
 */
public class ImageConfigResolver {

    public static final String COMPOSE = "compose";
    public static final String PROPERTIES = "properties";
    
    private Map<String, ExternalConfigHandler> resolvers;

    /**
     * Resolve an image configuration. If it contains a reference to an external configuration
     * the corresponding resolver is called and the resolved image configurations are returned (can
     * be multiple). If no reference to an external configuration is found, the original configuration
     * is returned directly.
     *
     * @param unresolvedConfig the configuration to resolve
     * @param project maven project
     * @return list of resolved image configurations
     * @throws IllegalArgumentException if no type is given when an external reference configuration is provided
     * or when the type is not known (i.e. no handler is registered for this type).
     */
    public List<ImageConfiguration> resolve(ImageConfiguration unresolvedConfig, MavenProject project) {
        ExternalImageConfiguration external = unresolvedConfig.getExternalConfiguration();

        if (external == null) {
            return Collections.singletonList(unresolvedConfig);
        }
        
        String type = getHandlerType(external);
        if (resolvers.containsKey(type)) {
            return resolvers.get(type).resolve(unresolvedConfig, project);
        }

        throw new IllegalArgumentException(unresolvedConfig.getDescription() + ": No handler for type " + type + " found");
    }
    
    public void setResolvers(Map<String, ExternalConfigHandler> resolvers) {
        this.resolvers = resolvers;
    }
    
    private String getHandlerType(ExternalImageConfiguration external) {
        if (external.hasDockerCompose()) {
            return COMPOSE;
        } else {
            return PROPERTIES;
        }
    }
}
