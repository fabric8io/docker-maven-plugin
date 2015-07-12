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
    private Map<String, String> containerImageNameMap = new HashMap<>();

    // Key: Alias, Value: Image
    private Map<String, String> imageAliasMap = new HashMap<>();

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
        String image = imageAliasMap.containsKey(lookup) ? imageAliasMap.get(lookup) : lookup;
        return containerImageNameMap.get(image);
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
        containerImageNameMap.put(imageConfig.getName(), id);
        if (imageConfig.getAlias() != null) {
            imageAliasMap.put(imageConfig.getAlias(), imageConfig.getName());
        }
    }
}
