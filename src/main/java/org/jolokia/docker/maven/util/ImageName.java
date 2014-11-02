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

    public ImageName(String fullName) {
        Pattern tagPattern = Pattern.compile("^(.+?)(?::([^:/]+))?$");
        Matcher matcher = tagPattern.matcher(fullName);
        if (!matcher.matches()) {
            throw new IllegalArgumentException(fullName + " is not a proper image name ([registry/][repo][:port]");
        }
        tag = matcher.groupCount() > 1 ? matcher.group(2) : null;
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
        } else {
            throw new IllegalArgumentException("Invalid name " + fullName);
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

    public String getRepositoryWithRegistry() {
        if (hasRegistry()) {
            return registry + "/" + repository;
        } else {
            return repository;
        }
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
}
