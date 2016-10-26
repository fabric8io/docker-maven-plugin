package io.fabric8.maven.docker.config.handler;/*
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

import java.util.List;

import io.fabric8.maven.docker.config.ImageConfiguration;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;

/**
 * Interface which needs to be implemented to create
 * image configurations from external sources
 *
 * @author roland
 * @since 18/11/14
 */
public interface ExternalConfigHandler {

    /**
     * Get the unique type of this plugin as referenced with the <code>&lt;type&gt;</code> tag within a
     * <code>&lt;reference&gt;</code> configuration section
     *
     * @return plugin type
     */
    String getType();

    /**
     * For the given plugin configuration (which also contains the type) extract one or more
     * {@link ImageConfiguration} objects describing the image to manage
     *
     * @param unresolvedConfig the original, unresolved config
     * @param project maven project
     * @param session maven session
     * @return list of image configuration. Must not be null but can be empty.
     * @throws ExternalConfigHandlerException if there is a problem resolving the image configuration
     */
    List<ImageConfiguration> resolve(ImageConfiguration unresolvedConfig, MavenProject project, MavenSession session)
        throws ExternalConfigHandlerException;
}
