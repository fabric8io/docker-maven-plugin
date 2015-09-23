package org.jolokia.docker.maven;/*
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.StringUtils;
import org.jolokia.docker.maven.access.*;
import org.jolokia.docker.maven.assembly.AssemblyFiles;
import org.jolokia.docker.maven.config.*;
import org.jolokia.docker.maven.service.QueryService;
import org.jolokia.docker.maven.service.RunService;
import org.jolokia.docker.maven.util.MojoParameters;
import org.jolokia.docker.maven.util.StartOrderResolver;

import static org.jolokia.docker.maven.config.WatchMode.both;

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
 * @goal watch
 *
 * @author roland
 * @since 16/06/15
 */
public class WatchMojo extends AbstractBuildSupportMojo {

    /** @parameter property = "docker.watchMode" default-value="both" **/
    private WatchMode watchMode;

    /**
     * @parameter property = "docker.watchInterval" default-value = "5000"
     */
    private int watchInterval;

    /**
     * @parameter property = "docker.keepRunning" default-value = "false"
     */
    private boolean keepRunning;

    /**
     * @parameter property = "docker.watchPostGoal"
     */
    private String watchPostGoal;

    // Scheduler
    private ScheduledExecutorService executor;


    @Override
    protected synchronized void executeInternal(DockerAccess dockerAccess) throws DockerAccessException, MojoExecutionException {
        // Important to be be a single threaded scheduler since watch jobs must run serialized
        executor = Executors.newSingleThreadScheduledExecutor();

        QueryService queryService = serviceHub.getQueryService();
        RunService runService = serviceHub.getRunService();

        MojoParameters mojoParameters = createMojoParameters();

        try {
            for (StartOrderResolver.Resolvable resolvable : runService.getImagesConfigsInOrder(queryService, getImages())) {
                final ImageConfiguration imageConfig = (ImageConfiguration) resolvable;

                String imageId = queryService.getImageId(imageConfig.getName());
                String containerId = runService.lookupContainer(imageConfig.getName());

                ImageWatcher watcher = new ImageWatcher(imageConfig, imageId, containerId);

                ArrayList<String> tasks = new ArrayList<>();

                if (imageConfig.getBuildConfiguration() != null && watcher.isBuild()) {
                    scheduleBuildWatchTask(dockerAccess, watcher, mojoParameters, watchMode == both);
                    tasks.add("rebuilding");
                }
                if (watcher.isRun() && watcher.getContainerId() != null) {
                    scheduleRestartWatchTask(dockerAccess, watcher);
                    tasks.add("restarting");
                }
                if (tasks.size() > 0) {
                    log.info(imageConfig.getDescription() + ": Watch for " + StringUtils.join(tasks.toArray()," and "));
                }
            }
            log.info("Waiting ...");
            if (!keepRunning) {
                runService.addShutdownHookForStoppingContainers(keepContainer, removeVolumes);
            }
            wait();
        } catch (InterruptedException e) {
            log.warn("Interrupted");
        } finally {
            executor.shutdownNow();
        }
    }

    private void scheduleBuildWatchTask(DockerAccess dockerAccess, ImageWatcher watcher,
            MojoParameters mojoParameters, boolean doRestart) throws MojoExecutionException {
        executor.scheduleAtFixedRate(
                createBuildWatchTask(dockerAccess, watcher, mojoParameters, doRestart),
                0, watcher.getInterval(), TimeUnit.MILLISECONDS);
    }

    private void scheduleRestartWatchTask(DockerAccess dockerAccess, ImageWatcher watcher) throws DockerAccessException {
        executor.scheduleAtFixedRate(
                createRestartWatchTask(dockerAccess, watcher),
                0, watcher.getInterval(), TimeUnit.MILLISECONDS);
    }

    private Runnable createBuildWatchTask(final DockerAccess docker, final ImageWatcher watcher,
                                          final MojoParameters mojoParameters, final boolean doRestart)
            throws MojoExecutionException {
        final ImageConfiguration imageConfig = watcher.getImageConfiguration();

        final AssemblyFiles files = serviceHub.getBuildService().getAssemblyFiles(imageConfig, mojoParameters);
        return new Runnable() {
            @Override
            public void run() {

                AssemblyConfiguration assemblyConfig = imageConfig.getBuildConfiguration().getAssemblyConfiguration();
                if( assemblyConfig!=null ) {
                    assemblyConfig.skip = true;
                }

                List<AssemblyFiles.Entry> entries = files.getUpdatedEntriesAndRefresh();
                if (entries != null && entries.size() > 0) {
                    try {
                        log.info(imageConfig.getDescription() + ": Assembly changed. Rebuild ...");

                        // TODO: Add an option to copy over single files into the container to known locations
                        // with maybe a post command.
                        buildImage(docker, imageConfig);

                        String name = imageConfig.getName();
                        watcher.setImageId(serviceHub.getQueryService().getImageId(name));
                        if (doRestart) {
                            restartContainer(watcher);
                        }
                        callPostGoal(watcher);
                    } catch (MojoExecutionException | MojoFailureException | IOException e) {
                        log.error(imageConfig.getDescription() + ": Error when rebuilding " + e);
                    }
                }
            }
        };
    }

    private Runnable createRestartWatchTask(final DockerAccess docker,
                                            final ImageWatcher watcher)
            throws DockerAccessException {

        final String imageName = watcher.getImageName();

        return new Runnable() {
            @Override
            public void run() {

                try {
                    String currentImageId = serviceHub.getQueryService().getImageId(imageName);
                    String oldValue = watcher.getAndSetImageId(currentImageId);
                    if (!currentImageId.equals(oldValue)) {
                        restartContainer(watcher);
                        callPostGoal(watcher);
                    }
                } catch (DockerAccessException | MojoFailureException | MojoExecutionException e) {
                    log.warn(watcher.getImageConfiguration().getDescription() + ": Error when restarting image " + e);
                }
            }
        };
    }

    private void restartContainer(ImageWatcher watcher) throws DockerAccessException {
        // Stop old one
        RunService runService = serviceHub.getRunService();
        ImageConfiguration imageConfig = watcher.getImageConfiguration();
        PortMapping mappedPorts = runService.getPortMapping(imageConfig.getRunConfiguration(), project.getProperties());
        String id = watcher.getContainerId();

        String optionalPreStop = getPreStopCommand(imageConfig);
        if (optionalPreStop != null) {
            runService.execInContainer(id, optionalPreStop);
        }
        runService.stopContainer(id, false, false);

        // Start new one
        watcher.setContainerId(runService.createAndStartContainer(imageConfig, mappedPorts, project.getProperties()));
    }

    private String getPreStopCommand(ImageConfiguration imageConfig) {
        if (imageConfig.getRunConfiguration() != null &&
            imageConfig.getRunConfiguration().getWaitConfiguration() != null &&
            imageConfig.getRunConfiguration().getWaitConfiguration().getExec() != null) {
            return imageConfig.getRunConfiguration().getWaitConfiguration().getExec().getPreStop();
        }
        return null;
    }

    private void callPostGoal(ImageWatcher watcher) throws MojoFailureException, MojoExecutionException {
        String postGoal = watcher.getPostGoal();
        if (postGoal != null) {
            serviceHub.getMojoExecutionService().callPluginGoal(postGoal);
        }
    }


    // ===============================================================================================================

    // Helper class for holding state and parameter when watching images
    private class ImageWatcher {

        private final WatchMode mode;
        private final AtomicReference<String> imageIdRef, containerIdRef;
        private final long interval;
        private final ImageConfiguration imageConfig;
        private final String postGoal;

        public ImageWatcher(ImageConfiguration imageConfig, String imageId, String containerIdRef) {
            this.imageConfig = imageConfig;

            this.imageIdRef = new AtomicReference<>(imageId);
            this.containerIdRef = new AtomicReference<>(containerIdRef);

            this.interval = getWatchInterval(imageConfig);
            this.mode = getWatchMode(imageConfig);
            this.postGoal = getPostGoal(imageConfig);
        }

        public String getContainerId() {
            return containerIdRef.get();
        }

        public long getInterval() {
            return interval;
        }

        public String getPostGoal() {
            return postGoal;
        }

        public boolean isBuild() {
            return mode.isBuild();
        }

        public boolean isRun() {
            return mode.isRun();
        }

        public ImageConfiguration getImageConfiguration() {
            return imageConfig;
        }

        public void setImageId(String imageId) {
            imageIdRef.set(imageId);
        }

        public void setContainerId(String containerId) {
            containerIdRef.set(containerId);
        }

        public String getImageName() {
            return imageConfig.getName();
        }

        public String getAndSetImageId(String currentImageId) {
            return imageIdRef.getAndSet(currentImageId);
        }

        // =========================================================

        private int getWatchInterval(ImageConfiguration imageConfig) {
            WatchImageConfiguration watchConfiguration = imageConfig.getWatchConfiguration();
            int interval = watchConfiguration != null ? watchConfiguration.getInterval() : watchInterval;
            return interval < 100 ? 100 : interval;
        }

        private String getPostGoal(ImageConfiguration imageConfig) {
            WatchImageConfiguration watchConfiguration = imageConfig.getWatchConfiguration();
            return watchConfiguration != null ? watchConfiguration.getPostGoal() : watchPostGoal;
        }

        private WatchMode getWatchMode(ImageConfiguration imageConfig) {
            WatchImageConfiguration watchConfig = imageConfig.getWatchConfiguration();
            WatchMode mode = watchConfig != null ? watchConfig.getMode() : null;
            return mode != null ? mode : WatchMojo.this.watchMode;
        }

    }
}
