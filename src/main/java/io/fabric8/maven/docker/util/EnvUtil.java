package io.fabric8.maven.docker.util;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.shared.utils.io.FileUtils;
import org.codehaus.plexus.util.StringUtils;

import static java.util.concurrent.TimeUnit.*;

/**
 * Utility class for various (loosely) environment related tasks.
 *
 * @author roland
 * @since 04.04.14
 */
public class EnvUtil {

    public static final String MAVEN_PROPERTY_REGEXP = "\\s*\\$\\{\\s*([^}]+)\\s*}\\s*$";

    // Standard HTTPS port (IANA registered). The other 2375 with plain HTTP is used only in older
    // docker installations.
    public static final String DOCKER_HTTPS_PORT = "2376";

    private EnvUtil() {}

    // Check both, url and env DOCKER_HOST (first takes precedence)
    public static String extractUrl(String dockerHost) {
        String connect = dockerHost != null ? dockerHost : System.getenv("DOCKER_HOST");
        if (connect == null) {
            File unixSocket = new File("/var/run/docker.sock");
            if (unixSocket.exists() && unixSocket.canRead() && unixSocket.canWrite()) {
                connect = "unix:///var/run/docker.sock";
            } else {
                throw new IllegalArgumentException("No url given, no DOCKER_HOST environment variable and no read/writable '/var/run/docker.sock'");
            }
        }
        String protocol = connect.contains(":" + DOCKER_HTTPS_PORT) ? "https:" : "http:";
        return connect.replaceFirst("^tcp:", protocol);
    }
    
    public static String getCertPath(String certPath) {
        String path = certPath != null ? certPath : System.getenv("DOCKER_CERT_PATH");
        if (path == null) {
            File dockerHome = new File(System.getProperty("user.home") + "/.docker");
            if (dockerHome.isDirectory() && dockerHome.list(SuffixFileFilter.PEM_FILTER).length > 0) {
                return dockerHome.getAbsolutePath();
            }
        }
        return path;
    }

    /**
     * Compare to version strings and return the larger version strings. This is used in calculating
     * the minimal required API version for this plugin. Version strings must be comparable as floating numbers.
     * (and parse via {@link Float#parseFloat(String)}.
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
            return
                Float.parseFloat(versionA) > Float.parseFloat(versionB) ?
                    versionA : versionB;
        }
    }


    /**
     * Splits every element in the given list on the last colon in the name and returns a list with
     * two elements: The left part before the colon and the right part after the colon. If the string doesnt contain
     * a colon, the value is used for both elements in the returned arrays.
     *
     * @param listToSplit list of strings to split
     * @return return list of 2-element arrays or an empty list if the given list is empty or null
     */
    public static List<String[]> splitOnLastColon(List<String> listToSplit) {
        if (listToSplit != null) {
            List<String[]> ret = new ArrayList<>();

            for (String element : listToSplit) {
                String[] p = element.split(":");
                String rightValue = p[p.length - 1];
                String[] nameParts = Arrays.copyOfRange(p, 0, p.length - 1);
                String leftValue = StringUtils.join(nameParts, ":");
                if (leftValue.length() == 0) {
                    leftValue = rightValue;
                }
                ret.add(new String[]{leftValue, rightValue});
            }

            return ret;
        }
        return Collections.emptyList();
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
     * Extract part of given properties as a map. The given prefix is used to find the properties,
     * the rest of the property name is used as key for the map.
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

    public static String findRegistry(String ... checkFirst) {
        for (String registry : checkFirst) {
            if (registry != null) {
                return registry;
            }
        }
        // Check environment as last resort
        return System.getenv("DOCKER_REGISTRY");
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
            FileUtils.fileWrite(tsFile, StandardCharsets.US_ASCII.name(), Long.toString(buildDate.getTime()));
        } catch (IOException e) {
            throw new MojoExecutionException("Cannot create " + tsFile + " for storing time " + buildDate.getTime(),e);
        }
    }

    public static Date loadTimestamp(File tsFile) throws MojoExecutionException {
        try {
            if (tsFile.exists()) {
                String ts = FileUtils.fileRead(tsFile);
                return new Date(Long.parseLong(ts));
            } else {
                return null;
            }
        } catch (IOException e) {
            throw new MojoExecutionException("Cannot read timestamp " + tsFile,e);
        }
    }

    public static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("windows");
    }
}
