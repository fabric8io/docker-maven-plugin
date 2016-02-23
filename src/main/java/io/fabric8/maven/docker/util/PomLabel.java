package io.fabric8.maven.docker.util;
/*
 * 
 * Copyright 2014 Roland Huss
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

/**
 * Label used to mark a container belonging to a certain build.
 *
 * @author roland
 * @since 31/03/15
 */
public class PomLabel {

    private String mavenCoordinates;

    /**
     * Construct from a given label
     *
     * @param label label as stored with the container
     */
    public PomLabel(String label) {
        String[] parts = label.split(":");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Label '" + label +
                                               "' has not the format <group>:<artifact>:<version>");
        }
        mavenCoordinates = parts[0] + ":" + parts[1] + ":" + parts[2];
    }

    /**
     * Construct from maven coordinates and run ID. If the runId is <code>null</code> this label
     * will.
     *
     * @param groupId Maven group
     * @param artifactId Maven artifact
     * @param version version
     */
    public PomLabel(String groupId, String artifactId, String version) {
        mavenCoordinates = groupId + ":" + artifactId + ":" + version;
    }

    /**
     * Get the label name
     *
     * @return the label name to use to mark a container belonging to this build
     */
    public String getKey() {
        return "dmp.coordinates";
    }

    /**
     * Get this label in string representation
     * @return this label as string
     */
    public String getValue() {
        return mavenCoordinates;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        return mavenCoordinates.equals(((PomLabel) o).mavenCoordinates);
    }

    @Override
    public int hashCode() {
        return mavenCoordinates.hashCode();
    }
}
