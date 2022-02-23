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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.apache.maven.plugins.assembly.interpolation.AssemblyInterpolator;
import org.apache.maven.plugins.assembly.io.DefaultAssemblyReader;
import org.codehaus.plexus.interpolation.fixed.FixedStringSearchInterpolator;

import io.fabric8.maven.docker.assembly.DockerAssemblyConfigurationSource;
import org.yaml.snakeyaml.Yaml;

import static io.fabric8.maven.docker.util.EnvUtil.getUserHome;


/**
 * Utility class for dealing with dockerfiles
 * @author roland
 * @since 21/01/16
 */
public class DockerFileUtil {

    private static final String ARG_PATTERN_REGEX = "\\$([\\w|\\-|\\.]+)|\\$\\{([\\w|\\-|\\.]+)\\}";

    private DockerFileUtil() {}

    /**
     * Extract the base images from a dockerfile. All lines containing a <code>FROM</code> is
     * taken.
     *
     * @param dockerFile file from where to extract the base image
     * @param interpolator interpolator for replacing properties
     * @return LinkedList of base images name or empty collection if none is found.
     */
    public static List<String> extractBaseImages(File dockerFile, FixedStringSearchInterpolator interpolator, Map<String, String> argsFromBuildConfig) throws IOException {
        List<String[]> fromLines = extractLines(dockerFile, "FROM", interpolator);
        Map<String, String> args = extractArgs(dockerFile, argsFromBuildConfig, interpolator);
        Set<String> result = new LinkedHashSet<>();
        Set<String> fromAlias = new HashSet<>();
        for (String[] fromLine :  fromLines) {
            if (fromLine.length > 1) {
                String from = extractValidFromLine(fromLine);
                if (!fromAlias.contains(from)) {
                    // Image is NOT referring to an already declared alias
                    result.add(resolveImageTagFromArgs(from, args));
                }
                if (fromLine.length == 4) { // FROM image:tag AS alias use case
                    // Image alias is declared - track it
                    fromAlias.add(resolveImageTagFromArgs(fromLine[3], args));
                }
            }
        }
        return new ArrayList<>(result);
    }

    private static String extractValidFromLine(String[] fromLine) {
        String validFrom = fromLine[1];
        for (int i = 1; i < fromLine.length; i++) {
            if (!fromLine[i].startsWith("--")) {
                validFrom = fromLine[i];
                break;
            }
        }
        return validFrom;
    }

    /**
     * Extract Args from dockerfile. All lines containing ARG is taken.
     *
     * @param dockerfile Docker File
     * @param interpolator interpolator for replacement
     * @return HashMap of arguments or empty collection if none is found
     */
    public static Map<String, String> extractArgs(File dockerfile, Map<String, String> argsFromBuildConfig, FixedStringSearchInterpolator interpolator) throws IOException {
        return extractArgsFromLines(extractLines(dockerfile, "ARG", interpolator), argsFromBuildConfig);
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

    /**
     * Helper method for extractArgs(exposed for test)
     *
     * @param argLines list of string arrays containing lines with words
     * @return map of parsed arguments
     */
    static Map<String, String> extractArgsFromLines(List<String[]> argLines, Map<String, String> argsFromBuildConfig) {
        Map<String, String> result = new HashMap<>();
        for (String[] argLine : argLines) {
            if (argLine.length > 1) {
                updateMapWithArgValue(result, argsFromBuildConfig, argLine[1]);
            }
        }
        return result;
    }

    static String resolveImageTagFromArgs(String imageTagString, Map<String, String> args) {
        String resolvedImageString = imageTagString;
        Set<String> foundArgs = findAllArgs(imageTagString);
        for (String foundArg : foundArgs) {
            if (args.containsKey(foundArg)) {
                resolvedImageString = resolvedImageString.replaceFirst(String.format("\\$\\{*%s\\}*", foundArg),
                        args.get(foundArg));
            }
        }
        return resolvedImageString;
    }

    static Set<String> findAllArgs(String imageTagString) {
        Matcher m = Pattern.compile(ARG_PATTERN_REGEX).matcher(imageTagString);
        Set<String> args = new HashSet<>();
        while(m.find()){
            if(m.group(1)!=null){
                args.add(m.group(1));
            }else if(m.group(2)!=null){
                args.add(m.group(2));
            }
        }
        return args;
    }

    private static Reader getFileReaderFromDir(File file) {
        if (file.exists() && file.length() != 0) {
            try {
                return new FileReader(file);
            } catch (FileNotFoundException e) {
                // Shouldnt happen. Nevertheless ...
                throw new IllegalStateException("Cannot find " + file,e);
            }
        } else {
            return null;
        }
    }

    public static JsonObject readDockerConfig() {
        String dockerConfig = System.getenv("DOCKER_CONFIG");

        Reader reader = dockerConfig == null
                ? getFileReaderFromDir(new File(getHomeDir(),".docker/config.json"))
                : getFileReaderFromDir(new File(dockerConfig,"config.json"));
        return reader != null ? new Gson().fromJson(reader, JsonObject.class) : null;
    }

    public static String[] extractDelimiters(String filter) {
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

    public static Map<String,?> readKubeConfig() {
        String kubeConfig = System.getenv("KUBECONFIG");

        Reader reader = kubeConfig == null
                ? getFileReaderFromDir(new File(getHomeDir(),".kube/config"))
                : getFileReaderFromDir(new File(kubeConfig));
        if (reader != null) {
            Yaml ret = new Yaml();
            return (Map<String, ?>) ret.load(reader);
        }
        return null;
    }

    private static File getHomeDir() {
        return new File(getUserHome());
    }

    private static void updateMapWithArgValue(Map<String, String> result, Map<String, String> args, String argString) {
        if (argString.contains("=") || argString.contains(":")) {
            String[] argStringParts = argString.split("[=:]");
            String argStringKey = argStringParts[0];
            String argStringValue = determineFinalArgValue(argString, argStringParts, args);
            if (argStringValue.startsWith("\"") || argStringValue.startsWith("'")) {
                // Replaces surrounding quotes
                argStringValue = argStringValue.replaceAll("^\"|\"|'|'$", "");
            } else {
                validateArgValue(argStringValue);
            }
            result.put(argStringKey, argStringValue);
        } else {
            validateArgValue(argString);
            result.putAll(fetchArgsFromBuildConfiguration(argString, args));
        }
    }

    private static String determineFinalArgValue(String argString, String[] argStringParts, Map<String, String> args) {
        String argStringValue = argString.substring(argStringParts[0].length() + 1);
        if(args == null || args.get(argStringParts[0]) == null){
            return argStringValue;
        }
        return args.getOrDefault(argStringParts[0], argStringValue);
    }

    private static Map<String, String> fetchArgsFromBuildConfiguration(String argString, Map<String, String> args) {
        Map<String, String> argFromBuildConfig = new HashMap<>();
        if (args != null) {
            argFromBuildConfig.put(argString, args.getOrDefault(argString, ""));
        }
        return argFromBuildConfig;
    }

    private static void validateArgValue(String argStringParam) {
        String[] argStringParts = argStringParam.split("\\s+");
        if (argStringParts.length > 1) {
            throw new IllegalArgumentException("Dockerfile parse error: ARG requires exactly one argument. Provided : " + argStringParam);
        }
    }
}
