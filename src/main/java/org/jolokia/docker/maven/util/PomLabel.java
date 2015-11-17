package org.jolokia.docker.maven.util;
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

import java.util.UUID;

/**
 * Label used to mark a container belonging to a certain build.
 *
 * @author roland
 * @since 31/03/15
 */
public class PomLabel {

    // Environment variable used to label containers
    public static final String CONTAINER_LABEL_NAME = "docker.maven.plugin.container";
    
    private String mavenCoordinates;
    private String runId;

    /**
     * Construct from a given label
     *
     * @param label label as stored with the container
     */
    public PomLabel(String label) {
        String[] parts = label.split(":");
        if (parts.length != 4 && parts.length != 3) {
            throw new IllegalArgumentException("Label '" + label +
                                               "' has not the format <group>:<artifact>:<version>[:<runId>]");
        }
        mavenCoordinates = parts[0] + ":" + parts[1] + ":" + parts[2];
        runId = parts.length == 4 ? parts[3] : null;
    }

    /**
     * Construct from maven coordinates. A random run-ID is created automatically.
     * @param groupId Maven group
     * @param artifactId Maven artifact
     * @param version version
     */
    public PomLabel(String groupId, String artifactId, String version) {
        this(groupId, artifactId, version, UUID.randomUUID().toString());
    }

    /**
     * Construct from maven coordinates and run ID. If the runId is <code>null</code> this label
     * will.
     *
     * @param groupId Maven group
     * @param artifactId Maven artifact
     * @param version version
     * @param runId a run id or <code>null</code>.
     */
    public PomLabel(String groupId, String artifactId, String version, String runId) {
        mavenCoordinates = groupId + ":" + artifactId + ":" + version;
        this.runId = runId;
    }

    /**
     * Get the label name
     *
     * @return the label name to use to mark a container belonging to this build
     */
    public String getKey() {
        return "docker.maven.plugin.container";
    }

    /**
     * Get this label in string representation
     * @return this label as string
     */
    public String getValue() {
        return mavenCoordinates + (runId != null ? ":" + runId : "");
    }

    public boolean matches(PomLabel other) {
        return matches(other, true);
    }
    
    /**
     * Check whether this label matches another.
     * <p>
     * To match, the maven coordinates must be equal and if <code>incRunId</code> is <code>true</code>, the <code>runId</code> must also
     * match.
     * </p>
     *
     * @param other label to match
     * @param incRunId <code>true<code> if the <code>runId</code> should be considered during the match, <code>false<code> otherwise.
     * @return true for a match
     */
    public boolean matches(PomLabel other, boolean incRunId) {
        boolean matches = other.mavenCoordinates.equals(mavenCoordinates);
        if (incRunId) {
            matches = matches && (runId == null || runId.equals(other.runId));
        }
        return matches;
    }
}
