package io.fabric8.maven.docker.log;/*
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
import java.util.HashMap;
import java.util.Map;

import io.fabric8.maven.docker.access.log.LogCallback;
import io.fabric8.maven.docker.util.Timestamp;

/**
 * @author roland
 * @since 26/09/15
 */
public class DefaultLogCallback implements LogCallback {

    private static Map<String, SharedPrintStream> printStreamMap = new HashMap<>();
    
    private final LogOutputSpec outputSpec;
    private final SharedPrintStream sps;
    
    public DefaultLogCallback(LogOutputSpec outputSpec) throws FileNotFoundException {
        this.outputSpec = outputSpec;
        this.sps = createOrReusePrintStream(outputSpec);
    }
    
    private synchronized SharedPrintStream createOrReusePrintStream(LogOutputSpec spec) throws FileNotFoundException {
        String file = spec.getFile();
        if (spec.isLogStdout() || file == null) {
            return new SharedPrintStream(System.out);
        }
        SharedPrintStream sps = printStreamMap.get(file);
        if (sps == null) {
            PrintStream ps = new PrintStream(new FileOutputStream(file), true);
            sps = new SharedPrintStream(ps);
            
            printStreamMap.put(file, sps);
        }
        else {
            sps.allocate();
        }
        return sps;
    }
    
    private PrintStream ps() {
        return sps.getPrintStream();
    }

    @Override
    public void log(int type, Timestamp timestamp, String txt) {
        addLogEntry(ps(), new LogEntry(type, timestamp, txt));
    }

    @Override
    public void error(String error) {
        ps().println(error);
    }

    @Override
    public synchronized void close() {
        sps.free();
        if (!sps.isUsed()) {
            if (sps.getPrintStream() != System.out) {
                sps.getPrintStream().close();
            }
            String file = outputSpec.getFile();
            if (file != null) {
                printStreamMap.remove(file);
            }
        }
    }
    private void addLogEntry(PrintStream ps, LogEntry logEntry) {
        // TODO: Add the entry to a queue, and let the queue be picked up with a small delay from an extra
        // thread which then can sort the entries by time before printing it out in order to avoid race conditions.

        LogOutputSpec spec = outputSpec;
        if (spec == null) {
            spec = LogOutputSpec.DEFAULT;
        }
        String text = logEntry.getText();
        ps.println(spec.getPrompt(spec.isUseColor(),logEntry.getTimestamp()) + text);
    }

        // A single log-entry
    private static class LogEntry implements Comparable<LogEntry> {
        private final int type;
        private final Timestamp timestamp;
        private final String text;

        public LogEntry(int type, Timestamp timestamp, String text) {
            this.type = type;
            this.timestamp = timestamp;
            this.text = text;
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
