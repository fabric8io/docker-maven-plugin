package io.fabric8.maven.docker.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Collections;

class ImageArchiveManifestEntryAdapterTest {
    @Test
    void createFromEmptyJsonObject() {
        ImageArchiveManifestEntryAdapter entry = new ImageArchiveManifestEntryAdapter(new JsonObject());

        Assertions.assertNotNull(entry);
        Assertions.assertNull(entry.getConfig());
        Assertions.assertNull(entry.getId());
        Assertions.assertNotNull(entry.getRepoTags());
        Assertions.assertTrue(entry.getRepoTags().isEmpty());
        Assertions.assertNotNull(entry.getLayers());
        Assertions.assertTrue(entry.getLayers().isEmpty());
    }

    @Test
    void createFromValidJsonObject() {
        JsonObject entryJson = new JsonObject();
        entryJson.addProperty(ImageArchiveManifestEntryAdapter.CONFIG, "image-id-sha256.json");

        JsonArray repoTagsJson = new JsonArray();
        repoTagsJson.add("test/image:latest");
        entryJson.add(ImageArchiveManifestEntryAdapter.REPO_TAGS, repoTagsJson);

        JsonArray layersJson = new JsonArray();
        layersJson.add("layer-id-sha256/layer.tar");
        entryJson.add(ImageArchiveManifestEntryAdapter.LAYERS, layersJson);

        ImageArchiveManifestEntryAdapter entry = new ImageArchiveManifestEntryAdapter(entryJson);

        Assertions.assertNotNull(entry);
        Assertions.assertEquals("image-id-sha256.json", entry.getConfig());
        Assertions.assertEquals("image-id-sha256", entry.getId());
        Assertions.assertNotNull(entry.getRepoTags());
        Assertions.assertEquals(Collections.singletonList("test/image:latest"), entry.getRepoTags());
        Assertions.assertNotNull(entry.getLayers());
        Assertions.assertEquals(Collections.singletonList("layer-id-sha256/layer.tar"), entry.getLayers());
    }

    @Test
    void createFromValidJsonObjectWithAdditionalFields() {
        JsonObject entryJson = new JsonObject();
        entryJson.addProperty("Random", "new feature");

        entryJson.addProperty(ImageArchiveManifestEntryAdapter.CONFIG, "image-id-sha256.json");

        JsonArray repoTagsJson = new JsonArray();
        repoTagsJson.add("test/image:latest");
        entryJson.add(ImageArchiveManifestEntryAdapter.REPO_TAGS, repoTagsJson);

        JsonArray layersJson = new JsonArray();
        layersJson.add("layer-id-sha256/layer.tar");
        entryJson.add(ImageArchiveManifestEntryAdapter.LAYERS, layersJson);

        ImageArchiveManifestEntryAdapter entry = new ImageArchiveManifestEntryAdapter(entryJson);

        Assertions.assertNotNull(entry);
        Assertions.assertEquals("image-id-sha256.json", entry.getConfig());
        Assertions.assertEquals("image-id-sha256", entry.getId());
        Assertions.assertNotNull(entry.getRepoTags());
        Assertions.assertEquals(Collections.singletonList("test/image:latest"), entry.getRepoTags());
        Assertions.assertNotNull(entry.getLayers());
        Assertions.assertEquals(Collections.singletonList("layer-id-sha256/layer.tar"), entry.getLayers());
    }

    @Test
    void createFromPartlyValidJsonObject() {
        JsonObject entryJson = new JsonObject();

        entryJson.addProperty(ImageArchiveManifestEntryAdapter.CONFIG, "image-id-sha256.json");

        JsonArray repoTagsJson = new JsonArray();
        repoTagsJson.add("test/image:latest");
        entryJson.add(ImageArchiveManifestEntryAdapter.REPO_TAGS, repoTagsJson);

        JsonObject layersJson = new JsonObject();
        layersJson.addProperty("layer1", "layer-id-sha256/layer.tar");
        entryJson.add(ImageArchiveManifestEntryAdapter.LAYERS, layersJson);

        ImageArchiveManifestEntryAdapter entry = new ImageArchiveManifestEntryAdapter(entryJson);

        Assertions.assertNotNull(entry);
        Assertions.assertEquals("image-id-sha256.json", entry.getConfig());
        Assertions.assertEquals("image-id-sha256", entry.getId());
        Assertions.assertNotNull(entry.getRepoTags());
        Assertions.assertEquals(Collections.singletonList("test/image:latest"), entry.getRepoTags());
        Assertions.assertNotNull(entry.getLayers());
        Assertions.assertTrue(entry.getLayers().isEmpty());
    }

}
