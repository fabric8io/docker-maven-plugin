package io.fabric8.maven.docker.util;/*
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

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Factory class for timestamp, eg {@link ZonedDateTime}.
 *
 * @author roland
 * @since 25/11/14
 */
public class TimestampFactory {
    private TimestampFactory() {
        // Empty Constructor
    }

    /**
     * Create a timestamp for *now*
     */
    public static ZonedDateTime createTimestamp() {
        return ZonedDateTime.now();
    }

    /**
     * Create a timestamp by parsing the given string representation which must be in an extended ISO 8601 Format
     * with Nanoseconds since this is the format used by Docker for logging (e.g. "2014-11-24T22:34:00.761764812Z")
     *
     * @param spec date specification to parse
     */
    public static ZonedDateTime createTimestamp(String spec) {
        return ZonedDateTime.parse(spec).truncatedTo(ChronoUnit.MILLIS);
    }
}
