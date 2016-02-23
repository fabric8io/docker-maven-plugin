package io.fabric8.maven.docker.service;

import java.util.*;

import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.config.RunImageConfiguration;
import io.fabric8.maven.docker.config.WaitConfiguration;
import io.fabric8.maven.docker.util.PomLabel;

/**
 * Tracker class for tracking started containers so that they can be shut down at the end when
 * <code>docker:start</code> and <code>docker:stop</code> are used in the same run
 */
public class ContainerTracker {

    // Map holding associations between started containers and their images via name and aliases
    // Key: Image, Value: Container
    private final Map<String, String> imageToContainerMap = new HashMap<>();

    // Key: Alias, Value: container
    private final Map<String, String> aliasToContainerMap = new HashMap<>();

    // Maps holding actions to be used when doing a shutdown
    private final Map<String, ContainerShutdownDescriptor> shutdownDescriptorPerContainerMap = new LinkedHashMap<>();
    private final Map<PomLabel,List<ContainerShutdownDescriptor>> shutdownDescriptorPerPomLabelMap = new HashMap<>();

    /**
     * Register a started container to this tracker
     *
     * @param containerId container id to register
     * @param imageConfig configuration of associated image
     * @param pomLabel pom label to identifying the reactor project where the container was created
     */
    public synchronized void registerContainer(String containerId,
                                               ImageConfiguration imageConfig,
                                               PomLabel pomLabel) {
        ContainerShutdownDescriptor descriptor = new ContainerShutdownDescriptor(imageConfig, containerId);
        shutdownDescriptorPerContainerMap.put(containerId, descriptor);
        updatePomLabelMap(pomLabel, descriptor);
        updateImageToContainerMapping(imageConfig, containerId);
    }

    /**
     * Remove a container from this container (if stored) and return its descriptor
     *
     * @param containerId id to remove
     * @return descriptor of the container removed or <code>null</code>
     */
    public synchronized ContainerShutdownDescriptor removeContainer(String containerId) {
        ContainerShutdownDescriptor descriptor = shutdownDescriptorPerContainerMap.remove(containerId);
        if (descriptor != null) {
            removeContainerIdFromLookupMaps(containerId);
            removeDescriptorFromPomLabelMap(descriptor);
        }
        return descriptor;
    }

    /**
     * Lookup a container by name or alias from the tracked containers
     *
     * @param lookup name or alias of the container to lookup
     * @return container id found or <code>null</code>
     */
    public synchronized String lookupContainer(String lookup) {
        if (aliasToContainerMap.containsKey(lookup)) {
            return aliasToContainerMap.get(lookup);
        }
        return imageToContainerMap.get(lookup);
    }

    /**
     * Get all shutdown descriptors for a given pom label and remove it from the tracker. The descriptors
     * are returned in reverse order of their registration.
     *
     * If no pom label is given, then all descriptors are returned.
     *
     * @param pomLabel the label for which to get the descriptors or <code>null</code> for all descriptors
     * @return the descriptors for the given label or an empty collection
     */
    public synchronized Collection<ContainerShutdownDescriptor> removeShutdownDescriptors(PomLabel pomLabel) {
        List<ContainerShutdownDescriptor> descriptors;
        if (pomLabel != null) {
            descriptors = removeFromPomLabelMap(pomLabel);
            removeFromPerContainerMap(descriptors);
        } else {
            // All entries are requested
            descriptors = new ArrayList<>(shutdownDescriptorPerContainerMap.values());
            clearAllMaps();
        }

        Collections.reverse(descriptors);
        return descriptors;
    }

    // ========================================================

    private void updatePomLabelMap(PomLabel pomLabel, ContainerShutdownDescriptor descriptor) {
        if (pomLabel != null) {
            List<ContainerShutdownDescriptor> descList = shutdownDescriptorPerPomLabelMap.get(pomLabel);
            if (descList == null) {
                descList = new ArrayList<>();
                shutdownDescriptorPerPomLabelMap.put(pomLabel,descList);
            }
            descList.add(descriptor);
        }
    }

    private void removeDescriptorFromPomLabelMap(ContainerShutdownDescriptor descriptor) {
        Iterator<Map.Entry<PomLabel, List<ContainerShutdownDescriptor>>> mapIt = shutdownDescriptorPerPomLabelMap.entrySet().iterator();
        while(mapIt.hasNext()) {
            Map.Entry<PomLabel,List<ContainerShutdownDescriptor>> mapEntry = mapIt.next();
            List descs = mapEntry.getValue();
            Iterator<ContainerShutdownDescriptor> it = descs.iterator();
            while (it.hasNext()) {
                ContainerShutdownDescriptor desc = it.next();
                if (descriptor.equals(desc)) {
                    it.remove();
                }
            }
            if (descs.size() == 0) {
                mapIt.remove();
            }
        }
    }

    private void removeContainerIdFromLookupMaps(String containerId) {
        removeValueFromMap(imageToContainerMap,containerId);
        removeValueFromMap(aliasToContainerMap,containerId);
    }

    private void removeValueFromMap(Map<String, String> map, String value) {
        Iterator<Map.Entry<String,String>> it = map.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String,String> entry = it.next();
            if (entry.getValue().equals(value)) {
                it.remove();
            }
        }
    }

    private void updateImageToContainerMapping(ImageConfiguration imageConfig, String id) {
        // Register name -> containerId and alias -> name
        imageToContainerMap.put(imageConfig.getName(), id);
        if (imageConfig.getAlias() != null) {
            aliasToContainerMap.put(imageConfig.getAlias(), id);
        }
    }

    private void removeFromPerContainerMap(List<ContainerShutdownDescriptor> descriptors) {
        Iterator<Map.Entry<String, ContainerShutdownDescriptor>> it = shutdownDescriptorPerContainerMap.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, ContainerShutdownDescriptor> entry = it.next();
            if (descriptors.contains(entry.getValue())) {
                removeContainerIdFromLookupMaps(entry.getKey());
                it.remove();
            }
        }
    }

    private List<ContainerShutdownDescriptor> removeFromPomLabelMap(PomLabel pomLabel) {
        List<ContainerShutdownDescriptor> descriptors;
        descriptors = shutdownDescriptorPerPomLabelMap.remove(pomLabel);
        if (descriptors == null) {
            descriptors = new ArrayList<>();
        } return descriptors;
    }

    private void clearAllMaps() {
        shutdownDescriptorPerContainerMap.clear();
        shutdownDescriptorPerPomLabelMap.clear();
        imageToContainerMap.clear();
        aliasToContainerMap.clear();
    }

    // =======================================================

    static class ContainerShutdownDescriptor {

        // The image's configuration
        private final ImageConfiguration imageConfig;

        // Alias of the image
        private final String containerId;

        // How long to wait after shutdown (in milliseconds)
        private final int shutdownGracePeriod;

        // How long to wait after stop to kill container (in seconds)
        private final int killGracePeriod;

        // Command to call before stopping container
        private String preStop;

        ContainerShutdownDescriptor(ImageConfiguration imageConfig, String containerId) {
            this.imageConfig = imageConfig;
            this.containerId = containerId;

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
