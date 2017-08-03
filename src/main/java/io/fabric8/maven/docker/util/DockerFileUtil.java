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
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


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
     * @param properties holding values used for interpolation
     *@param filter @return the base image name or <code>null</code> if none is found.
     */
    public static String extractBaseImage(File dockerFile, Properties properties, String filter) throws IOException {
        List<String[]> fromLines = extractLines(dockerFile, "FROM", properties, filter);
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
     * @return list of matched lines or an empty list
     */
    public static List<String[]> extractLines(File dockerFile, String keyword, Properties props, String filter) throws IOException {
        List<String[]> ret = new ArrayList<>();
        String[] delimiters = extractDelimiters(filter);
        try (BufferedReader reader = new BufferedReader(new FileReader(dockerFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String lineInterpolated = interpolateLine(line, props, delimiters);
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
     * @param properties properties to replace
     * @param filter filter holding delimeters
     * @return
     * @throws IOException
     */
    public static String interpolate(File dockerFile, Properties properties, String filter) throws IOException {
        StringBuilder ret = new StringBuilder();
        String[] delimiters = extractDelimiters(filter);
        try (BufferedReader reader = new BufferedReader(new FileReader(dockerFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                ret.append(interpolateLine(line, properties, delimiters)).append(System.lineSeparator());
            }
        }
        return ret.toString();
    }

    private static String interpolateLine(String line, Properties properties, String[] delimiters) {
        if (delimiters == null || delimiters.length == 0) {
            return line;
        }
        Pattern propertyPattern =
            Pattern.compile("(?<variable>" + Pattern.quote(delimiters[0]) + "(?<prop>.*?)" + Pattern.quote(delimiters[1]) + ")");
        Matcher matcher = propertyPattern.matcher(line);
        StringBuffer ret = new StringBuffer();
        while (matcher.find()) {
            String prop = matcher.group("prop");
            String value = properties.containsKey(prop) ?
                properties.getProperty(prop) :
                matcher.group("variable");
            matcher.appendReplacement(ret, value.replace("$","\\$"));
        }
        matcher.appendTail(ret);
        return ret.toString();
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
