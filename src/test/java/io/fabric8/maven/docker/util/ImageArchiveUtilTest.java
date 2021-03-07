package io.fabric8.maven.docker.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.regex.PatternSyntaxException;
import java.util.zip.GZIPOutputStream;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import io.fabric8.maven.docker.model.ImageArchiveManifest;
import io.fabric8.maven.docker.model.ImageArchiveManifestAdapter;
import io.fabric8.maven.docker.model.ImageArchiveManifestEntry;
import io.fabric8.maven.docker.model.ImageArchiveManifestEntryAdapter;

public class ImageArchiveUtilTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void readEmptyArchive() throws IOException {
        byte[] emptyTar;

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
            TarArchiveOutputStream tarOutput = new TarArchiveOutputStream(baos)) {
            tarOutput.finish();
            emptyTar = baos.toByteArray();
        }

        ImageArchiveManifest manifest = ImageArchiveUtil.readManifest(new ByteArrayInputStream(emptyTar));
        Assert.assertNull(manifest);
    }

    @Test
    public void readEmptyCompressedArchive() throws IOException {
        byte[] emptyTar;

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             GZIPOutputStream gzip = new GZIPOutputStream(baos);
             TarArchiveOutputStream tarOutput = new TarArchiveOutputStream(gzip)) {
            tarOutput.finish();
            gzip.finish();
            emptyTar = baos.toByteArray();
        }

        ImageArchiveManifest manifest = ImageArchiveUtil.readManifest(new ByteArrayInputStream(emptyTar));
        Assert.assertNull(manifest);
    }

    @Test
    public void readEmptyArchiveFromStreamWithoutMarkSupport() throws IOException {
        byte[] emptyTar;

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
            TarArchiveOutputStream tarOutput = new TarArchiveOutputStream(baos)) {
            tarOutput.finish();
            emptyTar = baos.toByteArray();
        }

        ImageArchiveManifest manifest = ImageArchiveUtil.readManifest(new ByteArrayInputStream(emptyTar) {
            public boolean markSupported() { return false; }
        });
        Assert.assertNull(manifest);
    }

    @Test
    public void readEmptyArchiveFromFile() throws IOException {
        File temporaryTar = temporaryFolder.newFile();

        try (FileOutputStream fileOutput = new FileOutputStream(temporaryTar);
             TarArchiveOutputStream tarOutput = new TarArchiveOutputStream(fileOutput)) {
            tarOutput.finish();
        }

        ImageArchiveManifest manifest = ImageArchiveUtil.readManifest(temporaryTar);
        Assert.assertNull(manifest);
    }

    @Test
    public void readUnrelatedArchive() throws IOException {
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
        Assert.assertNull(manifest);
    }

    @Test(expected = JsonParseException.class)
    public void readInvalidManifestInArchive() throws IOException {
        byte[] archiveBytes;

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
            TarArchiveOutputStream tarOutput = new TarArchiveOutputStream(baos)) {
            final byte[] entryData = ("}" + UUID.randomUUID().toString() + "{").getBytes();
            TarArchiveEntry tarEntry = new TarArchiveEntry(ImageArchiveUtil.MANIFEST_JSON);
            tarEntry.setSize(entryData.length);
            tarOutput.putArchiveEntry(tarEntry);
            tarOutput.write(entryData);
            tarOutput.closeArchiveEntry();
            tarOutput.finish();
            archiveBytes = baos.toByteArray();
        }

        ImageArchiveManifest manifest = ImageArchiveUtil.readManifest(new ByteArrayInputStream(archiveBytes));
        Assert.assertNull(manifest);
    }

    @Test
    public void readInvalidJsonInArchive() throws IOException {
        byte[] archiveBytes;

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
            TarArchiveOutputStream tarOutput = new TarArchiveOutputStream(baos)) {
            final byte[] entryData = ("}" + UUID.randomUUID().toString() + "{").getBytes();
            TarArchiveEntry tarEntry = new TarArchiveEntry("not-the-" + ImageArchiveUtil.MANIFEST_JSON);
            tarEntry.setSize(entryData.length);
            tarOutput.putArchiveEntry(tarEntry);
            tarOutput.write(entryData);
            tarOutput.closeArchiveEntry();
            tarOutput.finish();
            archiveBytes = baos.toByteArray();
        }

        ImageArchiveManifest manifest = ImageArchiveUtil.readManifest(new ByteArrayInputStream(archiveBytes));
        Assert.assertNull(manifest);
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
    public void readValidArchive() throws IOException {
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
        Assert.assertNotNull(manifest);
        Assert.assertNotNull(manifest.getEntries());
        Assert.assertFalse(manifest.getEntries().isEmpty());

        ImageArchiveManifestEntry entry = manifest.getEntries().get(0);
        Assert.assertNotNull(entry);
        Assert.assertEquals("image-id-sha256.json", entry.getConfig());
        Assert.assertEquals("image-id-sha256", entry.getId());
        Assert.assertNotNull(entry.getRepoTags());
        Assert.assertEquals(Collections.singletonList("test/image:latest"), entry.getRepoTags());
        Assert.assertNotNull(entry.getLayers());
        Assert.assertEquals(Collections.singletonList("layer-id-sha256/layer.tar"), entry.getLayers());
    }

    @Test
    public void findByRepoTagEmptyManifest() {
        ImageArchiveManifest empty = new ImageArchiveManifestAdapter(new JsonArray());

        Assert.assertNull(ImageArchiveUtil.findEntryByRepoTag("anything", empty));
        Assert.assertNull(ImageArchiveUtil.findEntryByRepoTag("anything", null));
        Assert.assertNull(ImageArchiveUtil.findEntryByRepoTag(null, null));
    }

    @Test
    public void findByRepoTagNonEmptyManifest() {
        ImageArchiveManifest nonEmpty = new ImageArchiveManifestAdapter(createBasicManifestJson());

        Assert.assertNull(ImageArchiveUtil.findEntryByRepoTag("anything", nonEmpty));
        // Prefix
        Assert.assertNull(ImageArchiveUtil.findEntryByRepoTag("test", nonEmpty));
        // Prefix
        Assert.assertNull(ImageArchiveUtil.findEntryByRepoTag("test/image", nonEmpty));
    }

    @Test
    public void findByRepoTagSuccessfully() {
        ImageArchiveManifest nonEmpty = new ImageArchiveManifestAdapter(createBasicManifestJson());
        ImageArchiveManifestEntry found = ImageArchiveUtil.findEntryByRepoTag("test/image:latest", nonEmpty);

        Assert.assertNotNull(found);
        Assert.assertTrue(found.getRepoTags().contains("test/image:latest"));
    }

    @Test
    public void findByRepoTagPatternEmptyManifest() {
        ImageArchiveManifest empty = new ImageArchiveManifestAdapter(new JsonArray());

        Assert.assertNull(ImageArchiveUtil.findEntryByRepoTagPattern(".*", empty));
        Assert.assertNull(ImageArchiveUtil.findEntryByRepoTagPattern(".*", null));
        Assert.assertNull(ImageArchiveUtil.findEntryByRepoTagPattern((String)null, null));
    }

    @Test(expected = PatternSyntaxException.class)
    public void findByRepoTagPatternInvalidPattern() {
        ImageArchiveManifest nonEmpty = new ImageArchiveManifestAdapter(createBasicManifestJson());

        Assert.assertNull(ImageArchiveUtil.findEntryByRepoTagPattern("*(?", nonEmpty));
    }

    @Test
    public void findByRepoTagPatternNonEmptyManifest() {
        ImageArchiveManifest nonEmpty = new ImageArchiveManifestAdapter(createBasicManifestJson());

        Assert.assertNull(ImageArchiveUtil.findEntryByRepoTagPattern("does/not:match", nonEmpty));
        // Anchored pattern
        Assert.assertNull(ImageArchiveUtil.findEntryByRepoTagPattern("^test/image$", nonEmpty));
    }

    @Test
    public void findByRepoTagPatternSuccessfully() {
        ImageArchiveManifest nonEmpty = new ImageArchiveManifestAdapter(createBasicManifestJson());
        Pair<String, ImageArchiveManifestEntry> found;

        // Complete match
        found = ImageArchiveUtil.findEntryByRepoTagPattern("test/image:latest", nonEmpty);
        Assert.assertNotNull(found);
        Assert.assertEquals("test/image:latest", found.getLeft());
        Assert.assertNotNull(found.getRight());
        Assert.assertTrue(found.getRight().getRepoTags().contains("test/image:latest"));

        // Unanchored match
        found = ImageArchiveUtil.findEntryByRepoTagPattern("test/image", nonEmpty);
        Assert.assertNotNull(found);
        Assert.assertEquals("test/image:latest", found.getLeft());
        Assert.assertNotNull(found.getRight());
        Assert.assertTrue(found.getRight().getRepoTags().contains("test/image:latest"));

        // Initial anchor
        found = ImageArchiveUtil.findEntryByRepoTagPattern("^test/image", nonEmpty);
        Assert.assertNotNull(found);
        Assert.assertEquals("test/image:latest", found.getLeft());
        Assert.assertNotNull(found.getRight());
        Assert.assertTrue(found.getRight().getRepoTags().contains("test/image:latest"));
    }

    @Test
    public void findEntriesByRepoTagPatternEmptyManifest() {
        ImageArchiveManifest empty = new ImageArchiveManifestAdapter(new JsonArray());
        Map<String, ImageArchiveManifestEntry> entries;

        entries = ImageArchiveUtil.findEntriesByRepoTagPattern((String)null, null);
        Assert.assertNotNull(entries);
        Assert.assertTrue(entries.isEmpty());

        entries = ImageArchiveUtil.findEntriesByRepoTagPattern(".*", null);
        Assert.assertNotNull(entries);
        Assert.assertTrue(entries.isEmpty());

        entries = ImageArchiveUtil.findEntriesByRepoTagPattern((String)null, empty);
        Assert.assertNotNull(entries);
        Assert.assertTrue(entries.isEmpty());

        entries = ImageArchiveUtil.findEntriesByRepoTagPattern(".*", empty);
        Assert.assertNotNull(entries);
        Assert.assertTrue(entries.isEmpty());
    }

    @Test(expected = PatternSyntaxException.class)
    public void findEntriesByRepoTagPatternInvalidPattern() {
        ImageArchiveManifest nonEmpty = new ImageArchiveManifestAdapter(createBasicManifestJson());

        Assert.assertNull(ImageArchiveUtil.findEntryByRepoTagPattern("*(?", nonEmpty));
    }

    @Test
    public void findEntriesByRepoTagPatternNonEmptyManifest() {
        ImageArchiveManifest nonEmpty = new ImageArchiveManifestAdapter(createBasicManifestJson());
        Map<String, ImageArchiveManifestEntry> entries;

        entries = ImageArchiveUtil.findEntriesByRepoTagPattern("does/not:match", nonEmpty);
        Assert.assertNotNull(entries);
        Assert.assertTrue(entries.isEmpty());

        // Anchored pattern
        entries = ImageArchiveUtil.findEntriesByRepoTagPattern("^test/image$", nonEmpty);
        Assert.assertNotNull(entries);
        Assert.assertTrue(entries.isEmpty());
    }

    @Test
    public void findEntriesByRepoTagPatternSuccessfully() {
        ImageArchiveManifest nonEmpty = new ImageArchiveManifestAdapter(createBasicManifestJson());
        Map<String, ImageArchiveManifestEntry> entries;

        // Complete match
        entries = ImageArchiveUtil.findEntriesByRepoTagPattern("test/image:latest", nonEmpty);
        Assert.assertNotNull(entries);
        Assert.assertNotNull(entries.get("test/image:latest"));
        Assert.assertTrue(entries.get("test/image:latest").getRepoTags().contains("test/image:latest"));

        // Unanchored match
        entries = ImageArchiveUtil.findEntriesByRepoTagPattern("test/image", nonEmpty);
        Assert.assertNotNull(entries);
        Assert.assertNotNull(entries.get("test/image:latest"));
        Assert.assertTrue(entries.get("test/image:latest").getRepoTags().contains("test/image:latest"));

        // Initial anchor
        entries = ImageArchiveUtil.findEntriesByRepoTagPattern("^test/image", nonEmpty);
        Assert.assertNotNull(entries);
        Assert.assertNotNull(entries.get("test/image:latest"));
        Assert.assertTrue(entries.get("test/image:latest").getRepoTags().contains("test/image:latest"));
    }

    @Test
    public void mapEntriesByIdSuccessfully() {
        ImageArchiveManifest nonEmpty = new ImageArchiveManifestAdapter(createBasicManifestJson());
        Map<String, ImageArchiveManifestEntry> entries = ImageArchiveUtil.mapEntriesById(nonEmpty.getEntries());

        Assert.assertNotNull(entries);
        Assert.assertEquals(1, entries.size());
        Assert.assertNotNull(entries.get("image-id-sha256"));
        Assert.assertTrue(entries.get("image-id-sha256").getRepoTags().contains("test/image:latest"));
    }
}
