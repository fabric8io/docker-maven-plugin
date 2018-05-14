package io.fabric8.maven.docker.util;/*
 *
 * Copyright 2015 Roland Huss
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

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.maven.plugins.assembly.interpolation.AssemblyInterpolator;
import org.apache.maven.plugins.assembly.io.DefaultAssemblyReader;
import org.codehaus.plexus.interpolation.fixed.FixedStringSearchInterpolator;

import io.fabric8.maven.docker.assembly.DockerAssemblyConfigurationSource;


/**
 * Utility class for dealing with dockerfiles
 * @author roland
 * @since 21/01/16
 */
public class DockerFileUtil {

    private DockerFileUtil() {}

    /**
     * Extract the base image from a dockerfile. The first line containing a <code>FROM</code> is
     * taken.
     *
     * @param dockerFile file from where to extract the base image
     * @param interpolator interpolator for replacing properties
     */
    public static String extractBaseImage(File dockerFile, FixedStringSearchInterpolator interpolator) throws IOException {
        List<String[]> fromLines = extractLines(dockerFile, "FROM", interpolator);
        if (!fromLines.isEmpty()) {
            String[] parts = fromLines.get(0);
            if (parts.length > 1) {
                return parts[1];
            }
        }
        return null;
    }

    /**
     * Extract all lines containing the given keyword
     *
     * @param dockerFile dockerfile to examine
     * @param keyword keyword to extract the lines for
     * @param interpolator interpolator for replacing properties
     * @return list of matched lines or an empty list
     */
    public static List<String[]> extractLines(File dockerFile, String keyword, FixedStringSearchInterpolator interpolator) throws IOException {
        List<String[]> ret = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(dockerFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String lineInterpolated = interpolator.interpolate(line);
                String[] lineParts = lineInterpolated.split("\\s+");
                if (lineParts.length > 0 && lineParts[0].equalsIgnoreCase(keyword)) {
                    ret.add(lineParts);
                }
            }
        }
        return ret;
    }

    /**
     * Interpolate a docker file with the given properties and filter
     *
     * @param dockerFile docker file to interpolate
     * @param interpolator interpolator for replacing properties
     * @return The interpolated contents of the file.
     * @throws IOException
     */
    public static String interpolate(File dockerFile, FixedStringSearchInterpolator interpolator) throws IOException {
        StringBuilder ret = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(dockerFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                ret.append(interpolator.interpolate(line)).append(System.lineSeparator());
            }
        }
        return ret.toString();
    }

    /**
     * Create an interpolator for the given maven parameters and filter configuration.
     *
     * @param params The maven parameters.
     * @param filter The filter configuration.
     * @return An interpolator for replacing maven properties.
     */
    public static FixedStringSearchInterpolator createInterpolator(MojoParameters params, String filter) {
        String[] delimiters = extractDelimiters(filter);
        if (delimiters == null) {
            // Don't interpolate anything
            return FixedStringSearchInterpolator.create();
        }

        DockerAssemblyConfigurationSource configSource = new DockerAssemblyConfigurationSource(params, null, null);
        // Patterned after org.apache.maven.plugins.assembly.interpolation.AssemblyExpressionEvaluator
        return AssemblyInterpolator
                .fullInterpolator(params.getProject(),
                        DefaultAssemblyReader.createProjectInterpolator(params.getProject())
                          .withExpressionMarkers(delimiters[0], delimiters[1]), configSource)
                .withExpressionMarkers(delimiters[0], delimiters[1]);
    }

    private static String[] extractDelimiters(String filter) {
        if (filter == null ||
            filter.equalsIgnoreCase("false") ||
            filter.equalsIgnoreCase("none")) {
            return null;
        }
        if (filter.contains("*")) {
            Matcher matcher = Pattern.compile("^(?<start>[^*]+)\\*(?<end>.*)$").matcher(filter);
            if (matcher.matches()) {
                return new String[] { matcher.group("start"), matcher.group("end") };
            }
        }
        return new String[] { filter, filter };
    }
}
