package org.jolokia.docker.maven.util;

import java.io.*;
import java.util.*;
import java.util.regex.Pattern;

import org.apache.maven.plugin.MojoExecutionException;
import org.codehaus.plexus.util.StringUtils;

/**
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

    public static List<String[]> splitLinks(List<String> links) {
        if (links != null) {
            List<String[]> ret = new ArrayList<>();

            for (String link : links) {
                String[] p = link.split(":");
                String linkAlias = p[p.length - 1];
                String[] nameParts = Arrays.copyOfRange(p, 0, p.length - 1);
                String lookup = StringUtils.join(nameParts, ":");
                if (lookup.length() == 0) {
                    lookup = linkAlias;
                }
                ret.add(new String[]{lookup, linkAlias});
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

}
