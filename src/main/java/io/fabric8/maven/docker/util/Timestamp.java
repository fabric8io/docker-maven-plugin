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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.joda.time.DateTime;
import org.joda.time.format.*;

/**
 * Timestamp holding a {@link DateTime} and nano seconds and which can be compared.
 *
 * @author roland
 * @since 25/11/14
 */
public class Timestamp implements Comparable<Timestamp> {

    private DateTime date;
    private int rest;

    private static Pattern TS_PATTERN = Pattern.compile("^(.*?)(?:\\.(\\d{3})(\\d*))?(Z|[+\\-][\\d:]+)?$",Pattern.CASE_INSENSITIVE);

    /**
     * Create a timestamp for *now*
     *
     */
    public Timestamp() {
        date = new DateTime();
    }

    /**
     * Create a timestamp by parsing the given string representation which must be in an extended ISO 8601 Format
     * with Nanoseconds since this is the format used by Docker for logging (e.g. "2014-11-24T22:34:00.761764812Z")
     *
     * @param spec date specification to parse
     */
    public Timestamp(String spec) {
        //
        Matcher matcher = TS_PATTERN.matcher(spec);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid timestamp '" + spec + "' given.");
        }
        String millis = matcher.group(2);
        String rest = matcher.group(3);
        this.rest = rest != null ? Integer.parseInt(rest) : 0;
        DateTimeFormatter parser = ISODateTimeFormat.dateTime();
        date = parser.parseDateTime(matcher.group(1) + (millis != null ? "." + millis : ".000") + matcher.group(4));
    }

    public DateTime getDate() {
        return date;
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Timestamp timestamp = (Timestamp) o;

        if (rest != timestamp.rest) return false;
        if (!date.equals(timestamp.date)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = date.hashCode();
        result = 31 * result + (int) (rest ^ (rest >>> 32));
        return result;
    }

    @Override
    public int compareTo(Timestamp ts) {
        int fc = this.date.compareTo(ts.date);
        if (fc != 0) {
            return fc;
        }
        return this.rest - ts.rest;
    }

    @Override
    public String toString() {
        return date.toString();
    }

}
