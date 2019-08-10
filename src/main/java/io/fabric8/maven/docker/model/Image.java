package io.fabric8.maven.docker.model;

import java.util.List;
import java.util.Map;

/**
 * Interface representing an image on the server.
 */
public interface Image {
    /**
     * @return the image ID
     */
    String getId();

    /**
     * @return the image ID, or null if not present
     */
    String getParentId();

    /**
     * @return Image create timestamp
     */
    long getCreated();

    /**
     * @return the image size
     */
    long getSize();

    /**
     * @return the image virtual size
     */
    long getVirtualSize();

    /**
     * @return the labels assigned to the image
     */
    Map<String, String> getLabels();

    /**
     * @return the names associated with the image (formatted as repository:tag)
     */
    List<String> getRepoTags();

    /**
     * @return the digests associated with the image (formatted as repository:tag@sha256:digest)
     */
    List<String> getRepoDigests();
}
