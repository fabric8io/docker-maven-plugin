package org.jolokia.docker.maven.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jolokia.docker.maven.config.*;
import org.jolokia.docker.maven.util.PomLabel;

public class ContainerTracker {

    // Map holding associations between started containers and their images via name and aliases
    // Key: Image, Value: Container
    private final Map<String, String> imageToContainerMap = new HashMap<>();

    // Key: Alias, Value: container
    private final Map<String, String> aliasToContainerMap = new HashMap<>();

    // Action to be used when doing a shutdown
    private final Map<String, ContainerShutdownDescriptor> shutdownDescriptorMap = new LinkedHashMap<>();

    /**
     * Register a container to this tracker
     *
     * @param containerId container id to register
     * @param imageConfig configuration of associated image
     * @param pomLabel pom label
     */
    public synchronized void registerContainer(String containerId, ImageConfiguration imageConfig, PomLabel pomLabel) {
        shutdownDescriptorMap.put(containerId, new ContainerShutdownDescriptor(imageConfig, containerId, pomLabel));
        updateImageToContainerMapping(imageConfig, containerId);
    }

    public synchronized ContainerShutdownDescriptor getContainerShutdownDescriptor(String containerId) {
        return shutdownDescriptorMap.get(containerId);
    }

    public synchronized ContainerShutdownDescriptor removeContainerShutdownDescriptor(String containerId) {
        return shutdownDescriptorMap.remove(containerId);
    }

    public synchronized String lookupContainer(String lookup) {
        if (aliasToContainerMap.containsKey(lookup)) {
            return aliasToContainerMap.get(lookup);
        }
        return imageToContainerMap.get(lookup);
    }

    public synchronized void resetContainers() {
        shutdownDescriptorMap.clear();
    }

    public synchronized Collection<ContainerShutdownDescriptor> getAllContainerShutdownDescriptors() {
        List<ContainerShutdownDescriptor> descriptors = new ArrayList<>(shutdownDescriptorMap.values());
        Collections.reverse(descriptors);

        return descriptors;
    }

    private void updateImageToContainerMapping(ImageConfiguration imageConfig, String id) {
        // Register name -> containerId and alias -> name
        imageToContainerMap.put(imageConfig.getName(), id);
        if (imageConfig.getAlias() != null) {
            aliasToContainerMap.put(imageConfig.getAlias(), id);
        }
    }

    // =======================================================

    static class ContainerShutdownDescriptor {

        // The image's configuration
        private final ImageConfiguration imageConfig;

        // Alias of the image
        private final String containerId;

        private final PomLabel pomLabel;

        // How long to wait after shutdown (in milliseconds)
        private final int shutdownGracePeriod;

        // How long to wait after stop to kill container (in seconds)
        private final int killGracePeriod;

        // Command to call before stopping container
        private String preStop;

        ContainerShutdownDescriptor(ImageConfiguration imageConfig, String containerId, PomLabel pomLabel) {
            this.imageConfig = imageConfig;
            this.containerId = containerId;
            this.pomLabel = pomLabel;

            RunImageConfiguration runConfig = imageConfig.getRunConfiguration();
            WaitConfiguration waitConfig = runConfig != null ? runConfig.getWaitConfiguration() : null;
            this.shutdownGracePeriod = waitConfig != null ? waitConfig.getShutdown() : 0;
            this.killGracePeriod = waitConfig != null ? waitConfig.getKill() : 0;
            if (waitConfig != null && waitConfig.getExec() != null) {
                this.preStop = waitConfig.getExec().getPreStop();
            }
        }

        public ImageConfiguration getImageConfiguration() {
            return imageConfig;
        }

        public String getImage() {
            return imageConfig.getName();
        }

        public String getContainerId() {
            return containerId;
        }

        public String getDescription() {
            return imageConfig.getDescription();
        }

        public int getShutdownGracePeriod() {
            return shutdownGracePeriod;
        }

        public int getKillGracePeriod() {
            return killGracePeriod;
        }

        public String getPreStop() {
            return preStop;
        }

        public boolean matches(PomLabel pomLabel) {
            /*
             * if the input is null, the calling operation didn't need to track the label and thus couldn't be run in parallel (eg, using
             * the 'watch' goal), so it's an automatic match)
             */
            return (pomLabel == null || pomLabel.matches(this.pomLabel));
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ContainerShutdownDescriptor that = (ContainerShutdownDescriptor) o;

            return containerId.equals(that.containerId);

        }

        @Override
        public int hashCode() {
            return containerId.hashCode();
        }
    }
}
