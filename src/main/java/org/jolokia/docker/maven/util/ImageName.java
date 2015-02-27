package org.jolokia.docker.maven.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Helper class for parsing docker repository/image names:
 *
 * <ul>
 *     <li>If the first part before the slash contains a "." or a ":" it is considered to be a registry URL</li>
 *     <li>A last part starting with with a ":" is considered to be a tag</li>
 *     <li>The rest is considered the repository name (which might be separated via slashes)</li>
 * </ul>
 *
 * Example of valid names:
 *
 * <ul>
 *     <li>consol/tomcat-8.0</li>
 *     <li>consol/tomcat-8.0:8.0.9</li>
 *     <li>docker.consol.de:5000/tomcat-8.0</li>
 *     <li>docker.consol.de:5000/jolokia/tomcat-8.0:8.0.9</li>
 * </ul>
 *
 * @author roland
 * @since 22.07.14
 */
public class ImageName {

    // The repository part of the full image
    private String repository;

    // Registry
    private String registry;

    // Tag name
    private String tag;

    /**
     * Create an image name
     *
     * @param fullName The fullname of the image in Docker format.
     */
    public ImageName(String fullName) {
        this(fullName,null);
    }

    /**
     * Create an image name with a tag. If a tag is provided (i.e. is not null) then this tag is used.
     * Otherwise the tag of the provided name is used (if any).
     *
     * @param fullName The fullname of the image in Docker format. I
     * @param givenTag tag to use. Can be null in which case the tag specified in fullName is used.
     */
    public ImageName(String fullName,String givenTag) {
        if (fullName == null) {
            throw new NullPointerException("Image name must not be null");
        }
        Pattern tagPattern = Pattern.compile("^(.+?)(?::([^:/]+))?$");
        Matcher matcher = tagPattern.matcher(fullName);
        if (!matcher.matches()) {
            throw new IllegalArgumentException(fullName + " is not a proper image name ([registry/][repo][:port]");
        }
        tag = givenTag != null ?
                givenTag :
                matcher.groupCount() > 1 ? matcher.group(2) : null;
        String rest = matcher.group(1);

        String[] parts = rest.split("\\s*/\\s*");
        if (parts.length == 1) {
            registry = null;
            repository = parts[0];
        } else if (parts.length >= 2) {
            if (isRegistry(parts[0])) {
                registry = parts[0];
                repository = joinTail(parts);
            } else {
                registry = null;
                repository = rest;
            }
        }
    }

    public String getRepository() {
        return repository;
    }

    public String getRegistry() {
        return registry;
    }

    public String getTag() {
        return tag;
    }

    public boolean hasRegistry() {
        return registry != null && registry.length() > 0;
    }

    private String joinTail(String[] parts) {
        StringBuilder builder = new StringBuilder();
        for (int i = 1;i < parts.length; i++) {
            builder.append(parts[i]);
            if (i < parts.length - 1) {
                builder.append("/");
            }
        }
        return builder.toString();
    }

    private boolean isRegistry(String part) {
        return part.contains(".") || part.contains(":");
    }

    /**
     * Get the full name of this image, including the registry but without
     * any tag (e.g. <code>privateregistry:rhuss/java</code>)
     *
     * @return full name with the original registry
     */
    public String getNameWithoutTag() {
        return getNameWithoutTag(null);
    }

    /**
     * Get the full name of this image like {@link #getNameWithoutTag()} does, but allow
     * an optional registry. This registry is used when this image does not already
     * contain a registry.
     *
     * @param optionalRegistry optional registry to use when this image does not provide
     *                         a registry. Can be null in which case no optional registry is used*
     * @return full name with original registry (if set) or optional registry (if not <code>null</code>)
     */
    public String getNameWithoutTag(String optionalRegistry) {
        StringBuilder ret = new StringBuilder();
        if (registry != null || optionalRegistry != null) {
            ret.append(registry != null ? registry : optionalRegistry).append("/");
        }
        ret.append(repository);
        return ret.toString();
    }


    /**
     * Get the full name of this image, including the registry and tag
     * (e.g. <code>privateregistry:rhuss/java:7u53</code>)
     *
     * @return full name with the original registry and the original tag given (if any).
     */
    public String getFullName() {
        return getFullName(null);
    }

    /**
     * Get the full name of this image like {@link #getFullName(String)} does, but allow
     * an optional registry. This registry is used when this image does not already
     * contain a registry. If no tag was provided in the initial name, <code>latest</code> is used.
     *
     * @param optionalRegistry optional registry to use when this image does not provide
     *                         a registry. Can be null in which case no optional registry is used*
     * @return full name with original registry (if set) or optional registry (if not <code>null</code>).
     */
    public String getFullName(String optionalRegistry) {
        return getNameWithoutTag(optionalRegistry) + ":" + (tag != null ? tag : "latest");
    }
}
