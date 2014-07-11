package org.jolokia.docker.maven;

/*
 * Copyright 2001-2005 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Goal for pushing a data-docker container
 *
 * @author roland
 */
@Mojo(name = "push")
public class PushMojo extends AbstractDataSupportedDockerMojo {

    // Whether the data image should be kept if an assembly is used
    @Parameter(property = "docker.keepData", defaultValue = "false")
    private boolean keepData;

    // Registry to push image to
    @Parameter(property = "docker.registry")
    String registry;

    /** {@inheritDoc} */
    public void executeInternal(DockerAccess docker) throws MojoExecutionException, MojoFailureException {
        if (assemblyDescriptor != null && assemblyDescriptorRef != null) {
            throw new MojoExecutionException("No assemblyDescriptor or assemblyDescriptorRef have been given");
        }
        String image = createDataImage(null,docker);
        docker.pushImage(image,registry);
        if (!keepData) {
            docker.removeImage(image);
        }
    }
}
