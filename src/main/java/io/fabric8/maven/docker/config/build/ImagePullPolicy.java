package io.fabric8.maven.docker.config.build;

import org.apache.commons.lang3.StringUtils;

/**
 * @author roland
 * @since 05.11.17
 */
public enum ImagePullPolicy {

    /**
     * Pull always images
     */
    Always,

    /**
     * Pull image only if not present
     */
    IfNotPresent,

    /**
     * Don't auto pull images
     */
    Never;

    public static ImagePullPolicy fromString(String imagePullPolicy) {
        for (ImagePullPolicy policy : values()) {
            if (policy.name().equalsIgnoreCase(imagePullPolicy)) {
                return policy;
            }
        }
        throw new IllegalArgumentException(String.format("No policy %s known. Valid values are: %s",
                                           imagePullPolicy, StringUtils.join(values(), ", ")));
    }
}
