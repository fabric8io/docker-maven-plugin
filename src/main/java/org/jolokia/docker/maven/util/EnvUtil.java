package org.jolokia.docker.maven.util;

import java.io.*;
import java.util.*;
import java.util.regex.Pattern;

import org.apache.maven.plugin.MojoExecutionException;
import org.codehaus.plexus.util.StringUtils;

/**
 * Utility class for various (loosely) environment related tasks.
 *
 * @author roland
 * @since 04.04.14
 */
public class EnvUtil {

    private EnvUtil() {}

    /**
     * Write out a property file
     *
     * @param props properties to write
     * @param portPropertyFile file name
     * @throws MojoExecutionException
     */
    public static void writePortProperties(Properties props,String portPropertyFile) throws MojoExecutionException {
        File propFile = new File(portPropertyFile);
        OutputStream os = null;
        try {
            os = new FileOutputStream(propFile);
            props.store(os,"Docker ports");
        } catch (IOException e) {
            throw new MojoExecutionException("Cannot write properties to " + portPropertyFile + ": " + e,e);
        } finally {
            if (os != null) {
                try {
                    os.close();
                } catch (IOException e) {
                    // best try ...
                }
            }
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
        } else {
            return Collections.emptyList();
        }
    }

    public static String[] splitWOnSpaceWithEscape(String toSplit) {
        String[] split = toSplit.split("(?<!" + Pattern.quote("\\") + ")\\s+");
        String[] res = new String[split.length];
        for (int i = 0; i < split.length; i++) {
            res[i] = split[i].replaceAll("\\\\ "," ");
        }
        return res;
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
        List<String> ret = new ArrayList<String>(orderedMap.values());
        ret.addAll(rest);
        return ret.size() > 0 ? ret : null;
    }

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
}
