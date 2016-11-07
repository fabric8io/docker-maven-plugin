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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import io.fabric8.maven.docker.access.DockerAccessException;
import io.fabric8.maven.docker.access.PortMapping;
import io.fabric8.maven.docker.assembly.AssemblyFiles;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.config.WatchImageConfiguration;
import io.fabric8.maven.docker.config.WatchMode;
import io.fabric8.maven.docker.service.*;
import io.fabric8.maven.docker.util.MojoParameters;
import io.fabric8.maven.docker.util.StartOrderResolver;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.util.StringUtils;

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

    // Scheduler
    private ScheduledExecutorService executor;

    @Override
    protected synchronized void executeInternal(ServiceHub hub) throws DockerAccessException,
                                                                       MojoExecutionException {
        // Important to be be a single threaded scheduler since watch jobs must run serialized
        executor = Executors.newSingleThreadScheduledExecutor();

        QueryService queryService = hub.getQueryService();
        RunService runService = hub.getRunService();

        MojoParameters mojoParameters = createMojoParameters();

        try {
            for (StartOrderResolver.Resolvable resolvable : runService.getImagesConfigsInOrder(queryService, getResolvedImages())) {
                final ImageConfiguration imageConfig = (ImageConfiguration) resolvable;

                String imageId = queryService.getImageId(imageConfig.getName());
                String containerId = runService.lookupContainer(imageConfig.getName());

                ImageWatcher watcher = new ImageWatcher(imageConfig, imageId, containerId);
                long interval = watcher.getInterval();

                WatchMode watchMode = watcher.getWatchMode(imageConfig);
                log.info("Watching " + imageConfig.getName() + (watchMode != null ? " using " + watchMode.getDescription() : ""));

                ArrayList<String> tasks = new ArrayList<>();

                if (imageConfig.getBuildConfiguration() != null &&
                    imageConfig.getBuildConfiguration().getAssemblyConfiguration() != null) {
                    if (watcher.isCopy()) {
                        String containerBaseDir = imageConfig.getBuildConfiguration().getAssemblyConfiguration().getTargetDir();
                        schedule(createCopyWatchTask(hub, watcher, mojoParameters, containerBaseDir),interval);
                        tasks.add("copying artifacts");
                    }

                    if (watcher.isBuild()) {
                        schedule(createBuildWatchTask(hub, watcher, mojoParameters, watchMode == WatchMode.both), interval);
                        tasks.add("rebuilding");
                    }
                }

                if (watcher.isRun() && watcher.getContainerId() != null) {
                    schedule(createRestartWatchTask(hub, watcher), interval);
                    tasks.add("restarting");
                }

                if (tasks.size() > 0) {
                    log.info("%s: Watch for %s",imageConfig.getDescription(),StringUtils.join(tasks.toArray()," and "));
                }
            }
            log.info("Waiting ...");
            if (!keepRunning) {
                runService.addShutdownHookForStoppingContainers(keepContainer, removeVolumes, autoCreateCustomNetworks);
            }
            wait();
        } catch (InterruptedException e) {
            log.warn("Interrupted");
        } finally {
            executor.shutdownNow();
        }
    }

    private void schedule(Runnable runnable, long interval) {
        executor.scheduleAtFixedRate(runnable, 0, interval, TimeUnit.MILLISECONDS);
    }

    private Runnable createCopyWatchTask(final ServiceHub hub, final ImageWatcher watcher,
                                         final MojoParameters mojoParameters, final String containerBaseDir) throws MojoExecutionException {
        final ImageConfiguration imageConfig = watcher.getImageConfiguration();
        final ArchiveService archiveService = hub.getArchiveService();
        final AssemblyFiles files = archiveService.getAssemblyFiles(imageConfig, mojoParameters);
        return new Runnable() {
            @Override
            public void run() {
                List<AssemblyFiles.Entry> entries = files.getUpdatedEntriesAndRefresh();
                if (entries != null && entries.size() > 0) {
                    try {
                        log.info("%s: Assembly changed. Copying changed files to container ...", imageConfig.getDescription());

                        File changedFilesArchive = archiveService.createChangedFilesArchive(entries,files.getAssemblyDirectory(),
                                                                                          imageConfig.getName(),mojoParameters);
                        hub.getDockerAccess().copyArchive(watcher.getContainerId(), changedFilesArchive, containerBaseDir);
                        callPostExec(hub.getRunService(), watcher);
                    } catch (MojoExecutionException | IOException e) {
                        log.error("%s: Error when copying files to container %s: %s",
                                  imageConfig.getDescription(),watcher.getContainerId(),e.getMessage());
                    }
                }
            }
        };
    }

    private void callPostExec(RunService runService, ImageWatcher watcher) throws DockerAccessException {
        if (watcher.getPostExec() != null) {
            String containerId = watcher.getContainerId();
            runService.execInContainer(containerId, watcher.getPostExec(), watcher.getImageConfiguration());
        }
    }

    private Runnable createBuildWatchTask(final ServiceHub hub, final ImageWatcher watcher,
                                          final MojoParameters mojoParameters, final boolean doRestart)
        throws MojoExecutionException {
        final ImageConfiguration imageConfig = watcher.getImageConfiguration();
        final AssemblyFiles files = hub.getArchiveService().getAssemblyFiles(imageConfig, mojoParameters);
        if (files.isEmpty()) {
            log.error("No assembly files for %s. Are you sure you invoked together with the `package` goal?", imageConfig.getDescription());
            throw new MojoExecutionException("No files to watch found for " + imageConfig);
        }

        return new Runnable() {
            @Override
            public void run() {
                List<AssemblyFiles.Entry> entries = files.getUpdatedEntriesAndRefresh();
                if (entries != null && entries.size() > 0) {
                    try {
                        log.info("%s: Assembly changed. Rebuild ...", imageConfig.getDescription());

                        buildImage(hub, imageConfig);

                        String name = imageConfig.getName();
                        watcher.setImageId(hub.getQueryService().getImageId(name));
                        if (doRestart) {
                            restartContainer(hub, watcher);
                        }
                        callPostGoal(hub, watcher);
                    } catch (MojoExecutionException | MojoFailureException | IOException e) {
                        log.error("%s: Error when rebuilding - %s",imageConfig.getDescription(),e);
                    }
                }
            }
        };
    }

    private Runnable createRestartWatchTask(final ServiceHub hub,
                                            final ImageWatcher watcher)
            throws DockerAccessException {

        final String imageName = watcher.getImageName();

        return new Runnable() {
            @Override
            public void run() {

                try {
                    String currentImageId = hub.getQueryService().getImageId(imageName);
                    String oldValue = watcher.getAndSetImageId(currentImageId);
                    if (!currentImageId.equals(oldValue)) {
                        restartContainer(hub, watcher);
                        callPostGoal(hub, watcher);
                    }
                } catch (DockerAccessException | MojoFailureException | MojoExecutionException e) {
                    log.warn("%s: Error when restarting image - %s",watcher.getImageConfiguration().getDescription(),e);
                }
            }
        };
    }

    protected void restartContainer(ServiceHub hub, ImageWatcher watcher) throws DockerAccessException, MojoExecutionException, MojoFailureException {
        // Stop old one
        RunService runService = hub.getRunService();
        ImageConfiguration imageConfig = watcher.getImageConfiguration();
        PortMapping mappedPorts = runService.createPortMapping(imageConfig.getRunConfiguration(), project.getProperties());
        String id = watcher.getContainerId();

        String optionalPreStop = getPreStopCommand(imageConfig);
        if (optionalPreStop != null) {
            runService.execInContainer(id, optionalPreStop, watcher.getImageConfiguration());
        }
        runService.stopPreviouslyStartedContainer(id, false, false);

        // Start new one
        watcher.setContainerId(runService.createAndStartContainer(imageConfig, mappedPorts, getPomLabel(), project.getProperties()));
    }

    private String getPreStopCommand(ImageConfiguration imageConfig) {
        if (imageConfig.getRunConfiguration() != null &&
            imageConfig.getRunConfiguration().getWaitConfiguration() != null &&
            imageConfig.getRunConfiguration().getWaitConfiguration().getExec() != null) {
            return imageConfig.getRunConfiguration().getWaitConfiguration().getExec().getPreStop();
        }
        return null;
    }

    private void callPostGoal(ServiceHub hub, ImageWatcher watcher) throws MojoFailureException,
                                                                           MojoExecutionException {
        String postGoal = watcher.getPostGoal();
        if (postGoal != null) {
            hub.getMojoExecutionService().callPluginGoal(postGoal);
        }
    }


    // ===============================================================================================================

    // Helper class for holding state and parameter when watching images
    public class ImageWatcher {

        private final WatchMode mode;
        private final AtomicReference<String> imageIdRef, containerIdRef;
        private final long interval;
        private final ImageConfiguration imageConfig;
        private final String postGoal;
        private String postExec;

        public ImageWatcher(ImageConfiguration imageConfig, String imageId, String containerIdRef) {
            this.imageConfig = imageConfig;

            this.imageIdRef = new AtomicReference<>(imageId);
            this.containerIdRef = new AtomicReference<>(containerIdRef);

            this.interval = getWatchInterval(imageConfig);
            this.mode = getWatchMode(imageConfig);
            this.postGoal = getPostGoal(imageConfig);
            this.postExec = getPostExec(imageConfig);
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

        public boolean isCopy() {
            return mode.isCopy();
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

        public String getPostExec() {
            return postExec;
        }

        // =========================================================

        private int getWatchInterval(ImageConfiguration imageConfig) {
            WatchImageConfiguration watchConfig = imageConfig.getWatchConfiguration();
            int interval = watchConfig != null ? watchConfig.getInterval() : WatchMojo.this.watchInterval;
            return interval < 100 ? 100 : interval;
        }

        private String getPostExec(ImageConfiguration imageConfig) {
            WatchImageConfiguration watchConfig = imageConfig.getWatchConfiguration();
            return watchConfig != null && watchConfig.getPostExec() != null ?
                    watchConfig.getPostExec() : WatchMojo.this.watchPostExec;
        }

        private String getPostGoal(ImageConfiguration imageConfig) {
            WatchImageConfiguration watchConfig = imageConfig.getWatchConfiguration();
            return watchConfig != null && watchConfig.getPostGoal() != null ?
                    watchConfig.getPostGoal() : WatchMojo.this.watchPostGoal;

        }

        private WatchMode getWatchMode(ImageConfiguration imageConfig) {
            WatchImageConfiguration watchConfig = imageConfig.getWatchConfiguration();
            WatchMode mode = watchConfig != null ? watchConfig.getMode() : null;
            return mode != null ? mode : WatchMojo.this.watchMode;
        }
    }
}
