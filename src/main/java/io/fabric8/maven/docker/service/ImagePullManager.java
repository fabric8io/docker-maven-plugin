package io.fabric8.maven.docker.service;

import com.google.gson.JsonObject;

import io.fabric8.maven.docker.config.ImagePullPolicy;
import io.fabric8.maven.docker.util.AutoPullMode;
import io.fabric8.maven.docker.util.GsonBridge;

/**
 * Simple interface for a ImagePullCache manager, to load and persist the cache.
 */
public class ImagePullManager {

    // Key for the previously used image cache
    private static final String CONTEXT_KEY_PREVIOUSLY_PULLED = "CONTEXT_KEY_PREVIOUSLY_PULLED";

    // image pull policy
    private final ImagePullPolicy imagePullPolicy;

    private CacheStore cacheStore;

    public ImagePullManager(CacheStore cacheStore, String imagePullPolicy, String autoPull) {
        this.cacheStore = cacheStore;
        this.imagePullPolicy = createPullPolicy(imagePullPolicy, autoPull);
    }

    ImagePullPolicy getImagePullPolicy() {
        return imagePullPolicy;
    }

    public ImagePullPolicy createPullPolicy(String imagePullPolicy, String autoPull) {
        if (imagePullPolicy != null) {
            return ImagePullPolicy.fromString(imagePullPolicy);
        }
        if (autoPull != null) {
            AutoPullMode autoPullMode = AutoPullMode.fromString(autoPull);
            switch(autoPullMode) {
                case OFF:
                    return ImagePullPolicy.Never;
                case ALWAYS:
                    return ImagePullPolicy.Always;
                case ON:
                case ONCE:
                    return ImagePullPolicy.IfNotPresent;
            }
        }
        return ImagePullPolicy.IfNotPresent;
    }

    public boolean hasAlreadyPulled(String image) {
        return load().has(image);
    }

    public void pulled(String image) {
        save(load().add(image));
    }


    public interface CacheStore {
        String get(String key);

        void put(String key, String value);
    }

    public ImagePullCache load() {

        String pullCacheJson = cacheStore.get(CONTEXT_KEY_PREVIOUSLY_PULLED);

        ImagePullCache cache = new ImagePullCache(pullCacheJson);

        if (pullCacheJson == null) {
            save(cache);
            cacheStore.put(CONTEXT_KEY_PREVIOUSLY_PULLED, cache.toString());
        }
        return cache;
    }

    public void save(ImagePullCache cache) {
        cacheStore.put(CONTEXT_KEY_PREVIOUSLY_PULLED, cache.toString());
    }

    /**
     * Simple serializable cache for holding image names
     *
     * @author roland
     * @since 20/07/16
     */
    class ImagePullCache {

        private JsonObject cache;

        public ImagePullCache() {
            this(null);
        }

        public ImagePullCache(String json) {
            cache = json != null ? GsonBridge.toJsonObject(json) : new JsonObject();
        }

        public boolean has(String imageName) {
            return cache.has(imageName);
        }

        public ImagePullCache add(String image) {
            cache.addProperty(image, Boolean.TRUE);
            return this;
        }

        @Override
        public String toString() {
            return cache.toString();
        }
    }
}
