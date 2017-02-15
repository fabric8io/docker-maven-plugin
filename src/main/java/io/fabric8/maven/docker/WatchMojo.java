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

import io.fabric8.maven.docker.access.DockerAccessException;
import io.fabric8.maven.docker.config.WatchMode;
import io.fabric8.maven.docker.service.BuildService;
import io.fabric8.maven.docker.service.ServiceHub;
import io.fabric8.maven.docker.service.WatchService;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Mojo for watching source code changes.
 *
 * This Mojo does essentially
 * two things when it detects a image content change:
 *
 * <ul>
 *     <li>Rebuilding one or more images</li>
 *     <li>Restarting restarting one or more containers </li>
 * </ul>
 *
 * @author roland
 * @since 16/06/15
 */
@Mojo(name = "watch")
public class WatchMojo extends AbstractBuildSupportMojo {

    /**
     * Watching mode for rebuilding images
     */
    @Parameter(property = "docker.watchMode", defaultValue = "both")
    private WatchMode watchMode;

    @Parameter(property = "docker.watchInterval", defaultValue = "5000")
    private int watchInterval;

    @Parameter(property = "docker.keepRunning", defaultValue = "false")
    private boolean keepRunning;

    @Parameter(property = "docker.watchPostGoal")
    private String watchPostGoal;

    @Parameter(property = "docker.watchPostExec")
    private String watchPostExec;

    /**
     * Whether to create the customs networks (user-defined bridge networks) before starting automatically
     */
    @Parameter(property = "docker.autoCreateCustomNetworks", defaultValue = "false")
    protected boolean autoCreateCustomNetworks;

    @Override
    protected synchronized void executeInternal(ServiceHub hub) throws DockerAccessException,
                                                                       MojoExecutionException {

        BuildService.BuildContext buildContext = getBuildContext();
        WatchService.WatchContext watchContext = getWatchContext();

        hub.getWatchService().watch(watchContext, buildContext, getResolvedImages());
    }

    protected WatchService.WatchContext getWatchContext() throws MojoExecutionException {
        return new WatchService.WatchContext.Builder()
                .watchInterval(watchInterval)
                .watchMode(watchMode)
                .watchPostGoal(watchPostGoal)
                .watchPostExec(watchPostExec)
                .autoCreateCustomNetworks(autoCreateCustomNetworks)
                .keepContainer(keepContainer)
                .keepRunning(keepRunning)
                .removeVolumes(removeVolumes)
                .pomLabel(getPomLabel())
                .mojoParameters(createMojoParameters())
                .build();
    }

}
