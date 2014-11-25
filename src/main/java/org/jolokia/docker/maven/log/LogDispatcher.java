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
    private Map<String,ContainerLogOutputSpec> outputSpecs;
    private Map<String,LogGetHandle> logHandles;

    private List<PrintStream> printStreams;

    private DockerAccess dockerAccess;

    public LogDispatcher(DockerAccess dockerAccess,boolean withColor) {
        this.dockerAccess = dockerAccess;
        this.withColor = withColor;
        outputSpecs = new HashMap<>();
        printStreams = new ArrayList<>();
        logHandles = new HashMap<>();
    }

    public synchronized void addLogOutputStream(PrintStream out) {
        printStreams.add(out);
    }

    public synchronized void trackContainerLog(String id, ContainerLogOutputSpec spec) {
        outputSpecs.put(id, spec);

        LogGetHandle handle = dockerAccess.getLogAsync(id, createLogCallBack(id));
        logHandles.put(id, handle);
    }

    public synchronized void fetchContainerLog(String id, ContainerLogOutputSpec spec) {
        outputSpecs.put(id, spec);

        dockerAccess.getLogSync(id,createLogCallBack(id));
    }

    private LogCallback createLogCallBack(final String id) {
        return new LogCallback() {
            @Override
            public void log(int type, Timestamp timestamp, String txt) {
                addLogEntry(new LogEntry(id, type, timestamp, txt));
            }

            @Override
            public void error(String error) {
                printError(error);
            }
        };
    }

    private void addLogEntry(LogEntry logEntry) {
        // TODO: Add the entry to a queue, and let the queue be picked up with a small delay from an extra
        // thread which then can sort the entries by time before printing it out in order to avoid race conditions.

        ContainerLogOutputSpec spec = outputSpecs.get(logEntry.getContainerId());
        if (spec == null) {
            spec = ContainerLogOutputSpec.DEFAULT;
        }

        // FIX me according to spec
        print(spec.getPrompt(withColor,logEntry.getTimestamp()) + logEntry.getText());
    }

    private void printError(String e) {
        for (PrintStream ps : printStreams) {
            ps.println(e);
        }
    }

    private void print(String line) {
        for (PrintStream ps : printStreams) {
            ps.println(line);
        }
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

        public LogEntry(String containerId, int type, Timestamp timestamp, String text) {
            this.containerId = containerId;
            this.type = type;
            this.timestamp = timestamp;
            this.text = text;
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

        @Override
        public int compareTo(LogEntry entry) {
            return timestamp.compareTo(entry.timestamp);
        }
    }
}
