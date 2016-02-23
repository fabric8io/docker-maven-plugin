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

import io.fabric8.maven.docker.util.Timestamp;
import org.fusesource.jansi.Ansi;
import org.joda.time.format.*;

import static org.fusesource.jansi.Ansi.Color.*;
import static org.fusesource.jansi.Ansi.ansi;

/**
 * @author roland
 * @since 25/11/14
 */
public class LogOutputSpec {

    public static final LogOutputSpec DEFAULT = new LogOutputSpec("", YELLOW, null, null, null, true, true);

    private final String containerId;
    private final boolean useColor;
    private final boolean logStdout;
    private String prefix;
    private Ansi.Color color;
    private DateTimeFormatter timeFormatter;
    private String file;

    // Palette used for prefixing the log output
    private final static Ansi.Color COLOR_PALETTE[] = {
            YELLOW,CYAN,MAGENTA,GREEN,RED,BLUE
    };
    private static int globalColorIdx = 0;

    private LogOutputSpec(String prefix, Ansi.Color color, DateTimeFormatter timeFormatter,
                          String containerId, String file, boolean useColor, boolean logStdout) {
        this.prefix = prefix;
        this.color = color;
        this.containerId = containerId;
        this.timeFormatter = timeFormatter;
        this.file = file;
        this.useColor = useColor;
        this.logStdout = logStdout;
    }

    public boolean isUseColor() {
        return useColor && (file == null || logStdout);
    }

    public boolean isLogStdout() {
        return logStdout;
    }

    public String getContainerId() {
        return containerId;
    }

    public String getPrompt(boolean withColor,Timestamp timestamp) {
        return formatTimestamp(timestamp,withColor) + formatPrefix(prefix, withColor) + "> ";
    }

    public String getFile(){
        return file;
    }

    private String formatTimestamp(Timestamp timestamp,boolean withColor) {
        if (timeFormatter == null) {
            return "";
        }
        String date = timeFormatter.print(timestamp.getDate());
        return (withColor ?
                ansi().fgBright(BLACK).a(date).reset().toString() :
                date) + " ";
    }

    private String formatPrefix(String prefix,boolean withColor) {
        return withColor ?
                ansi().fg(color).a(prefix).reset().toString() :
                prefix;
    }

    public static class Builder {
        private String prefix;
        private Ansi.Color color;
        private String containerId;
        private DateTimeFormatter timeFormatter;
        private String file;
        private boolean useColor;
        private boolean logStdout;

        public Builder prefix(String prefix) {
            this.prefix = prefix;
            return this;
        }

        public Builder color(String color) {
            if (color == null) {
                this.color = COLOR_PALETTE[globalColorIdx++ % COLOR_PALETTE.length];
            } else {
                try {
                    this.color = Ansi.Color.valueOf(color.toUpperCase());
                } catch (IllegalArgumentException exp) {
                    throw new IllegalArgumentException(
                            "Invalid color '" + color +
                            "'. Color must be one YELLOW, CYAN, MAGENTA, GREEN, RED or BLUE");
                }
            }
            return this;
        }

        public Builder file(String file){
            this.file = file;
            return this;
        }

        public Builder timeFormatter(String formatOrConstant) {
            if (formatOrConstant == null || formatOrConstant.equalsIgnoreCase("NONE")
                || formatOrConstant.equalsIgnoreCase("FALSE")) {
                timeFormatter = null;
            } else if (formatOrConstant.length() == 0 || formatOrConstant.equalsIgnoreCase("DEFAULT")) {
                timeFormatter = DateTimeFormat.forPattern("HH:mm:ss.SSS");
            } else if (formatOrConstant.equalsIgnoreCase("ISO8601")) {
                timeFormatter = ISODateTimeFormat.dateTime();
            } else if (formatOrConstant.equalsIgnoreCase("SHORT")) {
                timeFormatter = DateTimeFormat.shortDateTime();
            } else if (formatOrConstant.equalsIgnoreCase("MEDIUM")) {
                timeFormatter = DateTimeFormat.mediumDateTime();
            } else if (formatOrConstant.equalsIgnoreCase("LONG")) {
                timeFormatter = DateTimeFormat.longDateTime();
            } else {
                try {
                    timeFormatter = DateTimeFormat.forPattern(formatOrConstant);
                } catch (IllegalArgumentException exp) {
                    throw new IllegalArgumentException(
                            "Cannot parse log date specification '" + formatOrConstant + "'." +
                            "Must be either DEFAULT, NONE, ISO8601, SHORT, MEDIUM, LONG or a " +
                            "format string parseable by DateTimeFormat. See " +
                            "http://joda-time.sourceforge.net/apidocs/org/joda/time/format/DateTimeFormat.html");
                }
            }
            return this;
        }

        public Builder containerId(String id) {
            this.containerId = id;
            return this;
        }

        public Builder useColor(boolean useColor) {
            this.useColor = useColor;
            return this;
        }

        public Builder logStdout(boolean logStdout) {
            this.logStdout = logStdout;
            return this;
        }

        public LogOutputSpec build() {
            return new LogOutputSpec(prefix, color, timeFormatter, containerId, file, useColor, logStdout);
        }
    }
}
