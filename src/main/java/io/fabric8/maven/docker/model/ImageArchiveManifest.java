package io.fabric8.maven.docker.model;

import java.util.List;

import com.google.gson.JsonObject;

public interface ImageArchiveManifest {
    /**
     * @return the list of images in the archive.
     */
    List<ImageArchiveManifestEntry> getEntries();

    /**
     * Return the JSON object for the named config
     * @param configName
     * @return
     */
    JsonObject getConfig(String configName);
}
