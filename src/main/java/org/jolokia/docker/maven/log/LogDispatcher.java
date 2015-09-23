package org.jolokia.docker.maven.log;/*
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

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.*;

import org.jolokia.docker.maven.access.DockerAccess;
import org.jolokia.docker.maven.access.log.LogCallback;
import org.jolokia.docker.maven.access.log.LogGetHandle;
import org.jolokia.docker.maven.util.Timestamp;

/**
 * @author roland
 * @since 25/11/14
 */
public class LogDispatcher {

    private final boolean withColor;
    private final boolean logStdout;
    private Map<String,ContainerLogOutputSpec> outputSpecs;
    private Map<String,LogGetHandle> logHandles;

    private DockerAccess dockerAccess;

    public LogDispatcher(DockerAccess dockerAccess, boolean withColor, boolean logStdout) {
        this.dockerAccess = dockerAccess;
        this.withColor = withColor;
        this.logStdout = logStdout;
        outputSpecs = new HashMap<>();
        logHandles = new HashMap<>();
    }

    public synchronized void trackContainerLog(String id, ContainerLogOutputSpec spec) throws FileNotFoundException {
        outputSpecs.put(id, spec);
        LogGetHandle handle = dockerAccess.getLogAsync(id, createLogCallBack(id, spec.getFile()));
        logHandles.put(id, handle);
    }

    public synchronized void fetchContainerLog(String id, ContainerLogOutputSpec spec) throws FileNotFoundException {
        outputSpecs.put(id, spec);
        dockerAccess.getLogSync(id,createLogCallBack(id,spec.getFile()));
    }

    // =======================================================================================

    private PrintStream getPrintStream(String file) throws FileNotFoundException {
        return !logStdout && file != null ? new PrintStream(new FileOutputStream(file), true) : System.out;
    }

    private LogCallback createLogCallBack(final String id, final String file) throws FileNotFoundException {
        final PrintStream ps = getPrintStream(file);
        return new LogCallback() {
            @Override
            public void log(int type, Timestamp timestamp, String txt) {
                addLogEntry(ps, new LogEntry(id, type, timestamp, txt, withColor && (file == null || logStdout)));
            }

            @Override
            public void error(String error) {
                ps.println(error);
            }
        };
    }

    private void addLogEntry(PrintStream ps, LogEntry logEntry) {
        // TODO: Add the entry to a queue, and let the queue be picked up with a small delay from an extra
        // thread which then can sort the entries by time before printing it out in order to avoid race conditions.

        String id = logEntry.getContainerId();
        ContainerLogOutputSpec spec = outputSpecs.get(id);
        if (spec == null) {
            spec = ContainerLogOutputSpec.DEFAULT;
        }

        ps.println(spec.getPrompt(logEntry.isWithColor(),logEntry.getTimestamp()) + logEntry.getText());
    }

    public synchronized void untrackAllContainerLogs() {
        for (String key : logHandles.keySet()) {
            LogGetHandle handle = logHandles.get(key);
            handle.finish();
        }
        logHandles.clear();
    }

    // A single log-entry
    private class LogEntry implements Comparable<LogEntry> {
        private final String containerId;
        private final int type;
        private final Timestamp timestamp;
        private final String text;

        private final boolean withColor;

        public LogEntry(String containerId, int type, Timestamp timestamp, String text, boolean withColor) {
            this.containerId = containerId;
            this.type = type;
            this.timestamp = timestamp;
            this.text = text;
            this.withColor = withColor;
        }

        public String getContainerId() {
            return containerId;
        }

        public int getType() {
            return type;
        }

        public Timestamp getTimestamp() {
            return timestamp;
        }

        public String getText() {
            return text;
        }

        public boolean isWithColor() {
            return withColor;
        }

        @Override
        public int compareTo(LogEntry entry) {
            return timestamp.compareTo(entry.timestamp);
        }
    }
}
