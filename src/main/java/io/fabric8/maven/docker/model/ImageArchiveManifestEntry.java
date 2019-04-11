package io.fabric8.maven.docker.model;

import java.util.List;

/**
 * Interface representing an entry in an image archive manifest.
 */
public interface ImageArchiveManifestEntry {
    /**
     * @return the image id for this manifest entry
     */
    String getId();

    /**
     * @return the configuration JSON path for this manifest entry
     */
    String getConfig();

    /**
     * @return the repository tags associated with this manifest entry
     */
    List<String> getRepoTags();

    /**
     * @return the layer archive paths for this manifest entry
     */
    List<String> getLayers();
}
