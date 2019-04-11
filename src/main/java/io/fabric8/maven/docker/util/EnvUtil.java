package io.fabric8.maven.docker.util;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.base.*;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.utils.io.FileUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import static java.util.concurrent.TimeUnit.*;

/**
 * Utility class for various (loosely related) environment related tasks.
 *
 * @author roland
 * @since 04.04.14
 */
public class EnvUtil {

    public static final String MAVEN_PROPERTY_REGEXP = "\\s*\\$\\{\\s*([^}]+)\\s*}\\s*$";

    // Standard HTTPS port (IANA registered). The other 2375 with plain HTTP is used only in older
    // docker installations.
    public static final String DOCKER_HTTPS_PORT = "2376";

    public static final String PROPERTY_COMBINE_POLICY_SUFFIX = "_combine";

    private EnvUtil() {}

    // Convert docker host URL to an http URL
    public static String convertTcpToHttpUrl(String connect) {
        String protocol = connect.contains(":" + DOCKER_HTTPS_PORT) ? "https:" : "http:";
        return connect.replaceFirst("^tcp:", protocol);
    }

    /**
     * Compare to version strings and return the larger version strings. This is used in calculating
     * the minimal required API version for this plugin. Version strings must be comparable as floating numbers.
     * The versions must be given in the format in a semantic version foramt (e.g. "1.23"
     *
     * If either version is <code>null</code>, the other version is returned (which can be null as well)
     *
     * @param versionA first version number
     * @param versionB second version number
     * @return the larger version number
     */
    public static String extractLargerVersion(String versionA, String versionB) {
        if (versionB == null || versionA == null) {
            return versionA == null ? versionB : versionA;
        } else {
            String partsA[] = versionA.split("\\.");
            String partsB[] = versionB.split("\\.");
            for (int i = 0; i < (partsA.length < partsB.length ? partsA.length : partsB.length); i++) {
                int pA = Integer.parseInt(partsA[i]);
                int pB = Integer.parseInt(partsB[i]);
                if (pA > pB) {
                    return versionA;
                } else if (pB > pA) {
                    return versionB;
                }
            }
            return partsA.length > partsB.length ? versionA : versionB;
        }
    }

    /**
     * Check whether the first given API version is larger or equals the second given version
     *
     * @param versionA first version to check against
     * @param versionB the second version
     * @return true if versionA is greater or equals versionB, false otherwise
     */
    public static boolean greaterOrEqualsVersion(String versionA, String versionB) {
        String largerVersion = extractLargerVersion(versionA, versionB);
        return largerVersion != null && largerVersion.equals(versionA);
    }

    private static final Function<String, String[]> SPLIT_ON_LAST_COLON = new Function<String, String[]>() {
        @Override
        public String[] apply(String element) {
          int colon = element.lastIndexOf(':');
          if (colon < 0) {
              return new String[] {element, element};
          } else {
              return new String[] {element.substring(0, colon), element.substring(colon + 1)};
          }
        }
    };

    /**
     * Splits every element in the given list on the last colon in the name and returns a list with
     * two elements: The left part before the colon and the right part after the colon. If the string
     * doesn't contain a colon, the value is used for both elements in the returned arrays.
     *
     * @param listToSplit list of strings to split
     * @return return list of 2-element arrays or an empty list if the given list is empty or null
     */
    public static List<String[]> splitOnLastColon(List<String> listToSplit) {
        if (listToSplit != null) {
          return Lists.transform(listToSplit, SPLIT_ON_LAST_COLON);
        }
        return Collections.emptyList();
    }

    private static final Function<String, Iterable<String>> COMMA_SPLITTER = new Function<String, Iterable<String>>() {
        private Splitter COMMA_SPLIT = Splitter.on(",").trimResults().omitEmptyStrings();

        @Override
        public Iterable<String> apply(String input) {
            return COMMA_SPLIT.split(input);
        }
    };

    private static final Predicate<String> NOT_EMPTY = new Predicate<String>() {
        @Override
        public boolean apply(@Nullable String s) {
            return s!=null && !s.isEmpty();
        }
    };

    private static final Function<String,String> TRIM = new Function<String,String>() {
        @Nullable
        @Override
        public String apply(@Nullable String s) {
            return s!=null ?s.trim() :s;
        }
    };

    /**
     * Remove empty members of a list.
     * @param input A list of String
     * @return A list of Non-Empty (length>0) String
     */
    @Nonnull
    public static List<String> removeEmptyEntries(@Nullable List<String> input) {
        if(input==null) {
            return Collections.emptyList();
        }
        Iterable<String> trimmedInputs = Iterables.transform(input, TRIM);
        Iterable<String> nonEmptyInputs = Iterables.filter(trimmedInputs, NOT_EMPTY);
        return Lists.newArrayList(nonEmptyInputs);
    }

    /**
     * Split each element of an Iterable<String> at commas.
     * @param input Iterable over strings.
     * @return An Iterable over string which breaks down each input element at comma boundaries
     */
    @Nonnull
    public static List<String> splitAtCommasAndTrim(Iterable<String> input) {
        if(input==null) {
            return Collections.emptyList();
        }
        Iterable<String> nonEmptyInputs = Iterables.filter(input, Predicates.notNull());
        return Lists.newArrayList(Iterables.concat(Iterables.transform(nonEmptyInputs, COMMA_SPLITTER)));
    }

    public static String[] splitOnSpaceWithEscape(String toSplit) {
        String[] split = toSplit.split("(?<!" + Pattern.quote("\\") + ")\\s+");
        String[] res = new String[split.length];
        for (int i = 0; i < split.length; i++) {
            res[i] = split[i].replaceAll("\\\\ "," ");
        }
        return res;
    }


    /**
     * Join a list of objects to a string with a given separator by calling Object.toString() on the elements.
     *
     * @param list to join
     * @param separator separator to use
     * @return the joined string.
     */
    public static String stringJoin(List list, String separator) {
        StringBuilder ret = new StringBuilder();
        boolean first = true;
        for (Object o : list) {
            if (!first) {
                ret.append(separator);
            }
            ret.append(o);
            first = false;
        }
        return ret.toString();
    }

    /**
     * Return all properties in Maven project, merged with all System properties (-D flags sent to Maven).
     *
     * System properties always takes precedence.
     *
     * @param project Project to extract Properties from
     * @return
     */
    public static Properties getPropertiesWithSystemOverrides(MavenProject project) {
        Properties properties = new Properties(project.getProperties());
        properties.putAll(System.getProperties());
        return properties;
    }

    /**
     * Extract part of given properties as a map. The given prefix is used to find the properties,
     * the rest of the property name is used as key for the map.
     *
     * NOTE: If key is "._combine" ({@link #PROPERTY_COMBINE_POLICY_SUFFIX)} it is ignored! This is reserved for combine policy tweaking.
     *
     * @param prefix prefix which specifies the part which should be extracted as map
     * @param properties properties to extract from
     * @return the extracted map or null if no such map exists
     */
    public static Map<String, String> extractFromPropertiesAsMap(String prefix, Properties properties) {
        Map<String, String> ret = new HashMap<>();
        Enumeration names = properties.propertyNames();
        String prefixP = prefix + ".";
        while (names.hasMoreElements()) {
            String propName = (String) names.nextElement();
            if (propMatchesPrefix(prefixP, propName)) {
                String mapKey = propName.substring(prefixP.length());
                if(PROPERTY_COMBINE_POLICY_SUFFIX.equals(mapKey)) {
                    continue;
                }

                ret.put(mapKey, properties.getProperty(propName));
            }
        }
        return ret.size() > 0 ? ret : null;
    }

    /**
     * Extract from given properties a list of string values. The prefix is used to determine the subset of the
     * given properties from which the list should be extracted, the rest is used as a numeric index. If the rest
     * is not numeric, the order is not determined (all those props are appended to the end of the list)
     *
     * NOTE: If suffix/index is "._combine" ({@link #PROPERTY_COMBINE_POLICY_SUFFIX)} it is ignored!
     * This is reserved for combine policy tweaking.
     *
     * @param prefix for selecting the properties from which the list should be extracted
     * @param properties properties from which to extract from
     * @return parsed list or null if no element with prefixes exists
     */
    public static List<String> extractFromPropertiesAsList(String prefix, Properties properties) {
        TreeMap<Integer,String> orderedMap = new TreeMap<>();
        List<String> rest = new ArrayList<>();
        Enumeration names = properties.propertyNames();
        String prefixP = prefix + ".";
        while (names.hasMoreElements()) {
            String key = (String) names.nextElement();
            if (propMatchesPrefix(prefixP, key)) {
                String index = key.substring(prefixP.length());

                if(PROPERTY_COMBINE_POLICY_SUFFIX.equals(index)) {
                    continue;
                }

                String value = properties.getProperty(key);
                try {
                    Integer nrIndex = Integer.parseInt(index);
                    orderedMap.put(nrIndex,value);
                } catch (NumberFormatException exp) {
                    rest.add(value);
                }
            }
        }
        List<String> ret = new ArrayList<>(orderedMap.values());
        ret.addAll(rest);
        return ret.size() > 0 ? ret : null;
    }

    /**
     * Extract from a Maven property which is in the form ${name} the name.
     *
     * @param propName property name to extrat
     * @return the pure name or null if this is not a property name
     */
    public static String extractMavenPropertyName(String propName) {
        Matcher matcher = Pattern.compile(MAVEN_PROPERTY_REGEXP).matcher(propName);
        if (matcher.matches()) {
            return matcher.group(1);
        } else {
            return null;
        }
    }

    /**
     * Fix path on Windows machines, i.e. convert 'c:\...\' to '/c/..../'
     *
     * @param path path to fix
     * @return the fixed path
     */
    public static String fixupPath(String path) {
        // Hack-fix for mounting on Windows where the ${projectDir} variable and other
        // contain backslashes and what not. Related to #188
        Pattern pattern = Pattern.compile("^(?i)([A-Z]):(.*)$");
        Matcher matcher = pattern.matcher(path);
        if (matcher.matches()) {
            String result = "/" + matcher.group(1).toLowerCase() + matcher.group(2);
            return result.replace("\\","/");
        }
        return path;
    }

    /**
     * Calculate the duration between now and the given time
     *
     * Taken mostly from http://stackoverflow.com/a/5062810/207604 . Kudos to @dblevins
     *
     * @param start starting time (in milliseconds)
     * @return time in seconds
     *
     */
    public static String formatDurationTill(long start) {
        long duration = System.currentTimeMillis() - start;
        StringBuilder res = new StringBuilder();

        TimeUnit current = HOURS;

        while (duration > 0) {
            long temp = current.convert(duration, MILLISECONDS);

            if (temp > 0) {
                duration -= current.toMillis(temp);
                res.append(temp).append(" ").append(current.name().toLowerCase());
                if (temp < 2) res.deleteCharAt(res.length() - 1);
                res.append(", ");
            }
            if (current == SECONDS) {
                break;
            }
            current = TimeUnit.values()[current.ordinal() - 1];
        }
        if (res.lastIndexOf(", ") < 0) {
            return duration + " " + MILLISECONDS.name().toLowerCase();
        }
        res.deleteCharAt(res.length() - 2);
        int i = res.lastIndexOf(", ");
        if (i > 0) {
            res.deleteCharAt(i);
            res.insert(i, " and");
        }

        return res.toString();
    }

    // ======================================================================================================

    private static boolean propMatchesPrefix(String prefix, String key) {
        return key.startsWith(prefix) && key.length() >= prefix.length();
    }

    /**
     * Return the first non null registry given. Use the env var DOCKER_REGISTRY as final fallback
     * @param checkFirst list of registries to check
     * @return registry found or null if none.
     */
    public static String firstRegistryOf(String ... checkFirst) {
        for (String registry : checkFirst) {
            if (registry != null) {
                return registry;
            }
        }
        // Check environment as last resort
        return System.getenv("DOCKER_REGISTRY");
    }

    // sometimes registries might be specified with https? schema, sometimes not
    public static String ensureRegistryHttpUrl(String registry) {
        if (registry.toLowerCase().startsWith("http")) {
            return registry;
        }
        // Default to https:// schema
        return "https://" + registry;
    }

    public static File prepareAbsoluteOutputDirPath(MojoParameters params, String dir, String path) {
        return prepareAbsolutePath(params, new File(params.getOutputDirectory(), dir).toString(), path);
    }

    public static File prepareAbsoluteSourceDirPath(MojoParameters params, String path) {
        return prepareAbsolutePath(params, params.getSourceDirectory(), path);
    }

    private static File prepareAbsolutePath(MojoParameters params, String directory, String path) {
        File file = new File(path);
        if (file.isAbsolute()) {
            return file;
        }
        return new File(new File(params.getProject().getBasedir(), directory), path);
    }

    // create a timestamp file holding time in epoch seconds
    public static void storeTimestamp(File tsFile, Date buildDate) throws MojoExecutionException {
        try {
            if (tsFile.exists()) {
                tsFile.delete();
            }
            File dir = tsFile.getParentFile();
            if (!dir.exists()) {
                if (!dir.mkdirs()) {
                    throw new MojoExecutionException("Cannot create directory " + dir);
                }
            }
            FileUtils.fileWrite(tsFile, StandardCharsets.US_ASCII.name(), Long.toString(buildDate.getTime()));
        } catch (IOException e) {
            throw new MojoExecutionException("Cannot create " + tsFile + " for storing time " + buildDate.getTime(),e);
        }
    }

    public static Date loadTimestamp(File tsFile) throws IOException {
        try {
            if (tsFile.exists()) {
                String ts = FileUtils.fileRead(tsFile);
                return new Date(Long.parseLong(ts));
            } else {
                return null;
            }
        } catch (IOException e) {
            throw new IOException("Cannot read timestamp " + tsFile,e);
        }
    }

    public static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("windows");
    }

    public static boolean isMaven350OrLater(MavenSession mavenSession) {
        // Maven enforcer and help:evaluate goals both use mavenSession.getSystemProperties(),
        // and it turns out that System.getProperty("maven.version") does not return the value.
        String mavenVersion = mavenSession.getSystemProperties().getProperty("maven.version", "3");
        return greaterOrEqualsVersion(mavenVersion, "3.5.0");
    }
}
