package io.fabric8.maven.docker.model;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Assert;
import org.junit.Test;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public class ImageArchiveManifestEntryAdapterTest {
    @Test
    public void createFromEmptyJsonObject() {
        ImageArchiveManifestEntryAdapter entry = new ImageArchiveManifestEntryAdapter(new JsonObject());

        Assert.assertNotNull(entry);
        Assert.assertNull(entry.getConfig());
        Assert.assertNull(entry.getId());
        Assert.assertNotNull(entry.getRepoTags());
        Assert.assertTrue(entry.getRepoTags().isEmpty());
        Assert.assertNotNull(entry.getLayers());
        Assert.assertTrue(entry.getLayers().isEmpty());
    }

    @Test
    public void createFromValidJsonObject() {
        JsonObject entryJson = new JsonObject();
        entryJson.addProperty(ImageArchiveManifestEntryAdapter.CONFIG, "image-id-sha256.json");

        JsonArray repoTagsJson = new JsonArray();
        repoTagsJson.add("test/image:latest");
        entryJson.add(ImageArchiveManifestEntryAdapter.REPO_TAGS, repoTagsJson);

        JsonArray layersJson = new JsonArray();
        layersJson.add("layer-id-sha256/layer.tar");
        entryJson.add(ImageArchiveManifestEntryAdapter.LAYERS, layersJson);

        ImageArchiveManifestEntryAdapter entry = new ImageArchiveManifestEntryAdapter(entryJson);

        Assert.assertNotNull(entry);
        Assert.assertEquals("image-id-sha256.json", entry.getConfig());
        Assert.assertEquals("image-id-sha256", entry.getId());
        Assert.assertNotNull(entry.getRepoTags());
        Assert.assertEquals(Collections.singletonList("test/image:latest"), entry.getRepoTags());
        Assert.assertNotNull(entry.getLayers());
        Assert.assertEquals(Collections.singletonList("layer-id-sha256/layer.tar"), entry.getLayers());
    }

    @Test
    public void createFromValidJsonObjectWithAdditionalFields() {
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

        Assert.assertNotNull(entry);
        Assert.assertEquals("image-id-sha256.json", entry.getConfig());
        Assert.assertEquals("image-id-sha256", entry.getId());
        Assert.assertNotNull(entry.getRepoTags());
        Assert.assertEquals(Collections.singletonList("test/image:latest"), entry.getRepoTags());
        Assert.assertNotNull(entry.getLayers());
        Assert.assertEquals(Collections.singletonList("layer-id-sha256/layer.tar"), entry.getLayers());
    }

    @Test
    public void createFromPartlyValidJsonObject() {
        JsonObject entryJson = new JsonObject();

        entryJson.addProperty(ImageArchiveManifestEntryAdapter.CONFIG, "image-id-sha256.json");

        JsonArray repoTagsJson = new JsonArray();
        repoTagsJson.add("test/image:latest");
        entryJson.add(ImageArchiveManifestEntryAdapter.REPO_TAGS, repoTagsJson);

        JsonObject layersJson = new JsonObject();
        layersJson.addProperty("layer1", "layer-id-sha256/layer.tar");
        entryJson.add(ImageArchiveManifestEntryAdapter.LAYERS, layersJson);

        ImageArchiveManifestEntryAdapter entry = new ImageArchiveManifestEntryAdapter(entryJson);

        Assert.assertNotNull(entry);
        Assert.assertEquals("image-id-sha256.json", entry.getConfig());
        Assert.assertEquals("image-id-sha256", entry.getId());
        Assert.assertNotNull(entry.getRepoTags());
        Assert.assertEquals(Collections.singletonList("test/image:latest"), entry.getRepoTags());
        Assert.assertNotNull(entry.getLayers());
        Assert.assertTrue(entry.getLayers().isEmpty());
    }

}
