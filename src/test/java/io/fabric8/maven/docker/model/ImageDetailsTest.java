package io.fabric8.maven.docker.model;


 

import com.google.gson.JsonNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

class ImageDetailsTest {

    private Image image;

    private JsonObject json;

    @BeforeEach
    void setup() {
        json = new JsonObject();
    }

    @Test
    void testImageWithLabels() {
        givenAnImageWithLabels();
        whenCreateImage();
        thenLabelsSizeIs(2);
        thenLabelsContains("key1", "value1");
        thenLabelsContains("key2", "value2");
    }

    private void thenLabelsContains(String key, String value) {
        Assertions.assertTrue(image.getLabels().containsKey(key));
        Assertions.assertEquals(value, image.getLabels().get(key));
    }

    private void givenAnImageWithLabels() {
        JsonObject labels = new JsonObject();

        labels.addProperty("key1", "value1");
        labels.addProperty("key2", "value2");

        json.add(ImageDetails.LABELS, labels);
    }

    @Test
    void testImageWithRepoTags() {
        givenImageData();
        whenCreateImage();
        thenRepoTagsSizeIs(2);
    }

    @Test
    void testImageWithNullRepoTags() {
        givenAnImageWithNullRepoTags();
        whenCreateImage();
        thenRepoTagsSizeIs(0);
    }

    @Test
    void testImageWitEmptyRepoTags() {
        givenAnImageWithEmptyRepoTags();
        whenCreateImage();
        thenRepoTagsSizeIs(0);
    }

    @Test
    void testImageWitNoRepoTags() {
        whenCreateImage();
        thenRepoTagsSizeIs(0);
    }

    private void givenAnImageWithNullRepoTags() {
        json.add(ImageDetails.REPO_TAGS, JsonNull.INSTANCE);
    }

    private void givenAnImageWithEmptyRepoTags() {
        json.add(ImageDetails.REPO_TAGS, new JsonArray());
    }

    @Test
    void testCreateImage() {
        givenImageData();
        whenCreateImage();
        thenValidateImage();
    }

    private void givenImageData() {
        json.addProperty(ImageDetails.ID, "b750fe79269d2ec9a3c593ef05b4332b1d1a02a62b4accb2c21d589ff2f5f2dc");
        json.addProperty(ImageDetails.PARENT_ID, "27cf784147099545");
        json.addProperty(ImageDetails.CREATED, 1365714795L);

        json.addProperty(ImageDetails.SIZE, 24653L);
        json.addProperty(ImageDetails.VIRTUAL_SIZE, 180116135L);

        JsonArray repoTags = new JsonArray();
        repoTags.add("ubuntu:12.10");
        repoTags.add("ubuntu:quantal");

        json.add(ImageDetails.REPO_TAGS, repoTags);
    }

    private void thenLabelsSizeIs(int size) {
        Assertions.assertEquals(size, image.getLabels().size());
    }

    private void thenRepoTagsSizeIs(int size) {
        Assertions.assertEquals(size, image.getRepoTags().size());
    }

    private void thenValidateImage() {
        Assertions.assertEquals("b750fe79269d2ec9a3c593ef05b4332b1d1a02a62b4accb2c21d589ff2f5f2dc", image.getId());
        Assertions.assertEquals("27cf784147099545", image.getParentId());
        Assertions.assertEquals(1365714795L, image.getCreated());

        Assertions.assertEquals(24653L, image.getSize());
        Assertions.assertEquals(180116135L, image.getVirtualSize());
    }

    private void whenCreateImage() {
        image = new ImageDetails(json);
    }


}
