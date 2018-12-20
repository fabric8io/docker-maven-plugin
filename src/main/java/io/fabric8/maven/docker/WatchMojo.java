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

import java.io.IOException;

import io.fabric8.maven.docker.config.WatchMode;
import io.fabric8.maven.docker.service.BuildService;
import io.fabric8.maven.docker.service.ServiceHub;
import io.fabric8.maven.docker.service.WatchService;
import io.fabric8.maven.docker.util.ContainerNamingUtil;
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
    @Parameter
    private WatchMode watchMode;

    @Parameter
    private int watchInterval;

    @Parameter
    private boolean keepRunning;

    @Parameter
    private String watchPostGoal;

    @Parameter
    private String watchPostExec;

    /**
     * Naming pattern for how to name containers when started
     */
    @Parameter
    private String containerNamePattern = ContainerNamingUtil.DEFAULT_CONTAINER_NAME_PATTERN;

    /**
     * Whether to create the customs networks (user-defined bridge networks) before starting automatically
     */
    @Parameter
    protected boolean autoCreateCustomNetworks;

    @Override
    protected synchronized void executeInternal(ServiceHub hub) throws IOException,
                                                                       MojoExecutionException {

        BuildService.BuildContext buildContext = getBuildContext();
        WatchService.WatchContext watchContext = getWatchContext(hub);

        hub.getWatchService().watch(watchContext, buildContext, getResolvedImages());
    }


    @Override
    public String getPrefix() {
        return "docker.";
    }

    protected WatchService.WatchContext getWatchContext(ServiceHub hub) throws IOException {
        return new WatchService.WatchContext.Builder()
                .watchInterval(getWatchInterval())
                .watchMode(getWatchMode())
                .watchPostGoal(getWatchPostGoal())
                .watchPostExec(getWatchPostExec())
                .autoCreateCustomNetworks(getAutoCreateCustomNetworks())
                .keepContainer(getKeepContainer())
                .keepRunning(getKeepRunning())
                .removeVolumes(getRemoveVolumes())
                .containerNamePattern(getContainerNamePattern())
                .buildTimestamp(getBuildTimestamp())
                .pomLabel(getGavLabel())
                .mojoParameters(createMojoParameters())
                .follow(follow())
                .showLogs(showLogs())
                .serviceHubFactory(serviceHubFactory)
                .hub(hub)
                .dispatcher(getLogDispatcher(hub))
                .build();
    }

    private String showLogs() {
        return System.getProperty("docker.showLogs");
    }

    private boolean follow() {
        return Boolean.valueOf(System.getProperty("docker.follow", "false"));
    }

    private WatchMode getWatchMode() {
        String value = getProperty("watchMode", "both");
        switch(value) {
            case "build": return WatchMode.build;
            case "copy" : return WatchMode.copy;
            case "run"  : return WatchMode.run;
            case "both" : return WatchMode.both;
            case "none" : return WatchMode.none;
            default     : return WatchMode.both;
        }
    }

    private int getWatchInterval() {
        return Integer.parseInt(getProperty("watchInterval", "5000"));
    }

    private boolean getKeepRunning() {
        return Boolean.parseBoolean(getProperty("keepRunning"));
    }

    private String getWatchPostGoal() {
        return getProperty("watchPostGoal");
    }

    private String getWatchPostExec() {
        return getProperty("watchPostExec");
    }

    private String getContainerNamePattern() {
        return getProperty("containerNamePattern", ContainerNamingUtil.DEFAULT_CONTAINER_NAME_PATTERN);
    }

    private Boolean getAutoCreateCustomNetworks() {
        return Boolean.parseBoolean(getProperty("autoCreateCustomNetworks"));
    }
}