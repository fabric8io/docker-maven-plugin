package io.fabric8.maven.docker.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public class ImageDetailsTest {

    private Image image;

    private JsonObject json;

    @Before
    public void setup() {
        json = new JsonObject();
    }

    @Test
    public void testImageWithLabels() {
        givenAnImageWithLabels();
        whenCreateImage();
        thenLabelsSizeIs(2);
        thenLabelsContains("key1", "value1");
        thenLabelsContains("key2", "value2");
    }

    private void thenLabelsContains(String key, String value) {
        assertTrue(image.getLabels().containsKey(key));
        assertEquals(value, image.getLabels().get(key));
    }

    private void givenAnImageWithLabels() {
        JsonObject labels = new JsonObject();

        labels.addProperty("key1", "value1");
        labels.addProperty("key2", "value2");

        json.add(ImageDetails.LABELS, labels);
    }

    @Test
    public void testCreateImage() throws Exception {
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
        assertEquals(size, image.getLabels().size());
    }

    private void thenValidateImage() {
        assertEquals("b750fe79269d2ec9a3c593ef05b4332b1d1a02a62b4accb2c21d589ff2f5f2dc", image.getId());
        assertEquals("27cf784147099545", image.getParentId());
        assertEquals(1365714795L, image.getCreated());

        assertEquals(24653L, image.getSize());
        assertEquals(180116135L, image.getVirtualSize());
    }

    private void whenCreateImage() {
        image = new ImageDetails(json);
    }


}
