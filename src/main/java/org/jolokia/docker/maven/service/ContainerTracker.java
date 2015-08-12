package org.jolokia.docker.maven.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jolokia.docker.maven.config.ImageConfiguration;

public class ContainerTracker {

    // Map holding associations between started containers and their images via name and aliases
    // Key: Image, Value: Container
    private final Map<String, String> imageToContainerMap = new HashMap<>();

    // Key: Alias, Value: container
    private final Map<String, String> aliasToContainerMap = new HashMap<>();

    // Action to be used when doing a shutdown
    private final Map<String,ShutdownAction> shutdownActionMap = new LinkedHashMap<>();
    
    public void registerShutdownAction(String id, ImageConfiguration imageConfig) {
        shutdownActionMap.put(id, new ShutdownAction(imageConfig, id));
        updateImageToContainerMapping(imageConfig, id);
    }
    
    public ShutdownAction getShutdownAction(String containerId) {
        return shutdownActionMap.get(containerId);
    }
    
    public ShutdownAction removeShutdownAction(String containerId) {
        return shutdownActionMap.remove(containerId);
    }
    
    public String lookupContainer(String lookup) {
        if (aliasToContainerMap.containsKey(lookup)) {
            return aliasToContainerMap.get(lookup);
        }

        return imageToContainerMap.get(lookup);
    }

    public void resetShutdownActions() {
        shutdownActionMap.clear();
    }
    
    public Collection<ShutdownAction> getAllShutdownActions() {
        List<ShutdownAction> actions = new ArrayList<>(shutdownActionMap.values());
        Collections.reverse(actions);

        return actions;
    }
    
    private void updateImageToContainerMapping(ImageConfiguration imageConfig, String id) {
        // Register name -> containerId and alias -> name
        imageToContainerMap.put(imageConfig.getName(), id);
        if (imageConfig.getAlias() != null) {
            aliasToContainerMap.put(imageConfig.getAlias(), id);
        }
    }
}
