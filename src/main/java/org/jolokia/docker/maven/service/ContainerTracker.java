package org.jolokia.docker.maven.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jolokia.docker.maven.config.*;

public class ContainerTracker {

    // Map holding associations between started containers and their images via name and aliases
    // Key: Image, Value: Container
    private final Map<String, String> imageToContainerMap = new HashMap<>();

    // Key: Alias, Value: container
    private final Map<String, String> aliasToContainerMap = new HashMap<>();

    // Action to be used when doing a shutdown
    private final Map<String,ContainerShutdownDescriptor> shutdownDescriptorMap = new LinkedHashMap<>();

    /**
     * Register a container to this tracker
     *
     * @param containerId container id to register
     * @param imageConfig configuration of associated image
     */
    public void registerContainer(String containerId, ImageConfiguration imageConfig) {
        shutdownDescriptorMap.put(containerId, new ContainerShutdownDescriptor(imageConfig, containerId));
        updateImageToContainerMapping(imageConfig, containerId);
    }
    
    public ContainerShutdownDescriptor getContainerShutdownDescriptor(String containerId) {
        return shutdownDescriptorMap.get(containerId);
    }
    
    public ContainerShutdownDescriptor removeContainerShutdownDescriptor(String containerId) {
        return shutdownDescriptorMap.remove(containerId);
    }
    
    public String lookupContainer(String lookup) {
        if (aliasToContainerMap.containsKey(lookup)) {
            return aliasToContainerMap.get(lookup);
        }
        return imageToContainerMap.get(lookup);
    }

    public void resetContainers() {
        shutdownDescriptorMap.clear();
    }
    
    public Collection<ContainerShutdownDescriptor> getAllContainerShutdownDescriptors() {
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

        // The image used
        private String image;

        // Alias of the image
        private final String containerId;

        // Description
        private String description;

        // How long to wait after shutdown (in milliseconds)
        private int shutdownGracePeriod;

        // How long to wait after stop to kill container (in seconds)
        private int killGracePeriod;

        private String preStop;

        ContainerShutdownDescriptor(ImageConfiguration imageConfig, String containerId) {
            this.image = imageConfig.getName();
            this.containerId = containerId;
            this.description = imageConfig.getDescription();
            RunImageConfiguration runConfig = imageConfig.getRunConfiguration();
            WaitConfiguration waitConfig = runConfig != null ? runConfig.getWaitConfiguration() : null;
            this.shutdownGracePeriod = waitConfig != null ? waitConfig.getShutdown() : 0;
            this.killGracePeriod = waitConfig != null ? waitConfig.getKill() : 0;

            if (waitConfig != null && waitConfig.getExec() != null) {
                this.preStop = waitConfig.getExec().getPreStop();
            }
        }

        public String getImage() {
            return image;
        }

        public String getContainerId() {
            return containerId;
        }

        public String getDescription() {
            return description;
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
