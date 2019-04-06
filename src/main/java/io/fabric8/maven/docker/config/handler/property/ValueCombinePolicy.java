package io.fabric8.maven.docker.config.handler.property;

import org.apache.commons.lang3.StringUtils;

/**
 * Dictates how to combine values from different sources. See {@link PropertyConfigHandler} for details.
 */
public enum ValueCombinePolicy {
    /**
     * The prioritized value fully replaces any other values.
     */
    Replace,

    /**
     * All provided values are merged. This only makes sense for complex types such as lists and maps.
     */
    Merge;

    public static ValueCombinePolicy fromString(String valueCombinePolicy) {
        for (ValueCombinePolicy policy : values()) {
            if (policy.name().equalsIgnoreCase(valueCombinePolicy)) {
                return policy;
            }
        }
        throw new IllegalArgumentException(String.format("No value combine policy %s known. Valid values are: %s",
                valueCombinePolicy, StringUtils.join(values(), ", ")));
    }
}
