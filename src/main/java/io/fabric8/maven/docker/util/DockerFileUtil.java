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
     * @param dockerfile file from where to extract the base image
     * @return the base image name or <code>null</code> if none is found.
     */
    public static String extractBaseImage(File dockerfile) throws IOException {
        // TODO: Interpolate Maven properties
        try (BufferedReader reader = new BufferedReader(new FileReader(dockerfile))) {
            String line;
            Pattern fromPattern = Pattern.compile("^\\s*FROM\\s+([^\\s]+).*$", Pattern.CASE_INSENSITIVE);
            while ((line = reader.readLine()) != null) {
                Matcher matcher = fromPattern.matcher(line);
                if (matcher.matches()) {
                    return matcher.group(1);
                }
            }
            return null;
        }
    }
}
