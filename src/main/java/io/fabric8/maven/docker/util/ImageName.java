package io.fabric8.maven.docker.util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Helper class for parsing docker repository/image names:
 *
 * <ul>
 *     <li>If the first part before the slash contains a "." or a ":" it is considered to be a registry URL</li>
 *     <li>A last part starting with a ":" is considered to be a tag</li>
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

    // Digest
    private String digest;

    // User name
    private String user;

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
    public ImageName(String fullName, String givenTag) {
        if (fullName == null) {
            throw new NullPointerException("Image name must not be null");
        }

        // set digest to null as default
        digest = null;
        // check if digest is part of fullName, if so -> extract it
        if(fullName.contains("@sha256")) { // Of it contains digest
            String[] digestParts = fullName.split("@");
            digest = digestParts[1];
            fullName = digestParts[0];
        }

        // check for tag
        Pattern tagPattern = Pattern.compile("^(.+?)(?::([^:/]+))?$");
        Matcher matcher = tagPattern.matcher(fullName);
        if (!matcher.matches()) {
            throw new IllegalArgumentException(fullName + " is not a proper image name ([registry/][repo][:port]");
        }
        // extract tag if it exists
        tag = givenTag != null ? givenTag : matcher.group(2);
        String rest = matcher.group(1);

        // extract registry, repository, user
        parseComponentsBeforeTag(rest);

        /*
         * set tag to latest if tag AND digest are null
         * if digest is not null but tag is -> leave it!
         *  -> in case of "image_name@sha256" it is not required to get resolved to "latest"
         */
        if (tag == null && digest == null) {
            tag = "latest";
        }

        doValidate();
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

    public String getDigest() {
        return digest;
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
     * any tag (e.g. <code>privateregistry:fabric8io/java</code>)
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
     * (e.g. <code>privateregistry:fabric8io/java:7u53</code>)
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
        String fullName = getNameWithoutTag(optionalRegistry);
        if (tag != null) {
            fullName = fullName +  ":" + tag;
        }
        if(digest != null) {
            fullName = fullName + "@" + digest;
        }
        return fullName;
    }

    /**
     * Get the user (or "project") part of the image name. This is the part after the registry and before
     * the image name
     *
     * @return user part or <code>null</code> if no user is present in the name
     */
    public String getUser() {
        return user;
    }

    /**
     * Get the simple name of the image, which is the repository sans the user parts.
     *
     * @return simple name of the image
     */
    public String getSimpleName() {
        String prefix = user + "/";
        return repository.startsWith(prefix) ? repository.substring(prefix.length()) : repository;
    }

    /**
     * Check whether the given name validates agains the Docker rules for names
     *
     * @param image image name to validate
     * d@throws IllegalArgumentException if the name doesnt validate
     */
    public static void validate(String image) {
        // Validation will be triggered during construction
        new ImageName(image);
    }

    // Validate parts and throw an IllegalArgumentException if a part is not valid
    private void doValidate() {
        List<String> errors = new ArrayList<>();
        // Strip off user from repository name
        String image = user != null ? repository.substring(user.length() + 1) : repository;
        Object[] checks = new Object[] {
            "registry", DOMAIN_REGEXP, registry,
            "image", IMAGE_NAME_REGEXP, image,
            "user", NAME_COMP_REGEXP, user,
            "tag", TAG_REGEXP, tag,
            "digest", DIGEST_REGEXP, digest
        };
        for (int i = 0; i < checks.length; i +=3) {
            String value = (String) checks[i + 2];
            Pattern checkPattern = (Pattern) checks[i + 1];
            if (value != null &&
                !checkPattern.matcher(value).matches()) {
                errors.add(String.format("%s part '%s' doesn't match allowed pattern '%s'",
                                         checks[i], value, checkPattern.pattern()));
            }
        }
        if (errors.size() > 0) {
            StringBuilder buf = new StringBuilder();
            buf.append(String.format("Given Docker name '%s' is invalid:\n", getFullName()));
            for (String error : errors) {
                buf.append(String.format("   * %s\n",error));
            }
            buf.append("See http://bit.ly/docker_image_fmt for more details");
            throw new IllegalArgumentException(buf.toString());
        }
    }

    private void parseComponentsBeforeTag(String rest) {
        String[] parts = rest.split("\\s*/\\s*");
        if (parts.length == 1) {
            registry = null;
            user = null;
            repository = parts[0];
        } else if (parts.length >= 2) {
            if (isRegistry(parts[0])) {
                registry = parts[0];
                if (parts.length > 2) {
                    user = parts[1];
                    repository = joinTail(parts);
                } else {
                    user = null;
                    repository = parts[1];
                }
            } else {
                registry = null;
                user = parts[0];
                repository = rest;
            }
        }
    }

    // ================================================================================================

    // Validations patterns, taken directly from the docker source -->
    // https://github.com/docker/docker/blob/04da4041757370fb6f85510c8977c5a18ddae380/vendor/github.com/docker/distribution/reference/regexp.go
    // https://github.com/docker/docker/blob/04da4041757370fb6f85510c8977c5a18ddae380/vendor/github.com/docker/distribution/reference/reference.go

    // ---------------------------------------------------------------------
    // https://github.com/docker/docker/blob/04da4041757370fb6f85510c8977c5a18ddae380/vendor/github.com/docker/distribution/reference/regexp.go#L18
    private final String nameComponentRegexp = "[a-z0-9]+(?:(?:(?:[._]|__|[-]*)[a-z0-9]+)+)?";

    // https://github.com/docker/docker/blob/04da4041757370fb6f85510c8977c5a18ddae380/vendor/github.com/docker/distribution/reference/regexp.go#L25
    private final String domainComponentRegexp = "(?:[a-zA-Z0-9]|[a-zA-Z0-9][a-zA-Z0-9-]*[a-zA-Z0-9])";

    // ==========================================================

    // https://github.com/docker/docker/blob/04da4041757370fb6f85510c8977c5a18ddae380/vendor/github.com/docker/distribution/reference/regexp.go#L18
    private final Pattern NAME_COMP_REGEXP = Pattern.compile(nameComponentRegexp);

    // https://github.com/docker/docker/blob/04da4041757370fb6f85510c8977c5a18ddae380/vendor/github.com/docker/distribution/reference/regexp.go#L53
    private final Pattern IMAGE_NAME_REGEXP = Pattern.compile(nameComponentRegexp + "(?:(?:/" + nameComponentRegexp + ")+)?");

    // https://github.com/docker/docker/blob/04da4041757370fb6f85510c8977c5a18ddae380/vendor/github.com/docker/distribution/reference/regexp.go#L31
    private final Pattern DOMAIN_REGEXP = Pattern.compile("^" + domainComponentRegexp + "(?:\\." + domainComponentRegexp + ")*(?::[0-9]+)?$");

    // https://github.com/docker/docker/blob/04da4041757370fb6f85510c8977c5a18ddae380/vendor/github.com/docker/distribution/reference/regexp.go#L37
    private final Pattern TAG_REGEXP = Pattern.compile("^[\\w][\\w.-]{0,127}$");

    private final Pattern DIGEST_REGEXP = Pattern.compile("^sha256:[a-z0-9]{32,}$");
}
