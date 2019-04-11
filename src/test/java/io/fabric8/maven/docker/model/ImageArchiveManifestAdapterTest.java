package io.fabric8.maven.docker.model;

import org.junit.Assert;
import org.junit.Test;
import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

public class ImageArchiveManifestAdapterTest {
    @Test
    public void createFromEmptyJsonArray() {
        ImageArchiveManifest manifest = new ImageArchiveManifestAdapter(new JsonArray());
        Assert.assertNotNull(manifest);
        Assert.assertNotNull(manifest.getEntries());
        Assert.assertTrue("No entries in manifest", manifest.getEntries().isEmpty());
    }

    @Test
    public void createFromJsonArrayNonObject() {
        JsonArray jsonArray = new JsonArray();
        jsonArray.add(false);
        jsonArray.add(new JsonArray());
        jsonArray.add(10);

        ImageArchiveManifest manifest = new ImageArchiveManifestAdapter(jsonArray);
        Assert.assertNotNull(manifest);
        Assert.assertNotNull(manifest.getEntries());
        Assert.assertTrue("No entries in manifest", manifest.getEntries().isEmpty());
    }

    @Test
    public void createFromEmptyJsonObject() {
        ImageArchiveManifest manifest = new ImageArchiveManifestAdapter(new JsonObject());
        Assert.assertNotNull(manifest);
        Assert.assertNotNull(manifest.getEntries());
        Assert.assertTrue("No entries in manifest", manifest.getEntries().isEmpty());
    }

    @Test
    public void createFromJsonNull() {
        ImageArchiveManifest manifest = new ImageArchiveManifestAdapter(JsonNull.INSTANCE);
        Assert.assertNotNull(manifest);
        Assert.assertNotNull(manifest.getEntries());
        Assert.assertTrue("No entries in manifest", manifest.getEntries().isEmpty());
    }

    @Test
    public void createFromArrayOfObject() {
        JsonArray objects = new JsonArray();
        objects.add(new JsonObject());

        ImageArchiveManifest manifest = new ImageArchiveManifestAdapter(objects);
        Assert.assertNotNull(manifest);
        Assert.assertNotNull(manifest.getEntries());
        Assert.assertFalse("Some entries in manifest", manifest.getEntries().isEmpty());

        for(ImageArchiveManifestEntry entry : manifest.getEntries()) {
            Assert.assertNotNull(entry);
        }
    }

    @Test
    public void createFromArrayOfObjects() {
        JsonArray objects = new JsonArray();
        objects.add(new JsonObject());
        objects.add(new JsonObject());
        objects.add(new JsonObject());

        ImageArchiveManifest manifest = new ImageArchiveManifestAdapter(objects);
        Assert.assertNotNull(manifest);
        Assert.assertNotNull(manifest.getEntries());
        Assert.assertFalse("Some entries in manifest", manifest.getEntries().isEmpty());

        for(ImageArchiveManifestEntry entry : manifest.getEntries()) {
            Assert.assertNotNull(entry);
        }
    }

    @Test
    public void createFromArrayOfObjectsAndElements() {
        JsonArray objects = new JsonArray();
        objects.add(new JsonObject());
        objects.add(new JsonArray());
        objects.add(new JsonObject());
        objects.add("ABC");
        objects.add(123);
        objects.add(JsonNull.INSTANCE);

        ImageArchiveManifest manifest = new ImageArchiveManifestAdapter(objects);
        Assert.assertNotNull(manifest);
        Assert.assertNotNull(manifest.getEntries());
        Assert.assertFalse("Some entries in manifest", manifest.getEntries().isEmpty());

        for(ImageArchiveManifestEntry entry : manifest.getEntries()) {
            Assert.assertNotNull(entry);
        }
    }
}
