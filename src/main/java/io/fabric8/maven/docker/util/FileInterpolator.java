/*-
 *
 * Copyright 2017
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
package io.fabric8.maven.docker.util;

import org.codehaus.plexus.util.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for performing variable substitution on a text file
 *
 * @author pgier
 * @since 06.01.17
 */
public class FileInterpolator {
    //public final static String DEFAULT_CHAR_ENCODING = "UTF-8";

    public final static String DEFAULT_START_DELIMITER = "%%";
    public final static String DEFAULT_END_DELIMITER = "%%";

    /**
     * Interpolate the variables in the given file.
     *
     * @param inputFile
     * @param outputFile
     * @param vars
     * @throws IOException
     */
    public static void interpolate(File inputFile, File outputFile, Properties vars) throws IOException {
        interpolate(inputFile, outputFile, vars, DEFAULT_START_DELIMITER, DEFAULT_END_DELIMITER);
    }

    /**
     * Interpolate the variables in the given file
     *
     * @param inputFile
     * @param outputFile
     * @param vars
     * @param startDelim
     * @param endDelim
     * @throws IOException
     */
    public static void interpolate(File inputFile, File outputFile, Properties vars, String startDelim, String endDelim)
            throws IOException {
        String input = FileUtils.fileRead(inputFile);
        String output = interpolate(input, vars, startDelim, endDelim);
        FileUtils.fileWrite(outputFile, output);
    }

    private static String interpolate(String input, Properties vars, String startDelim, String endDelim) {
        StringBuffer output = new StringBuffer();
        final String regex = startDelim + "([.\\-_\\w]+)" + endDelim + "?";
        final Pattern pattern = Pattern.compile(regex);
        final Matcher matcher = pattern.matcher(input);
        while (matcher.find()) {
            String replace = vars.getProperty(matcher.group(1));
            if(replace == null) {
                // If there isn't a matching property, then just keep the current string
                replace = matcher.group();
            }
            matcher.appendReplacement(output, replace);
        }
        matcher.appendTail(output);
        return output.toString();
    }

}
