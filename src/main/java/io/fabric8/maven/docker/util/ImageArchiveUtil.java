package io.fabric8.maven.docker.util;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.CompressorException;
import org.apache.commons.compress.compressors.CompressorStreamFactory;
import org.apache.commons.lang3.tuple.Pair;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;

import io.fabric8.maven.docker.model.ImageArchiveManifest;
import io.fabric8.maven.docker.model.ImageArchiveManifestAdapter;
import io.fabric8.maven.docker.model.ImageArchiveManifestEntry;

/**
 * Helper functions for working with Docker image archives, as produced by
 * the docker:save mojo.
 */
public class ImageArchiveUtil {
    public static final String MANIFEST_JSON = "manifest.json";

    private static InputStream createUncompressedStream(InputStream possiblyCompressed) {
        if(!possiblyCompressed.markSupported()) {
            possiblyCompressed = new BufferedInputStream(possiblyCompressed, 512 * 1000);
        }

        try {
            return new CompressorStreamFactory().createCompressorInputStream(possiblyCompressed);
        } catch(CompressorException e) {
            return possiblyCompressed;
        }
    }

    /**
     * Read the (possibly compressed) image archive provided and return the archive manifest.
     *
     * If there is no manifest found, then null is returned. Incomplete manifests are returned
     * with as much information parsed as possible.
     *
     * @param file
     * @return the parsed manifest, or null if none found.
     * @throws IOException
     * @throws JsonParseException
     */
    public static ImageArchiveManifest readManifest(File file) throws IOException, JsonParseException {
        return readManifest(new FileInputStream(file));
    }


    /**
     * Read the (possibly compressed) image archive stream provided and return the archive manifest.
     *
     * If there is no manifest found, then null is returned. Incomplete manifests are returned
     * with as much information parsed as possible.
     *
     * @param inputStream
     * @return the parsed manifest, or null if none found.
     * @throws IOException
     * @throws JsonParseException
     */
    public static ImageArchiveManifest readManifest(InputStream inputStream) throws IOException, JsonParseException {
        Map<String, JsonParseException> parseExceptions = new LinkedHashMap<>();
        Map<String, JsonElement> parsedEntries = new LinkedHashMap<>();

        try (TarArchiveInputStream tarStream = new TarArchiveInputStream(createUncompressedStream(inputStream))) {
            TarArchiveEntry tarEntry;
            Gson gson = new Gson();

            while((tarEntry = tarStream.getNextTarEntry()) != null) {
                if(tarEntry.isFile() && tarEntry.getName().endsWith(".json")) {
                    try {
                        JsonElement element = gson.fromJson(new InputStreamReader(tarStream, StandardCharsets.UTF_8), JsonElement.class);
                        parsedEntries.put(tarEntry.getName(), element);
                    } catch(JsonParseException exception) {
                        parseExceptions.put(tarEntry.getName(), exception);
                    }
                }
            }
        }

        JsonElement manifestJson = parsedEntries.get(MANIFEST_JSON);
        if(manifestJson == null) {
            JsonParseException parseException = parseExceptions.get(MANIFEST_JSON);
            if(parseException != null) {
                throw parseException;
            }

            return null;
        }

        ImageArchiveManifestAdapter manifest = new ImageArchiveManifestAdapter(manifestJson);

        for(ImageArchiveManifestEntry entry : manifest.getEntries()) {
            JsonElement entryConfigJson = parsedEntries.get(entry.getConfig());
            if(entryConfigJson != null && entryConfigJson.isJsonObject()) {
                manifest.putConfig(entry.getConfig(), entryConfigJson.getAsJsonObject());
            }
        }

        return manifest;
    }

    /**
     * Search the manifest for an entry that has the repository and tag provided.
     *
     * @param repoTag the repository and tag to search (e.g. busybox:latest).
     * @param manifest the manifest to be searched
     * @return the entry found, or null if no match.
     */
    public static ImageArchiveManifestEntry findEntryByRepoTag(String repoTag, ImageArchiveManifest manifest) {
        if(repoTag == null || manifest == null) {
            return null;
        }

        for(ImageArchiveManifestEntry entry : manifest.getEntries()) {
            for(String entryRepoTag : entry.getRepoTags()) {
                if(repoTag.equals(entryRepoTag)) {
                    return entry;
                }
            }
        }

        return null;
    }

    /**
     * Search the manifest for an entry that has a repository and tag matching the provided pattern.
     *
     * @param repoTagPattern the repository and tag to search (e.g. busybox:latest).
     * @param manifest the manifest to be searched
     * @return a pair containing the matched tag and the entry found, or null if no match.
     */
    public static Pair<String, ImageArchiveManifestEntry> findEntryByRepoTagPattern(String repoTagPattern, ImageArchiveManifest manifest) throws PatternSyntaxException {
        return findEntryByRepoTagPattern(repoTagPattern == null ? null : Pattern.compile(repoTagPattern), manifest);
    }

    /**
     * Search the manifest for an entry that has a repository and tag matching the provided pattern.
     *
     * @param repoTagPattern the repository and tag to search (e.g. busybox:latest).
     * @param manifest the manifest to be searched
     * @return a pair containing the matched tag and the entry found, or null if no match.
     */
    public static Pair<String, ImageArchiveManifestEntry> findEntryByRepoTagPattern(Pattern repoTagPattern, ImageArchiveManifest manifest) throws PatternSyntaxException {
        if(repoTagPattern == null || manifest == null) {
            return null;
        }

        Matcher matcher = repoTagPattern.matcher("");

        for(ImageArchiveManifestEntry entry : manifest.getEntries()) {
            for(String entryRepoTag : entry.getRepoTags()) {
                if(matcher.reset(entryRepoTag).find()) {
                    return Pair.of(entryRepoTag, entry);
                }
            }
        }

        return null;
    }

    /**
     * Search the manifest for an entry that has a repository and tag matching the provided pattern.
     *
     * @param repoTagPattern the repository and tag to search (e.g. busybox:latest).
     * @param manifest the manifest to be searched
     * @return a pair containing the matched tag and the entry found, or null if no match.
     */
    public static Map<String, ImageArchiveManifestEntry> findEntriesByRepoTagPattern(String repoTagPattern, ImageArchiveManifest manifest) throws PatternSyntaxException {
        return findEntriesByRepoTagPattern(repoTagPattern == null ? null : Pattern.compile(repoTagPattern), manifest);
    }

    /**
     * Search the manifest for an entry that has a repository and tag matching the provided pattern.
     *
     * @param repoTagPattern the repository and tag to search (e.g. busybox:latest).
     * @param manifest the manifest to be searched
     * @return a pair containing the matched tag and the entry found, or null if no match.
     */
    public static Map<String, ImageArchiveManifestEntry> findEntriesByRepoTagPattern(Pattern repoTagPattern, ImageArchiveManifest manifest) throws PatternSyntaxException {
        Map<String, ImageArchiveManifestEntry> entries = new LinkedHashMap<>();

        if(repoTagPattern == null || manifest == null) {
            return entries;
        }

        Matcher matcher = repoTagPattern.matcher("");

        for(ImageArchiveManifestEntry entry : manifest.getEntries()) {
            for(String entryRepoTag : entry.getRepoTags()) {
                if(matcher.reset(entryRepoTag).find()) {
                    entries.putIfAbsent(entryRepoTag, entry);
                }
            }
        }

        return entries;
    }

    /**
     * Build a map of entries by id from an iterable of entries.
     *
     * @param entries
     * @return a map of entries by id
     */
    public static Map<String, ImageArchiveManifestEntry> mapEntriesById(Iterable<ImageArchiveManifestEntry> entries) {
        Map<String, ImageArchiveManifestEntry> mapped = new LinkedHashMap<>();

        for(ImageArchiveManifestEntry entry : entries) {
            mapped.put(entry.getId(), entry);
        }

        return mapped;
    }
}
