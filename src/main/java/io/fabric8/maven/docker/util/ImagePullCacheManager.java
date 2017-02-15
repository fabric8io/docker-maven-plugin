package io.fabric8.maven.docker.util;

/**
 * Simple interface for a ImagePullCache manager, to load and persist the cache.
 */
public interface ImagePullCacheManager {

    ImagePullCache load();

    void save(ImagePullCache cache);

}
