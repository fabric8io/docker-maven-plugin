package io.fabric8.maven.docker.access.hc;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.fabric8.maven.docker.access.AuthConfig;
import io.fabric8.maven.docker.access.hc.util.ClientBuilder;
import io.fabric8.maven.docker.config.ArchiveCompression;
import io.fabric8.maven.docker.model.Container;
import io.fabric8.maven.docker.model.ContainersListElement;
import io.fabric8.maven.docker.model.Image;
import io.fabric8.maven.docker.model.ImageDetails;
import io.fabric8.maven.docker.util.Logger;
import mockit.Expectations;
import mockit.Mocked;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.ResponseHandler;
import org.junit.Before;
import org.junit.Test;

import static java.net.HttpURLConnection.HTTP_INTERNAL_ERROR;
import static org.junit.Assert.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public class DockerAccessWithHcClientTest {

    private AuthConfig authConfig;

    private DockerAccessWithHcClient client;

    private String imageName;

    @Mocked
    private ApacheHttpClientDelegate mockDelegate;

    @Mocked
    private Logger mockLogger;

    private int pushRetries;

    private String registry;

    private Exception thrownException;
    private String archiveFile;
    private String filename;
    private ArchiveCompression compression;
    private List<Container> containers;
    private List<Image> images;

    @Before
    public void setup() throws IOException {
        client = new DockerAccessWithHcClient("tcp://1.2.3.4:2375", null, 1, mockLogger) {
            @Override
            ApacheHttpClientDelegate createHttpClient(ClientBuilder builder) throws IOException {
                return mockDelegate;
            }
        };
    }

    @Test
    public void testPushFailes_noRetry() throws Exception {
        givenAnImageName("test");
        givenThePushWillFail(0);
        whenPushImage();
        thenImageWasNotPushed();
    }

    @Test
    public void testRetryPush() throws Exception {
        givenAnImageName("test");
        givenANumberOfRetries(1);
        givenThePushWillFailAndEventuallySucceed(1);
        whenPushImage();
        thenImageWasPushed();
    }

    @Test
    public void testRetriesExceeded() throws Exception {
        givenAnImageName("test");
        givenANumberOfRetries(1);
        givenThePushWillFail(1);
        whenPushImage();
        thenImageWasNotPushed();
    }

    @Test
    public void testListContainers() throws IOException {
        String containerId1 = UUID.randomUUID().toString().replace("-", "");
        String containerId2 = UUID.randomUUID().toString().replace("-", "");

        givenContainerIdImagePairs(Pair.of(containerId1, "image:tag"), Pair.of(containerId2, "image:tag"));
        whenListContainers();
        thenNoException();
        thenContainerIdImagePairsMatch(Pair.of(containerId1.substring(0, 12), "image:tag"), Pair.of(containerId2.substring(0, 12), "image:tag"));
    }

    @Test
    public void testListContainersFail() throws IOException {
        givenTheGetWithoutResponseHandlerWillFail();
        whenListContainers();
        thenContainerListNotReturned();
    }

    @Test
    public void testListImages() throws IOException {
        String imageId1 = UUID.randomUUID().toString().replace("-", "");
        String imageId2 = UUID.randomUUID().toString().replace("-", "");

        givenImageIdRepoTagPairs(Pair.of(imageId1, "image:tag1"), Pair.of(imageId2, "image:tag2"));
        whenListImages();
        thenNoException();
        thenImageIdRepoTagPairsMatch(Pair.of(imageId1, "image:tag1"), Pair.of(imageId2, "image:tag2"));
    }

    @Test
    public void testListImagesFail() throws IOException {
        givenTheGetWithoutResponseHandlerWillFail();
        whenListImages();
        thenImageListNotReturned();
    }

    @Test
    public void testLoadImage() {
        givenAnImageName("test");
        givenArchiveFile("test.tar");
        whenLoadImage();
        thenNoException();
    }
    @Test
    public void testLoadImageFail() throws IOException {
        givenAnImageName("test");
        givenArchiveFile("test.tar");
        givenThePostWillFail();
        whenLoadImage();
        thenImageWasNotLoaded();
    }

    @Test
    public void testSaveImage() throws IOException {
        givenAnImageName("test");
        givenFilename("test.tar");
        givenCompression(ArchiveCompression.none);
        whenSaveImage();
        thenNoException();
    }

    @Test
    public void testSaveImageFail() throws IOException {
        givenAnImageName("test");
        givenFilename("test.tar");
        givenCompression(ArchiveCompression.none);
        givenTheGetWillFail();
        whenSaveImage();
        thenImageWasNotSaved();
    }

    private void givenAnImageName(String imageName) {
        this.imageName = imageName;
    }

    private void givenANumberOfRetries(int retries) {
        this.pushRetries = retries;
    }

    private void givenArchiveFile(String archiveFile) {
        this.archiveFile = archiveFile;
    }

    private void givenFilename(String filename) {
    	this.filename = filename;
    }

    private void givenCompression(ArchiveCompression compression) {
    	this.compression = compression;
    }

    private void givenContainerIdImagePairs(Pair<String, String>... idNamePairs) throws IOException {
        final JsonArray array = new JsonArray();
        for(Pair<String, String> idNamePair : idNamePairs) {
            JsonObject idNameObject = new JsonObject();
            idNameObject.addProperty(ContainersListElement.ID, idNamePair.getLeft());
            idNameObject.addProperty(ContainersListElement.IMAGE, idNamePair.getRight());
            array.add(idNameObject);
        }

        new Expectations() {{
            mockDelegate.get(anyString, 200);
            result = array.toString();
        }};
    }

    private void givenImageIdRepoTagPairs(Pair<String, String>... idRepoTagPairs) throws IOException {
        final JsonArray array = new JsonArray();
        for(Pair<String, String> idNamePair : idRepoTagPairs) {
            JsonObject imageObject = new JsonObject();
            imageObject.addProperty(ImageDetails.ID, idNamePair.getLeft());
            JsonArray repoTags = new JsonArray();
            repoTags.add(idNamePair.getRight());
            imageObject.add(ImageDetails.REPO_TAGS, repoTags);
            array.add(imageObject);
        }

        new Expectations() {{
            mockDelegate.get(anyString, 200);
            result = array.toString();
        }};
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void givenThePushWillFailAndEventuallySucceed(final int retries) throws IOException {
        new Expectations() {{
            int fail = retries;
            mockDelegate.post(anyString, null, (Map<String, String>) any, (ResponseHandler) any,  200);
            minTimes = fail; maxTimes = fail;
            result = new HttpResponseException(HTTP_INTERNAL_ERROR, "error");
            mockDelegate.post(anyString, null, (Map<String, String>) any, (ResponseHandler) any, 200);
            minTimes = 1; maxTimes = 1;
        }};
    }

    private void givenThePushWillFail(final int retries) throws IOException {
        new Expectations() {{
            int fail = retries + 1;
            mockDelegate.post(anyString, null, (Map<String, String>) any, (ResponseHandler) any,  200);
            minTimes = fail; maxTimes = fail;
            result = new HttpResponseException(HTTP_INTERNAL_ERROR, "error");
        }};
    }

    private void givenThePostWillFail() throws IOException {
        new Expectations() {{
            mockDelegate.post(anyString, any, (ResponseHandler) any, 200);
            result = new HttpResponseException(HTTP_INTERNAL_ERROR, "error");
        }};
    }

    private void givenTheGetWillFail() throws IOException {
        new Expectations() {{
            mockDelegate.get(anyString, (ResponseHandler) any, 200);
            result = new HttpResponseException(HTTP_INTERNAL_ERROR, "error");
        }};
    }

    private void givenTheGetWithoutResponseHandlerWillFail() throws IOException {
        new Expectations() {{
            mockDelegate.get(anyString, 200);
            result = new HttpResponseException(HTTP_INTERNAL_ERROR, "error");
        }};
    }

    private void thenImageWasNotPushed() {
        assertNotNull(thrownException);
    }

    private void thenImageWasPushed() {
       assertNull(thrownException);
    }

    private void whenListContainers() {
        containers = null;
        try {
            containers = client.listContainers(true);
        } catch (Exception e) {
            thrownException = e;
        }
    }

    private void whenListImages() {
        images = null;
        try {
            images = client.listImages(false);
        } catch (Exception e) {
            thrownException = e;
        }
    }

    private void whenPushImage() {
        try {
            client.pushImage(imageName, authConfig, registry, pushRetries);
        } catch (Exception e) {
            thrownException = e;
        }
    }
    private void whenLoadImage() {
        try {
            client.loadImage(imageName, new File(archiveFile));
        } catch (Exception e) {
            thrownException = e;
        }
    }

    private void whenSaveImage() {
        try {
            client.saveImage(imageName, filename, compression);
        } catch (Exception e) {
            thrownException = e;
        }
    }

    private void thenNoException() {
        assertNull(thrownException);
    }

    private void thenImageWasNotLoaded() {
        assertNotNull(thrownException);
    }

    private void thenImageWasNotSaved() {
        assertNotNull(thrownException);
    }

    private void thenContainerListNotReturned() {
        assertNotNull(thrownException);
    }

    private void thenImageListNotReturned() {
        assertNotNull(thrownException);
    }

    private void thenContainerIdImagePairsMatch(Pair<String, String>... idNamePairs) {
        assertEquals(idNamePairs.length, this.containers.size());
        for (int i = 0; i < idNamePairs.length; ++i) {
            assertNotNull(this.containers.get(i));
            assertEquals(idNamePairs[i].getLeft(), this.containers.get(i).getId());
            assertEquals(idNamePairs[i].getRight(), this.containers.get(i).getImage());
        }
    }

    private void thenImageIdRepoTagPairsMatch(Pair<String, String>... idRepoTagPairs) {
        assertEquals(idRepoTagPairs.length, this.images.size());
        for (int i = 0; i < idRepoTagPairs.length; ++i) {
            assertNotNull(this.images.get(i));
            assertEquals(idRepoTagPairs[i].getLeft(), this.images.get(i).getId());
            assertEquals(Collections.singletonList(idRepoTagPairs[i].getRight()), this.images.get(i).getRepoTags());
        }
    }
}
