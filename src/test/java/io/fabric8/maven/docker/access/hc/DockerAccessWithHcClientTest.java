package io.fabric8.maven.docker.access.hc;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.fabric8.maven.docker.access.AuthConfig;
import io.fabric8.maven.docker.access.CreateImageOptions;
import io.fabric8.maven.docker.access.DockerAccessException;
import io.fabric8.maven.docker.access.hc.util.ClientBuilder;
import io.fabric8.maven.docker.config.ArchiveCompression;
import io.fabric8.maven.docker.model.Container;
import io.fabric8.maven.docker.model.ContainersListElement;
import io.fabric8.maven.docker.model.Image;
import io.fabric8.maven.docker.model.ImageDetails;
import io.fabric8.maven.docker.util.Logger;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.impl.client.CloseableHttpClient;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static java.net.HttpURLConnection.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class DockerAccessWithHcClientTest {

    private static final String BASE_URL = "tcp://1.2.3.4:2375";

    private AuthConfig authConfig;

    private DockerAccessWithHcClient client;

    private String imageName;

    @Mock
    private ApacheHttpClientDelegate mockDelegate;

    @Mock
    private CloseableHttpClient closeableHttpClient;

    @Mock
    private CloseableHttpResponse mockedResponse;

    @Mock
    private Logger mockLogger;

    private int pushPullRetries;

    private String registry;

    private Exception thrownException;
    private String archiveFile;
    private String filename;
    private ArchiveCompression compression;
    private List<Container> containers;
    private List<Image> images;

    @BeforeEach
    public void setup() throws IOException {
        Mockito.doReturn("{\"ApiVersion\":\"1.40\",\"Os\":\"linux\",\"Arch\":\"amd64\"}")
                .when(mockDelegate).get(BASE_URL + "/version", HTTP_OK);

        client = new DockerAccessWithHcClient(BASE_URL, null, 1, mockLogger) {
            @Override
            ApacheHttpClientDelegate createHttpClient(ClientBuilder builder) {
                return mockDelegate;
            }
        };
    }

    @Test
    void testPushImage_replacementOfExistingOfTheSameTag() throws Exception {
        String image = "test-image";
        String tag = "test-tag";
        String taggedImageName = String.format("%s:%s", image, tag);

        givenAnImageName(taggedImageName);
        givenRegistry("test-registry");
        givenThatGetWillSucceedWithOk();

        whenPushImage();

        thenAlreadyHasImageMessageIsLogged();
        thenImageWasTagged(image, tag);
        thenPushSucceeded(image, tag);
    }

    @Test
    void testPushImage_imageOfTheSameTagDoesNotExist() throws Exception {
        String image = "test-image";
        String tag = "test-tag";
        String taggedImageName = String.format("%s:%s", image, tag);

        givenAnImageName(taggedImageName);
        givenRegistry("test-registry");
        givenThatGetWillSucceedWithNotFound();
        givenThatDeleteWillSucceed();

        whenPushImage();

        thenImageWasTagged(image, tag);
        thenPushSucceeded(image, tag);
    }

    @Test
    void testPushFails_noRetry() throws Exception {
        givenAnImageName("test");
        givenThePushOrPullWillFail(0,false);

        whenPushImage();
        thenImageWasNotPushed();
    }

    @Test
    void testRetryPush() throws Exception {
        givenAnImageName("test");
        givenANumberOfRetries(1);
        givenThePushOrPullWillFail(1, true);

        whenPushImage();
        thenImageWasPushed();
    }

    @Test
    void testRetriesExceeded() throws Exception {
        givenAnImageName("test");
        givenANumberOfRetries(1);
        givenThePushOrPullWillFail(1, false);
        whenPushImage();
        thenImageWasNotPushed();
    }

    @Test
    void testListContainers() throws IOException {
        String containerId1 = UUID.randomUUID().toString().replace("-", "");
        String containerId2 = UUID.randomUUID().toString().replace("-", "");

        givenContainerIdImagePairs(Pair.of(containerId1, "image:tag"), Pair.of(containerId2, "image:tag"));
        whenListContainers();
        thenNoException();
        thenContainerIdImagePairsMatch(Pair.of(containerId1.substring(0, 12), "image:tag"), Pair.of(containerId2.substring(0, 12), "image:tag"));
    }

    @Test
    void testListContainersFail() throws IOException {
        givenTheGetWithoutResponseHandlerWillFail();
        whenListContainers();
        thenContainerListNotReturned();
    }

    @Test
    void testListImages() throws IOException {
        String imageId1 = UUID.randomUUID().toString().replace("-", "");
        String imageId2 = UUID.randomUUID().toString().replace("-", "");

        givenImageIdRepoTagPairs(Pair.of(imageId1, "image:tag1"), Pair.of(imageId2, "image:tag2"));
        whenListImages();
        thenNoException();
        thenImageIdRepoTagPairsMatch(Pair.of(imageId1, "image:tag1"), Pair.of(imageId2, "image:tag2"));
    }

    @Test
    void testListImagesFail() throws IOException {
        givenTheGetWithoutResponseHandlerWillFail();
        whenListImages();
        thenImageListNotReturned();
    }

    @Test
    void testLoadImage() {
        givenAnImageName("test");
        givenArchiveFile("test.tar");
        whenLoadImage();
        thenNoException();
    }

    @Test
    void testPullFailes_noRetry() throws Exception {
        givenAnImageName("test");
        givenThePushOrPullWillFail(0,false);
        whenPullImage();
        thenImageWasNotPulled();
    }

    @Test
    void testRetryPull() throws Exception {
        givenAnImageName("test");
        givenANumberOfRetries(1);
        givenThePushOrPullWillFail(1, true);
        whenPullImage();
        thenImageWasPulled(2);
    }

    @Test
    void testPullRetriesExceeded() throws Exception {
        givenAnImageName("test");
        givenANumberOfRetries(1);
        givenThePushOrPullWillFail(1, false);
        whenPullImage();
        thenImageWasNotPulled();
    }

    @Test
    void testLoadImageFail() throws IOException {
        givenAnImageName("test");
        givenArchiveFile("test.tar");
        givenThePostWillFail();
        whenLoadImage();
        thenImageWasNotLoaded();
    }

    @Test
    void testSaveImage() {
        givenAnImageName("test");
        givenFilename("test.tar");
        givenCompression(ArchiveCompression.none);
        whenSaveImage();
        thenNoException();
    }

    @Test
    void testSaveImageFail() throws IOException {
        givenAnImageName("test");
        givenFilename("test.tar");
        givenCompression(ArchiveCompression.none);
        givenTheGetWillFail();
        whenSaveImage();
        thenImageWasNotSaved();
    }

    @Test
    void testPullImage() throws Exception {
        givenAnImageName("test");
        whenPullImage();
        thenImageWasPulled(1);
    }

    @Test
    void testPullImageThrowsException() throws Exception {
        givenAnImageName("test");
        givenPostCallThrowsException();
        whenPullImage();
        DockerAccessException dae = (DockerAccessException) thrownException;
        Assertions.assertTrue(dae.getMessage().contains("Unable to pull 'test' from registry 'registry'"));
        Assertions.assertTrue(dae.getMessage().contains("Problem with images/create"));
    }

    @Test
    void stripTrailing() {
        Assertions.assertEquals("x", DockerAccessWithHcClient.stripTrailingSlash("x/"));
        Assertions.assertEquals("y", DockerAccessWithHcClient.stripTrailingSlash("y"));
    }

    @Test
    void serverApiVersion() {
        Assertions.assertEquals("1.40", client.getServerApiVersion());
    }

    @Test
    void testRepoTagsIsNull() throws IOException {
        String imageId = "123123";
        ApacheHttpClientDelegate.HttpBodyAndStatus bodyAndStatus = new ApacheHttpClientDelegate.HttpBodyAndStatus(HTTP_OK, "{\"RepoTags\": null}");

        Mockito.doReturn(bodyAndStatus)
                .when(mockDelegate)
                .get(
                        Mockito.eq(BASE_URL + "/v1.40/images/" + imageId + "/json"),
                        Mockito.any(ApacheHttpClientDelegate.BodyAndStatusResponseHandler.class),
                        Mockito.eq(HTTP_OK),
                        Mockito.eq(HTTP_NOT_FOUND)
                );

        List<String> imageTags = client.getImageTags(imageId);
        Assertions.assertTrue(imageTags.isEmpty());
    }

    @Test
    void testNoRepoTagsInInspect() throws IOException {
        String imageId = "123123";
        ApacheHttpClientDelegate.HttpBodyAndStatus bodyAndStatus = new ApacheHttpClientDelegate.HttpBodyAndStatus(HTTP_OK, "{}");

        Mockito.doReturn(bodyAndStatus)
                .when(mockDelegate)
                .get(
                        Mockito.eq(BASE_URL + "/v1.40/images/" + imageId + "/json"),
                        Mockito.any(ApacheHttpClientDelegate.BodyAndStatusResponseHandler.class),
                        Mockito.eq(HTTP_OK),
                        Mockito.eq(HTTP_NOT_FOUND)
                );

        List<String> imageTags = client.getImageTags(imageId);
        Assertions.assertTrue(imageTags.isEmpty());
    }

    private void givenAnImageName(String imageName) {
        this.imageName = imageName;
    }

    private void givenRegistry(String registry) {
        this.registry = registry;
    }

    private void givenANumberOfRetries(int retries) {
        this.pushPullRetries = retries;
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
        for (Pair<String, String> idNamePair : idNamePairs) {
            JsonObject idNameObject = new JsonObject();
            idNameObject.addProperty(ContainersListElement.ID, idNamePair.getLeft());
            idNameObject.addProperty(ContainersListElement.IMAGE, idNamePair.getRight());
            array.add(idNameObject);
        }

        Mockito.doReturn(array.toString()).when(mockDelegate).get(anyString(), Mockito.eq(200));
    }

    private void givenImageIdRepoTagPairs(Pair<String, String>... idRepoTagPairs) throws IOException {
        final JsonArray array = new JsonArray();
        for (Pair<String, String> idNamePair : idRepoTagPairs) {
            JsonObject imageObject = new JsonObject();
            imageObject.addProperty(ImageDetails.ID, idNamePair.getLeft());
            JsonArray repoTags = new JsonArray();
            repoTags.add(idNamePair.getRight());
            imageObject.add(ImageDetails.REPO_TAGS, repoTags);
            array.add(imageObject);
        }

        Mockito.doReturn(array.toString())
                .when(mockDelegate)
                .get(Mockito.anyString(), Mockito.eq(HTTP_OK));
    }

    private void givenThePushWillFailAndEventuallySucceed(final int retries) throws IOException {
        failPost(retries - 1);
    }

    private void givenThePushWillFail(final int failures) throws IOException {
        failPost(failures);
    }

    private void failPost(int retries) throws IOException {
        for (int i = 0; i < retries; ++i) {
            Mockito.doThrow(new HttpResponseException(HTTP_INTERNAL_ERROR, "error-" + i))
                    .when(mockDelegate)
                    .post(Mockito.anyString(), Mockito.isNull(), Mockito.anyMap(), Mockito.any(ResponseHandler.class), Mockito.eq(HTTP_OK));
        }
    }

    private void givenThePostWillFail() throws IOException {
        Mockito.doThrow(new HttpResponseException(HTTP_INTERNAL_ERROR, "error"))
                .when(mockDelegate)
                .post(Mockito.anyString(), Mockito.any(), Mockito.any(ResponseHandler.class), Mockito.eq(HTTP_OK));
    }

    @SuppressWarnings("unchecked")
    private void givenThatGetWillSucceedWithOk() throws IOException {
        Mockito.doReturn(HTTP_OK)
                .when(mockDelegate)
                .get(Mockito.anyString(), Mockito.any(ResponseHandler.class), Mockito.eq(HTTP_OK), Mockito.eq(HTTP_NOT_FOUND));
    }

    @SuppressWarnings("unchecked")
    private void givenThatGetWillSucceedWithNotFound() throws IOException {
        Mockito.doReturn(HTTP_NOT_FOUND)
                .when(mockDelegate)
                .get(Mockito.anyString(), Mockito.any(ResponseHandler.class), Mockito.eq(HTTP_OK), Mockito.eq(HTTP_NOT_FOUND));
    }

    private void givenTheGetWillFail() throws IOException {
        Mockito.doThrow(new HttpResponseException(HTTP_INTERNAL_ERROR, "error"))
                .when(mockDelegate)
                .get(Mockito.anyString(), Mockito.any(ResponseHandler.class), Mockito.eq(HTTP_OK));
    }

    private void givenTheGetWithoutResponseHandlerWillFail() throws IOException {
        Mockito.doThrow(new HttpResponseException(HTTP_INTERNAL_ERROR, "error"))
                .when(mockDelegate)
                .get(Mockito.anyString(), Mockito.eq(HTTP_OK));
    }

    @SuppressWarnings("unchecked")
    private void givenThatDeleteWillSucceed() throws IOException {
        Mockito.doReturn(new ApacheHttpClientDelegate.HttpBodyAndStatus(HTTP_OK, "body"))
                .when(mockDelegate)
                .delete(Mockito.anyString(), Mockito.any(ResponseHandler.class), Mockito.eq(HTTP_OK), Mockito.eq(HTTP_NOT_FOUND));
    }

    private void thenImageWasNotPushed() {
        Assertions.assertNotNull(thrownException);
    }

    private void thenImageWasPushed() {
        Assertions.assertNull(thrownException);
    }

    private void thenPushSucceeded(String imageNameWithoutTag, String tag) throws IOException {
        ArgumentCaptor<String> urlCapture = ArgumentCaptor.forClass(String.class);
        Mockito.verify(mockDelegate)
                .post(urlCapture.capture(), Mockito.isNull(), Mockito.anyMap(), Mockito.any(ResponseHandler.class), Mockito.eq(HTTP_OK));

        String expectedUrl = String.format("%s/v1.40/images/%s%%2F%s/push?force=1&tag=%s", BASE_URL, registry,
                imageNameWithoutTag, tag);
        Assertions.assertEquals(expectedUrl, urlCapture.getValue());
    }

    private void thenAlreadyHasImageMessageIsLogged() throws IOException {
        Mockito.verify(mockLogger).warn("Target image '%s' already exists. Tagging of '%s' will replace existing image",
                getImageNameWithRegistry(), imageName);
    }

    private void thenImageWasTagged(String imageNameWithoutTag, String tag) throws IOException {
        ArgumentCaptor<String> urlCapture = ArgumentCaptor.forClass(String.class);
        Mockito.verify(mockDelegate)
                .post(urlCapture.capture(), Mockito.eq(HTTP_CREATED));

        String expectedUrl = String.format("%s/v1.40/images/%s%%3A%s/tag?force=0&repo=%s%%2F%s&tag=%s", BASE_URL,
                imageNameWithoutTag, tag, registry, imageNameWithoutTag, tag);
        Assertions.assertEquals(expectedUrl, urlCapture.getValue());
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
            client.pushImage(imageName, authConfig, registry, pushPullRetries);
        } catch (Exception e) {
            thrownException = e;
        }
    }
    private void thenImageWasNotPulled() {
        Assertions.assertNotNull(thrownException);
    }

    private void whenPullImage() {
        try {
            client.pullImage("test", null, "registry", new CreateImageOptions().tag("1.1"), pushPullRetries);
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
        Assertions.assertNull(thrownException);
    }

    private void thenImageWasNotLoaded() {
        Assertions.assertNotNull(thrownException);
    }

    private void thenImageWasNotSaved() {
        Assertions.assertNotNull(thrownException);
    }

    private void thenContainerListNotReturned() {
        Assertions.assertNotNull(thrownException);
    }

    private void thenImageListNotReturned() {
        Assertions.assertNotNull(thrownException);
    }

    private void thenContainerIdImagePairsMatch(Pair<String, String>... idNamePairs) {
        Assertions.assertEquals(idNamePairs.length, this.containers.size());
        for (int i = 0; i < idNamePairs.length; ++i) {
            Assertions.assertNotNull(this.containers.get(i));
            Assertions.assertEquals(idNamePairs[i].getLeft(), this.containers.get(i).getId());
            Assertions.assertEquals(idNamePairs[i].getRight(), this.containers.get(i).getImage());
        }
    }

    private void thenImageIdRepoTagPairsMatch(Pair<String, String>... idRepoTagPairs) {
        Assertions.assertEquals(idRepoTagPairs.length, this.images.size());
        for (int i = 0; i < idRepoTagPairs.length; ++i) {
            Assertions.assertNotNull(this.images.get(i));
            Assertions.assertEquals(idRepoTagPairs[i].getLeft(), this.images.get(i).getId());
            Assertions.assertEquals(Collections.singletonList(idRepoTagPairs[i].getRight()), this.images.get(i).getRepoTags());
        }
    }

    private String getImageNameWithRegistry() {
        return registry + "/" + imageName;
    }

    private void givenPostCallThrowsException() throws IOException {
        Mockito.doThrow(new IOException("Problem with images/create"))
                .when(mockDelegate)
                .post(Mockito.anyString(), Mockito.any(), Mockito.anyMap(), Mockito.any(ResponseHandler.class), Mockito.eq(HTTP_OK));
    }

    private void thenImageWasPulled(int pushPullRetries) throws IOException {
        ArgumentCaptor<String> urlCapture = ArgumentCaptor.forClass(String.class);
        Mockito.verify(mockDelegate, times(pushPullRetries))
                .post(urlCapture.capture(), Mockito.isNull(), Mockito.anyMap(), Mockito.any(ResponseHandler.class), Mockito.eq(HTTP_OK));

        String postUrl = urlCapture.getValue();
        Assertions.assertNotNull(postUrl);
        Assertions.assertEquals("tcp://1.2.3.4:2375/v1.40/images/create?tag=1.1", postUrl);
    }

    private void givenThePushOrPullWillFail(final int pushPullRetries, final boolean succeedAtEnd) throws IOException {
        if (pushPullRetries == 1 && succeedAtEnd) {
            Mockito.when(mockDelegate.post(anyString(), isNull(), anyMap(), any(ResponseHandler.class), Mockito.eq(HTTP_OK)))
                .thenThrow(new HttpResponseException(HTTP_INTERNAL_ERROR, "error"))
                .thenReturn(new Object());
        } else if (pushPullRetries == 1) {
            Mockito.when(mockDelegate.post(anyString(), isNull(), anyMap(), any(ResponseHandler.class), Mockito.eq(HTTP_OK)))
                .thenThrow(new HttpResponseException(HTTP_INTERNAL_ERROR, "error"))
                .thenThrow(new HttpResponseException(HTTP_INTERNAL_ERROR, "error"));
        } else {
            Mockito.when(mockDelegate.post(anyString(), isNull(), anyMap(), any(ResponseHandler.class), Mockito.eq(HTTP_OK)))
                .thenThrow(new HttpResponseException(HTTP_INTERNAL_ERROR, "error"));
        }
    }
}
