package io.fabric8.maven.docker;/*
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

import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

import io.fabric8.maven.docker.access.DockerAccessException;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.service.QueryService;
import io.fabric8.maven.docker.service.ServiceHub;

/**
 * Mojo for removing image by name. Any image with or without run configuration.
 * 
 * @author nirkoren
 * @since 08.09.2016
 * 
 */
@Mojo(name = "removeimage", defaultPhase = LifecyclePhase.POST_INTEGRATION_TEST)
@Execute(phase = LifecyclePhase.INITIALIZE)
public class RemoveImageMojo extends AbstractDockerMojo {


	@Override
	protected void executeInternal(ServiceHub hub) throws DockerAccessException {
		QueryService queryService = hub.getQueryService();
		for (ImageConfiguration image : getResolvedImages()) {
			String name = image.getName();
				if (queryService.hasImage(name)) {
					if (hub.getDockerAccess().removeImage(name,true)) {
						log.info("Image '%s' removed successfully ",image.getDescription());
					}
				}
		}
	}
}
