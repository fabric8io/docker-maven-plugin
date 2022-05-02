package io.fabric8.maven.docker.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ImageArchiveManifestAdapterTest {
    @Test
    void createFromEmptyJsonArray() {
        ImageArchiveManifest manifest = new ImageArchiveManifestAdapter(new JsonArray());
        Assertions.assertNotNull(manifest);
        Assertions.assertNotNull(manifest.getEntries());
        Assertions.assertTrue( manifest.getEntries().isEmpty(),"No entries in manifest");
    }

    @Test
    void createFromJsonArrayNonObject() {
        JsonArray jsonArray = new JsonArray();
        jsonArray.add(false);
        jsonArray.add(new JsonArray());
        jsonArray.add(10);

        ImageArchiveManifest manifest = new ImageArchiveManifestAdapter(jsonArray);
        Assertions.assertNotNull(manifest);
        Assertions.assertNotNull(manifest.getEntries());
        Assertions.assertTrue( manifest.getEntries().isEmpty(),"No entries in manifest");
    }

    @Test
    void createFromEmptyJsonObject() {
        ImageArchiveManifest manifest = new ImageArchiveManifestAdapter(new JsonObject());
        Assertions.assertNotNull(manifest);
        Assertions.assertNotNull(manifest.getEntries());
        Assertions.assertTrue( manifest.getEntries().isEmpty(),"No entries in manifest");
    }

    @Test
    void createFromJsonNull() {
        ImageArchiveManifest manifest = new ImageArchiveManifestAdapter(JsonNull.INSTANCE);
        Assertions.assertNotNull(manifest);
        Assertions.assertNotNull(manifest.getEntries());
        Assertions.assertTrue( manifest.getEntries().isEmpty(),"No entries in manifest");
    }

    @Test
    void createFromArrayOfObject() {
        JsonArray objects = new JsonArray();
        objects.add(new JsonObject());

        ImageArchiveManifest manifest = new ImageArchiveManifestAdapter(objects);
        Assertions.assertNotNull(manifest);
        Assertions.assertNotNull(manifest.getEntries());
        Assertions.assertFalse(manifest.getEntries().isEmpty(),"Some entries in manifest");

        for(ImageArchiveManifestEntry entry : manifest.getEntries()) {
            Assertions.assertNotNull(entry);
        }
    }

    @Test
    void createFromArrayOfObjects() {
        JsonArray objects = new JsonArray();
        objects.add(new JsonObject());
        objects.add(new JsonObject());
        objects.add(new JsonObject());

        ImageArchiveManifest manifest = new ImageArchiveManifestAdapter(objects);
        Assertions.assertNotNull(manifest);
        Assertions.assertNotNull(manifest.getEntries());
        Assertions.assertFalse(manifest.getEntries().isEmpty(),"Some entries in manifest");

        for(ImageArchiveManifestEntry entry : manifest.getEntries()) {
            Assertions.assertNotNull(entry);
        }
    }

    @Test
    void createFromArrayOfObjectsAndElements() {
        JsonArray objects = new JsonArray();
        objects.add(new JsonObject());
        objects.add(new JsonArray());
        objects.add(new JsonObject());
        objects.add("ABC");
        objects.add(123);
        objects.add(JsonNull.INSTANCE);

        ImageArchiveManifest manifest = new ImageArchiveManifestAdapter(objects);
        Assertions.assertNotNull(manifest);
        Assertions.assertNotNull(manifest.getEntries());
        Assertions.assertFalse(manifest.getEntries().isEmpty(),"Some entries in manifest");

        for(ImageArchiveManifestEntry entry : manifest.getEntries()) {
            Assertions.assertNotNull(entry);
        }
    }
}
