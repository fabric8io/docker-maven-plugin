package io.fabric8.maven.docker.service;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import io.fabric8.maven.docker.access.DockerAccess;
import io.fabric8.maven.docker.access.DockerAccessException;
import io.fabric8.maven.docker.access.PortMapping;
import io.fabric8.maven.docker.assembly.AssemblyFiles;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.config.WatchImageConfiguration;
import io.fabric8.maven.docker.config.WatchMode;
import io.fabric8.maven.docker.util.Logger;
import io.fabric8.maven.docker.util.MojoParameters;
import io.fabric8.maven.docker.util.PomLabel;
import io.fabric8.maven.docker.util.StartOrderResolver;
import io.fabric8.maven.docker.util.Task;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.codehaus.plexus.util.StringUtils;

/**
 *
 */
public class WatchService {

    private final ArchiveService archiveService;
    private final BuildService buildService;
    private final DockerAccess dockerAccess;
    private final MojoExecutionService mojoExecutionService;
    private final QueryService queryService;
    private final RunService runService;
    private final Logger log;

    public WatchService(ArchiveService archiveService, BuildService buildService, DockerAccess dockerAccess, MojoExecutionService mojoExecutionService, QueryService queryService, RunService
            runService, Logger log) {
        this.archiveService = archiveService;
        this.buildService = buildService;
        this.dockerAccess = dockerAccess;
        this.mojoExecutionService = mojoExecutionService;
        this.queryService = queryService;
        this.runService = runService;
        this.log = log;
    }

    public synchronized void watch(WatchContext context, BuildService.BuildContext buildContext, List<ImageConfiguration> images) throws DockerAccessException,
            MojoExecutionException {

        // Important to be be a single threaded scheduler since watch jobs must run serialized
        ScheduledExecutorService executor = null;
        try {
            executor = Executors.newSingleThreadScheduledExecutor();

            for (StartOrderResolver.Resolvable resolvable : runService.getImagesConfigsInOrder(queryService, images)) {
                final ImageConfiguration imageConfig = (ImageConfiguration) resolvable;

                String imageId = queryService.getImageId(imageConfig.getName());
                String containerId = runService.lookupContainer(imageConfig.getName());

                ImageWatcher watcher = new ImageWatcher(imageConfig, imageId, containerId, context);
                long interval = watcher.getInterval();

                WatchMode watchMode = watcher.getWatchMode(imageConfig);
                log.info("Watching " + imageConfig.getName() + (watchMode != null ? " using " + watchMode.getDescription() : ""));

                ArrayList<String> tasks = new ArrayList<>();

                if (imageConfig.getBuildConfiguration() != null &&
                        imageConfig.getBuildConfiguration().getAssemblyConfiguration() != null) {
                    if (watcher.isCopy()) {
                        String containerBaseDir = imageConfig.getBuildConfiguration().getAssemblyConfiguration().getTargetDir();
                        schedule(executor, createCopyWatchTask(watcher, context.getMojoParameters(), containerBaseDir),interval);
                        tasks.add("copying artifacts");
                    }

                    if (watcher.isBuild()) {
                        schedule(executor, createBuildWatchTask(watcher, context.getMojoParameters(), watchMode == WatchMode.both, buildContext), interval);
                        tasks.add("rebuilding");
                    }
                }

                if (watcher.isRun() && watcher.getContainerId() != null) {
                    schedule(executor, createRestartWatchTask(watcher), interval);
                    tasks.add("restarting");
                }

                if (tasks.size() > 0) {
                    log.info("%s: Watch for %s",imageConfig.getDescription(), StringUtils.join(tasks.toArray()," and "));
                }
            }
            log.info("Waiting ...");
            if (!context.isKeepRunning()) {
                runService.addShutdownHookForStoppingContainers(context.isKeepContainer(), context.isRemoveVolumes(), context.isAutoCreateCustomNetworks());
            }
            wait();
        } catch (InterruptedException e) {
            log.warn("Interrupted");
        } finally {
            if (executor != null) {
                executor.shutdownNow();
            }
        }
    }

    private void schedule(ScheduledExecutorService executor, Runnable runnable, long interval) {
        executor.scheduleAtFixedRate(runnable, 0, interval, TimeUnit.MILLISECONDS);
    }

    private Runnable createCopyWatchTask(final ImageWatcher watcher,
                                         final MojoParameters mojoParameters, final String containerBaseDir) throws MojoExecutionException {
        final ImageConfiguration imageConfig = watcher.getImageConfiguration();

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
                        dockerAccess.copyArchive(watcher.getContainerId(), changedFilesArchive, containerBaseDir);
                        callPostExec(watcher);
                    } catch (MojoExecutionException | IOException e) {
                        log.error("%s: Error when copying files to container %s: %s",
                                imageConfig.getDescription(),watcher.getContainerId(),e.getMessage());
                    }
                }
            }
        };
    }

    private void callPostExec(ImageWatcher watcher) throws DockerAccessException {
        if (watcher.getPostExec() != null) {
            String containerId = watcher.getContainerId();
            runService.execInContainer(containerId, watcher.getPostExec(), watcher.getImageConfiguration());
        }
    }

    private Runnable createBuildWatchTask(final ImageWatcher watcher,
                                          final MojoParameters mojoParameters, final boolean doRestart, final BuildService.BuildContext buildContext)
            throws MojoExecutionException {
        final ImageConfiguration imageConfig = watcher.getImageConfiguration();
        final AssemblyFiles files = archiveService.getAssemblyFiles(imageConfig, mojoParameters);
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

                        if (watcher.getWatchContext().getImageCustomizer() != null) {
                            log.info("%s: Customizing the image ...", imageConfig.getDescription());
                            watcher.getWatchContext().getImageCustomizer().execute(imageConfig);
                        }

                        buildService.executeBuildWorkflow(imageConfig, buildContext);

                        String name = imageConfig.getName();
                        watcher.setImageId(queryService.getImageId(name));
                        if (doRestart) {
                            restartContainer(watcher);
                        }
                        callPostGoal(watcher);
                    } catch (MojoExecutionException | MojoFailureException | IOException e) {
                        log.error("%s: Error when rebuilding - %s",imageConfig.getDescription(),e);
                    }
                }
            }
        };
    }

    private Runnable createRestartWatchTask(final ImageWatcher watcher)
            throws DockerAccessException {

        final String imageName = watcher.getImageName();

        return new Runnable() {
            @Override
            public void run() {

                try {
                    String currentImageId = queryService.getImageId(imageName);
                    String oldValue = watcher.getAndSetImageId(currentImageId);
                    if (!currentImageId.equals(oldValue)) {
                        restartContainer(watcher);
                        callPostGoal(watcher);
                    }
                } catch (DockerAccessException | MojoFailureException | MojoExecutionException e) {
                    log.warn("%s: Error when restarting image - %s",watcher.getImageConfiguration().getDescription(),e);
                }
            }
        };
    }

    protected void restartContainer(ImageWatcher watcher) throws DockerAccessException, MojoExecutionException, MojoFailureException {
        Task<ImageWatcher> restarter = watcher.getWatchContext().getContainerRestarter();
        if (restarter == null) {
            restarter = defaultContainerRestartTask();
        }

        // Restart
        restarter.execute(watcher);
    }

    private Task<ImageWatcher> defaultContainerRestartTask() throws DockerAccessException, MojoExecutionException, MojoFailureException {
        return new Task<ImageWatcher>() {
            @Override
            public void execute(ImageWatcher watcher) throws DockerAccessException, MojoExecutionException, MojoFailureException {
                // Stop old one
                ImageConfiguration imageConfig = watcher.getImageConfiguration();
                PortMapping mappedPorts = runService.createPortMapping(imageConfig.getRunConfiguration(), watcher.getWatchContext().getMojoParameters().getProject().getProperties());
                String id = watcher.getContainerId();

                String optionalPreStop = getPreStopCommand(imageConfig);
                if (optionalPreStop != null) {
                    runService.execInContainer(id, optionalPreStop, watcher.getImageConfiguration());
                }
                runService.stopPreviouslyStartedContainer(id, false, false);

                // Start new one
                watcher.setContainerId(runService.createAndStartContainer(imageConfig, mappedPorts, watcher.getWatchContext().getPomLabel(),
                        watcher.getWatchContext().getMojoParameters().getProject().getProperties()));
            }
        };
    }

    private String getPreStopCommand(ImageConfiguration imageConfig) {
        if (imageConfig.getRunConfiguration() != null &&
                imageConfig.getRunConfiguration().getWaitConfiguration() != null &&
                imageConfig.getRunConfiguration().getWaitConfiguration().getExec() != null) {
            return imageConfig.getRunConfiguration().getWaitConfiguration().getExec().getPreStop();
        }
        return null;
    }

    private void callPostGoal(ImageWatcher watcher) throws MojoFailureException,
            MojoExecutionException {
        String postGoal = watcher.getPostGoal();
        if (postGoal != null) {
            mojoExecutionService.callPluginGoal(postGoal);
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
        protected WatchContext watchContext;

        public ImageWatcher(ImageConfiguration imageConfig, String imageId, String containerIdRef, WatchContext watchContext) {
            this.imageConfig = imageConfig;
            this.watchContext = watchContext;
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

        public WatchContext getWatchContext() {
            return watchContext;
        }

        // =========================================================

        private int getWatchInterval(ImageConfiguration imageConfig) {
            WatchImageConfiguration watchConfig = imageConfig.getWatchConfiguration();
            int interval = watchConfig != null ? watchConfig.getInterval() : watchContext.getWatchInterval();
            return interval < 100 ? 100 : interval;
        }

        private String getPostExec(ImageConfiguration imageConfig) {
            WatchImageConfiguration watchConfig = imageConfig.getWatchConfiguration();
            return watchConfig != null && watchConfig.getPostExec() != null ?
                    watchConfig.getPostExec() : watchContext.getWatchPostExec();
        }

        private String getPostGoal(ImageConfiguration imageConfig) {
            WatchImageConfiguration watchConfig = imageConfig.getWatchConfiguration();
            return watchConfig != null && watchConfig.getPostGoal() != null ?
                    watchConfig.getPostGoal() : watchContext.getWatchPostGoal();

        }

        private WatchMode getWatchMode(ImageConfiguration imageConfig) {
            WatchImageConfiguration watchConfig = imageConfig.getWatchConfiguration();
            WatchMode mode = watchConfig != null ? watchConfig.getMode() : null;
            return mode != null ? mode : watchContext.getWatchMode();
        }
    }

    // ===========================================

    public static class WatchContext implements Serializable {

        private MojoParameters mojoParameters;

        private WatchMode watchMode;

        private int watchInterval;

        private boolean keepRunning;

        private String watchPostGoal;

        private String watchPostExec;

        private PomLabel pomLabel;

        private boolean keepContainer;

        private boolean removeVolumes;

        private boolean autoCreateCustomNetworks;

        private Task<ImageConfiguration> imageCustomizer;

        private Task<ImageWatcher> containerRestarter;

        public WatchContext() {}

        public MojoParameters getMojoParameters() {
            return mojoParameters;
        }

        public void setMojoParameters(MojoParameters mojoParameters) {
            this.mojoParameters = mojoParameters;
        }

        public WatchMode getWatchMode() {
            return watchMode;
        }

        public void setWatchMode(WatchMode watchMode) {
            this.watchMode = watchMode;
        }

        public int getWatchInterval() {
            return watchInterval;
        }

        public void setWatchInterval(int watchInterval) {
            this.watchInterval = watchInterval;
        }

        public boolean isKeepRunning() {
            return keepRunning;
        }

        public void setKeepRunning(boolean keepRunning) {
            this.keepRunning = keepRunning;
        }

        public String getWatchPostGoal() {
            return watchPostGoal;
        }

        public void setWatchPostGoal(String watchPostGoal) {
            this.watchPostGoal = watchPostGoal;
        }

        public String getWatchPostExec() {
            return watchPostExec;
        }

        public void setWatchPostExec(String watchPostExec) {
            this.watchPostExec = watchPostExec;
        }

        public boolean isKeepContainer() {
            return keepContainer;
        }

        public void setKeepContainer(boolean keepContainer) {
            this.keepContainer = keepContainer;
        }

        public boolean isRemoveVolumes() {
            return removeVolumes;
        }

        public void setRemoveVolumes(boolean removeVolumes) {
            this.removeVolumes = removeVolumes;
        }

        public boolean isAutoCreateCustomNetworks() {
            return autoCreateCustomNetworks;
        }

        public void setAutoCreateCustomNetworks(boolean autoCreateCustomNetworks) {
            this.autoCreateCustomNetworks = autoCreateCustomNetworks;
        }

        public PomLabel getPomLabel() {
            return pomLabel;
        }

        public void setPomLabel(PomLabel pomLabel) {
            this.pomLabel = pomLabel;
        }

        public Task<ImageConfiguration> getImageCustomizer() {
            return imageCustomizer;
        }

        public void setImageCustomizer(Task<ImageConfiguration> imageCustomizer) {
            this.imageCustomizer = imageCustomizer;
        }

        public Task<ImageWatcher> getContainerRestarter() {
            return containerRestarter;
        }

        public void setContainerRestarter(Task<ImageWatcher> containerRestarter) {
            this.containerRestarter = containerRestarter;
        }

        // ===========================================

        public static class Builder {

            private WatchContext context = new WatchContext();

            public Builder() {
                this.context = new WatchContext();
            }

            public Builder mojoParameters(MojoParameters mojoParameters) {
                context.setMojoParameters(mojoParameters);
                return this;
            }

            public Builder watchMode(WatchMode watchMode) {
                context.setWatchMode(watchMode);
                return this;
            }

            public Builder watchInterval(int watchInterval) {
                context.setWatchInterval(watchInterval);
                return this;
            }

            public Builder keepRunning(boolean keepRunning) {
                context.setKeepRunning(keepRunning);
                return this;
            }

            public Builder watchPostGoal(String watchPostGoal) {
                context.setWatchPostGoal(watchPostGoal);
                return this;
            }

            public Builder watchPostExec(String watchPostExec) {
                context.setWatchPostExec(watchPostExec);
                return this;
            }

            public Builder pomLabel(PomLabel pomLabel) {
                context.setPomLabel(pomLabel);
                return this;
            }

            public Builder keepContainer(boolean keepContainer) {
                context.setKeepContainer(keepContainer);
                return this;
            }

            public Builder removeVolumes(boolean removeVolumes) {
                context.setRemoveVolumes(removeVolumes);
                return this;
            }

            public Builder imageCustomizer(Task<ImageConfiguration> imageCustomizer) {
                context.setImageCustomizer(imageCustomizer);
                return this;
            }

            public Builder containerRestarter(Task<ImageWatcher> containerRestarter) {
                context.setContainerRestarter(containerRestarter);
                return this;
            }

            public Builder autoCreateCustomNetworks(boolean autoCreateCustomNetworks) {
                context.setAutoCreateCustomNetworks(autoCreateCustomNetworks);
                return this;
            }

            public WatchContext build() {
                return context;
            }

        }
    }

}
