package io.fabric8.maven.docker.util;
/*
 *
 * Copyright 2016 Roland Huss
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.base.Strings;
import io.fabric8.maven.docker.config.ConfigHelper;
import org.apache.maven.project.MavenProject;

/**
 * Replace placeholders in an image name with certain properties found in the
 * project
 *
 * @author roland
 * @since 07/06/16
 */
public class ImageNameFormatter implements ConfigHelper.NameFormatter {

    private final Map<String, Lookup> lookups;

    private final Date now;

    public ImageNameFormatter(MavenProject project, Date now) {
        this.lookups = new HashMap<>();
        this.now = now;
        initLookups(project);
    }

    // Detect format elements within the name
    private Pattern FORMAT_IDENTIFIER_PATTERN = Pattern.compile("^(.*?)%([^a-zA-Z]*)([a-zA-Z])(.*)$");

    @Override
    public String format(String name) {
        if (name == null) {
            return null;
        }
        StringBuilder ret = new StringBuilder();
        String rest = name;

        while (true) {
            Matcher matcher = FORMAT_IDENTIFIER_PATTERN.matcher(rest);
            if (!matcher.matches()) {
                ret.append(rest);
                return ret.toString();
            }
            ret.append(matcher.group(1));
            ret.append(formatElement(matcher.group(2),matcher.group(3)));
            rest = matcher.group(4);
        }
    }

    // =====================================================================================

    private String formatElement(String options, String what) {
        Lookup lookup = lookups.get(what);
        if (lookup == null) {
            throw new IllegalArgumentException(String.format("No image name format element '%%%s' known", what) );
        }
        String val = lookup.lookup();
        return String.format("%" + (options != null ? options : "") + "s",val);
    }

    // Lookup abstraction
    private interface Lookup {
        String lookup();
    }

    // Lookup classes
    private void initLookups(final MavenProject project) {
        // Sanitized group id
        lookups.put("g", new DefaultUserLookup(project));

        // Sanitized artifact id
        lookups.put("a", new DefaultNameLookup(project));

        // Various ways for adding a version
        lookups.put("v", new DefaultTagLookup(project, DefaultTagLookup.Mode.PLAIN, now));
        lookups.put("t", new DefaultTagLookup(project, DefaultTagLookup.Mode.SNAPSHOT_WITH_TIMESTAMP, now));
        lookups.put("l", new DefaultTagLookup(project, DefaultTagLookup.Mode.SNAPSHOT_LATEST, now));
    }

    // ==============================================================================================

    private static abstract class AbstractLookup implements Lookup {
        protected final MavenProject project;

        private AbstractLookup(MavenProject project) {
            this.project = project;
        }

        protected String getProperty(String key) {
            return project.getProperties().getProperty(key);
        }

    }
    private static class DefaultUserLookup extends AbstractLookup {

        /**
         * Property to lookup for image user which overwrites the calculated default (group).
         * Used with format modifier %g
         */
        private static final String DOCKER_IMAGE_USER = "docker.image.user";

        private DefaultUserLookup(MavenProject project) {
            super(project);
        }

        public String lookup() {
            String user = getProperty(DOCKER_IMAGE_USER);
            if (user != null) {
                return user;
            }
            String groupId = project.getGroupId();
            while (groupId.endsWith(".")) {
                groupId = groupId.substring(0,groupId.length() - 1);
            }
            int idx = groupId.lastIndexOf(".");
            return sanitizeName(groupId.substring(idx != -1 ? idx + 1 : 0));
        }
    }

    private static class DefaultNameLookup extends AbstractLookup {

        private DefaultNameLookup(MavenProject project) {
            super(project);
        }

        public String lookup() {
            return sanitizeName(project.getArtifactId());
        }
    }


    private static class DefaultTagLookup extends AbstractLookup {

        /**
         * Property to lookup for image name which overwrites the calculated default, which is calculated
         * on the project version and depends whether it is a snapshot project or not.
         * Used with format modifier %v
         */
        private static final String DOCKER_IMAGE_TAG = "docker.image.tag";

        // how to resolve the version
        private final Mode mode;

        // timestamp indicating now
        private final Date now;

        private enum Mode {
            PLAIN,
            SNAPSHOT_WITH_TIMESTAMP,
            SNAPSHOT_LATEST
        }

        private DefaultTagLookup(MavenProject project, Mode mode, Date now) {
            super(project);
            this.mode = mode;
            this.now = now;
        }

        public String lookup() {
            String tag = getProperty(DOCKER_IMAGE_TAG);
            if (!Strings.isNullOrEmpty(tag)) {
                return tag;
            }

            tag = project.getVersion();
            if (mode != Mode.PLAIN) {
                if (tag.endsWith("-SNAPSHOT")) {
                    if (mode == Mode.SNAPSHOT_WITH_TIMESTAMP) {
                        tag = "snapshot-" + new SimpleDateFormat("yyMMdd-HHmmss-SSSS").format(now);
                    } else if (mode == Mode.SNAPSHOT_LATEST) {
                        tag = "latest";
                    }
                }
            }
            return tag;
        }
    }

    // ==========================================================================================

    // See also ImageConfiguration#doValidate()
    private static String sanitizeName(String name) {
        StringBuilder ret = new StringBuilder();
        int underscores = 0;
        boolean lastWasADot = false;
        for (char c : name.toCharArray()) {
            if (c == '_') {
                underscores++;
                // Only _ in a row are allowed
                if (underscores <= 2) {
                    ret.append(c);
                }
                continue;
            }

            if (c == '.') {
                // Only one dot in a row is allowed
                if (!lastWasADot) {
                    ret.append(c);
                }
                lastWasADot = true;
                continue;
            }

            underscores = 0;
            lastWasADot = false;
            if (Character.isLetter(c) || Character.isDigit(c) || c == '-') {
                ret.append(c);
            }
        }

        // All characters must be lowercase
        return ret.toString().toLowerCase();
    }
}
