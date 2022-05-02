package io.fabric8.maven.docker.util;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import io.fabric8.maven.docker.model.ImageArchiveManifest;
import io.fabric8.maven.docker.model.ImageArchiveManifestAdapter;
import io.fabric8.maven.docker.model.ImageArchiveManifestEntry;
import io.fabric8.maven.docker.model.ImageArchiveManifestEntryAdapter;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.regex.PatternSyntaxException;
import java.util.zip.GZIPOutputStream;

class ImageArchiveUtilTest {

    @Test
    void readEmptyArchive() throws IOException {
        byte[] emptyTar;

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
            TarArchiveOutputStream tarOutput = new TarArchiveOutputStream(baos)) {
            tarOutput.finish();
            emptyTar = baos.toByteArray();
        }

        ImageArchiveManifest manifest = ImageArchiveUtil.readManifest(new ByteArrayInputStream(emptyTar));
        Assertions.assertNull(manifest);
    }

    @Test
    void readEmptyCompressedArchive() throws IOException {
        byte[] emptyTar;

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
            GZIPOutputStream gzip = new GZIPOutputStream(baos);
            TarArchiveOutputStream tarOutput = new TarArchiveOutputStream(gzip)) {
            tarOutput.finish();
            gzip.finish();
            emptyTar = baos.toByteArray();
        }

        ImageArchiveManifest manifest = ImageArchiveUtil.readManifest(new ByteArrayInputStream(emptyTar));
        Assertions.assertNull(manifest);
    }

    @Test
    void readEmptyArchiveFromStreamWithoutMarkSupport() throws IOException {
        byte[] emptyTar;

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
            TarArchiveOutputStream tarOutput = new TarArchiveOutputStream(baos)) {
            tarOutput.finish();
            emptyTar = baos.toByteArray();
        }

        ImageArchiveManifest manifest = ImageArchiveUtil.readManifest(new ByteArrayInputStream(emptyTar) {
            public boolean markSupported() {
                return false;
            }
        });
        Assertions.assertNull(manifest);
    }

    @Test
    void readEmptyArchiveFromFile(@TempDir Path temporaryFolder) throws IOException {
        File temporaryTar = temporaryFolder.resolve("temp.tar").toFile();

        try (FileOutputStream fileOutput = new FileOutputStream(temporaryTar);
            TarArchiveOutputStream tarOutput = new TarArchiveOutputStream(fileOutput)) {
            tarOutput.finish();
        }

        ImageArchiveManifest manifest = ImageArchiveUtil.readManifest(temporaryTar);
        Assertions.assertNull(manifest);
    }

    @Test
    void readUnrelatedArchive() throws IOException {
        byte[] archiveBytes;

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
            TarArchiveOutputStream tarOutput = new TarArchiveOutputStream(baos)) {
            final byte[] entryData = UUID.randomUUID().toString().getBytes();
            TarArchiveEntry tarEntry = new TarArchiveEntry("unrelated.data");
            tarEntry.setSize(entryData.length);
            tarOutput.putArchiveEntry(tarEntry);
            tarOutput.write(entryData);
            tarOutput.closeArchiveEntry();
            tarOutput.finish();
            archiveBytes = baos.toByteArray();
        }

        ImageArchiveManifest manifest = ImageArchiveUtil.readManifest(new ByteArrayInputStream(archiveBytes));
        Assertions.assertNull(manifest);
    }

    @Test
    void readInvalidManifestInArchive() throws IOException {
        byte[] archiveBytes;

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
            TarArchiveOutputStream tarOutput = new TarArchiveOutputStream(baos)) {
            final byte[] entryData = ("}" + UUID.randomUUID() + "{").getBytes();
            TarArchiveEntry tarEntry = new TarArchiveEntry(ImageArchiveUtil.MANIFEST_JSON);
            tarEntry.setSize(entryData.length);
            tarOutput.putArchiveEntry(tarEntry);
            tarOutput.write(entryData);
            tarOutput.closeArchiveEntry();
            tarOutput.finish();
            archiveBytes = baos.toByteArray();
        }

        InputStream inputStream = new ByteArrayInputStream(archiveBytes);
        Assertions.assertThrows(JsonParseException.class, () -> ImageArchiveUtil.readManifest(inputStream));
    }

    @Test
    void readInvalidJsonInArchive() throws IOException {
        byte[] archiveBytes;

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
            TarArchiveOutputStream tarOutput = new TarArchiveOutputStream(baos)) {
            final byte[] entryData = ("}" + UUID.randomUUID() + "{").getBytes();
            TarArchiveEntry tarEntry = new TarArchiveEntry("not-the-" + ImageArchiveUtil.MANIFEST_JSON);
            tarEntry.setSize(entryData.length);
            tarOutput.putArchiveEntry(tarEntry);
            tarOutput.write(entryData);
            tarOutput.closeArchiveEntry();
            tarOutput.finish();
            archiveBytes = baos.toByteArray();
        }

        ImageArchiveManifest manifest = ImageArchiveUtil.readManifest(new ByteArrayInputStream(archiveBytes));
        Assertions.assertNull(manifest);
    }

    protected JsonArray createBasicManifestJson() {
        JsonObject entryJson = new JsonObject();

        entryJson.addProperty(ImageArchiveManifestEntryAdapter.CONFIG, "image-id-sha256.json");

        JsonArray repoTagsJson = new JsonArray();
        repoTagsJson.add("test/image:latest");
        entryJson.add(ImageArchiveManifestEntryAdapter.REPO_TAGS, repoTagsJson);

        JsonArray layersJson = new JsonArray();
        layersJson.add("layer-id-sha256/layer.tar");
        entryJson.add(ImageArchiveManifestEntryAdapter.LAYERS, layersJson);

        JsonArray manifestJson = new JsonArray();
        manifestJson.add(entryJson);

        return manifestJson;
    }

    @Test
    void readValidArchive() throws IOException {
        final byte[] entryData = new Gson().toJson(createBasicManifestJson()).getBytes(StandardCharsets.UTF_8);
        final byte[] relatedData = new Gson().toJson(new JsonObject()).getBytes(StandardCharsets.UTF_8);

        byte[] archiveBytes;

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
            TarArchiveOutputStream tarOutput = new TarArchiveOutputStream(baos)) {
            TarArchiveEntry tarEntry;

            tarEntry = new TarArchiveEntry("image-id-sha256.json");
            tarEntry.setSize(relatedData.length);
            tarOutput.putArchiveEntry(tarEntry);
            tarOutput.write(relatedData);
            tarOutput.closeArchiveEntry();

            tarEntry = new TarArchiveEntry(ImageArchiveUtil.MANIFEST_JSON);
            tarEntry.setSize(entryData.length);
            tarOutput.putArchiveEntry(tarEntry);
            tarOutput.write(entryData);
            tarOutput.closeArchiveEntry();

            tarOutput.finish();
            archiveBytes = baos.toByteArray();
        }

        ImageArchiveManifest manifest = ImageArchiveUtil.readManifest(new ByteArrayInputStream(archiveBytes));
        Assertions.assertNotNull(manifest);
        Assertions.assertNotNull(manifest.getEntries());
        Assertions.assertFalse(manifest.getEntries().isEmpty());

        ImageArchiveManifestEntry entry = manifest.getEntries().get(0);
        Assertions.assertNotNull(entry);
        Assertions.assertEquals("image-id-sha256.json", entry.getConfig());
        Assertions.assertEquals("image-id-sha256", entry.getId());
        Assertions.assertNotNull(entry.getRepoTags());
        Assertions.assertEquals(Collections.singletonList("test/image:latest"), entry.getRepoTags());
        Assertions.assertNotNull(entry.getLayers());
        Assertions.assertEquals(Collections.singletonList("layer-id-sha256/layer.tar"), entry.getLayers());
    }

    @Test
    void findByRepoTagEmptyManifest() {
        ImageArchiveManifest empty = new ImageArchiveManifestAdapter(new JsonArray());

        Assertions.assertNull(ImageArchiveUtil.findEntryByRepoTag("anything", empty));
        Assertions.assertNull(ImageArchiveUtil.findEntryByRepoTag("anything", null));
        Assertions.assertNull(ImageArchiveUtil.findEntryByRepoTag(null, null));
    }

    @Test
    void findByRepoTagNonEmptyManifest() {
        ImageArchiveManifest nonEmpty = new ImageArchiveManifestAdapter(createBasicManifestJson());

        Assertions.assertNull(ImageArchiveUtil.findEntryByRepoTag("anything", nonEmpty));
        // Prefix
        Assertions.assertNull(ImageArchiveUtil.findEntryByRepoTag("test", nonEmpty));
        // Prefix
        Assertions.assertNull(ImageArchiveUtil.findEntryByRepoTag("test/image", nonEmpty));
    }

    @Test
    void findByRepoTagSuccessfully() {
        ImageArchiveManifest nonEmpty = new ImageArchiveManifestAdapter(createBasicManifestJson());
        ImageArchiveManifestEntry found = ImageArchiveUtil.findEntryByRepoTag("test/image:latest", nonEmpty);

        Assertions.assertNotNull(found);
        Assertions.assertTrue(found.getRepoTags().contains("test/image:latest"));
    }

    @Test
    void findByRepoTagPatternEmptyManifest() {
        ImageArchiveManifest empty = new ImageArchiveManifestAdapter(new JsonArray());

        Assertions.assertNull(ImageArchiveUtil.findEntryByRepoTagPattern(".*", empty));
        Assertions.assertNull(ImageArchiveUtil.findEntryByRepoTagPattern(".*", null));
        Assertions.assertNull(ImageArchiveUtil.findEntryByRepoTagPattern((String) null, null));
    }

    @Test
    void findByRepoTagPatternInvalidPattern() {
        ImageArchiveManifest nonEmpty = new ImageArchiveManifestAdapter(createBasicManifestJson());
        Assertions.assertThrows(PatternSyntaxException.class, () -> ImageArchiveUtil.findEntryByRepoTagPattern("*(?", nonEmpty));
    }

    @Test
    void findByRepoTagPatternNonEmptyManifest() {
        ImageArchiveManifest nonEmpty = new ImageArchiveManifestAdapter(createBasicManifestJson());

        Assertions.assertNull(ImageArchiveUtil.findEntryByRepoTagPattern("does/not:match", nonEmpty));
        // Anchored pattern
        Assertions.assertNull(ImageArchiveUtil.findEntryByRepoTagPattern("^test/image$", nonEmpty));
    }

    @Test
    void findByRepoTagPatternSuccessfully() {
        ImageArchiveManifest nonEmpty = new ImageArchiveManifestAdapter(createBasicManifestJson());
        Pair<String, ImageArchiveManifestEntry> found;

        // Complete match
        found = ImageArchiveUtil.findEntryByRepoTagPattern("test/image:latest", nonEmpty);
        Assertions.assertNotNull(found);
        Assertions.assertEquals("test/image:latest", found.getLeft());
        Assertions.assertNotNull(found.getRight());
        Assertions.assertTrue(found.getRight().getRepoTags().contains("test/image:latest"));

        // Unanchored match
        found = ImageArchiveUtil.findEntryByRepoTagPattern("test/image", nonEmpty);
        Assertions.assertNotNull(found);
        Assertions.assertEquals("test/image:latest", found.getLeft());
        Assertions.assertNotNull(found.getRight());
        Assertions.assertTrue(found.getRight().getRepoTags().contains("test/image:latest"));

        // Initial anchor
        found = ImageArchiveUtil.findEntryByRepoTagPattern("^test/image", nonEmpty);
        Assertions.assertNotNull(found);
        Assertions.assertEquals("test/image:latest", found.getLeft());
        Assertions.assertNotNull(found.getRight());
        Assertions.assertTrue(found.getRight().getRepoTags().contains("test/image:latest"));
    }

    @Test
    void findEntriesByRepoTagPatternEmptyManifest() {
        ImageArchiveManifest empty = new ImageArchiveManifestAdapter(new JsonArray());
        Map<String, ImageArchiveManifestEntry> entries;

        entries = ImageArchiveUtil.findEntriesByRepoTagPattern((String) null, null);
        Assertions.assertNotNull(entries);
        Assertions.assertTrue(entries.isEmpty());

        entries = ImageArchiveUtil.findEntriesByRepoTagPattern(".*", null);
        Assertions.assertNotNull(entries);
        Assertions.assertTrue(entries.isEmpty());

        entries = ImageArchiveUtil.findEntriesByRepoTagPattern((String) null, empty);
        Assertions.assertNotNull(entries);
        Assertions.assertTrue(entries.isEmpty());

        entries = ImageArchiveUtil.findEntriesByRepoTagPattern(".*", empty);
        Assertions.assertNotNull(entries);
        Assertions.assertTrue(entries.isEmpty());
    }

    @Test
    void findEntriesByRepoTagPatternInvalidPattern() {
        ImageArchiveManifest nonEmpty = new ImageArchiveManifestAdapter(createBasicManifestJson());

        Assertions.assertThrows(PatternSyntaxException.class,
            () -> ImageArchiveUtil.findEntryByRepoTagPattern("*(?", nonEmpty));
    }

    @Test
    void findEntriesByRepoTagPatternNonEmptyManifest() {
        ImageArchiveManifest nonEmpty = new ImageArchiveManifestAdapter(createBasicManifestJson());
        Map<String, ImageArchiveManifestEntry> entries;

        entries = ImageArchiveUtil.findEntriesByRepoTagPattern("does/not:match", nonEmpty);
        Assertions.assertNotNull(entries);
        Assertions.assertTrue(entries.isEmpty());

        // Anchored pattern
        entries = ImageArchiveUtil.findEntriesByRepoTagPattern("^test/image$", nonEmpty);
        Assertions.assertNotNull(entries);
        Assertions.assertTrue(entries.isEmpty());
    }

    @Test
    void findEntriesByRepoTagPatternSuccessfully() {
        ImageArchiveManifest nonEmpty = new ImageArchiveManifestAdapter(createBasicManifestJson());
        Map<String, ImageArchiveManifestEntry> entries;

        // Complete match
        entries = ImageArchiveUtil.findEntriesByRepoTagPattern("test/image:latest", nonEmpty);
        Assertions.assertNotNull(entries);
        Assertions.assertNotNull(entries.get("test/image:latest"));
        Assertions.assertTrue(entries.get("test/image:latest").getRepoTags().contains("test/image:latest"));

        // Unanchored match
        entries = ImageArchiveUtil.findEntriesByRepoTagPattern("test/image", nonEmpty);
        Assertions.assertNotNull(entries);
        Assertions.assertNotNull(entries.get("test/image:latest"));
        Assertions.assertTrue(entries.get("test/image:latest").getRepoTags().contains("test/image:latest"));

        // Initial anchor
        entries = ImageArchiveUtil.findEntriesByRepoTagPattern("^test/image", nonEmpty);
        Assertions.assertNotNull(entries);
        Assertions.assertNotNull(entries.get("test/image:latest"));
        Assertions.assertTrue(entries.get("test/image:latest").getRepoTags().contains("test/image:latest"));
    }

    @Test
    void mapEntriesByIdSuccessfully() {
        ImageArchiveManifest nonEmpty = new ImageArchiveManifestAdapter(createBasicManifestJson());
        Map<String, ImageArchiveManifestEntry> entries = ImageArchiveUtil.mapEntriesById(nonEmpty.getEntries());

        Assertions.assertNotNull(entries);
        Assertions.assertEquals(1, entries.size());
        Assertions.assertNotNull(entries.get("image-id-sha256"));
        Assertions.assertTrue(entries.get("image-id-sha256").getRepoTags().contains("test/image:latest"));
    }
}
