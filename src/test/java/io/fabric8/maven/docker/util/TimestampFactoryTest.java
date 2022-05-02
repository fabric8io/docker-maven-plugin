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
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * @author roland
 * @since 25/11/14
 */
class TimestampFactoryTest {

    ZonedDateTime ref;

    @BeforeEach
    void setUp() {
        ref = TimestampFactory.createTimestamp("2014-11-24T22:34:00.761764812Z");
    }

    @Test
    void testParse() {
        ZonedDateTime dt = ZonedDateTime.parse("2014-11-24T22:34:00.761Z");
        Assertions.assertEquals(dt, ref);
    }

    @Test
    void testDateCompare() {
        ZonedDateTime ts = TimestampFactory.createTimestamp("2014-12-24T12:00:00.761764812Z");
        Assertions.assertTrue(ref.compareTo(ts) < 0);
    }

    @Test
    void testEquals() {
        ZonedDateTime ts = TimestampFactory.createTimestamp("2014-11-24T22:34:00.761764812Z");
        Assertions.assertEquals(0, ref.compareTo(ts));
        Assertions.assertEquals(ref, ts);
    }

    @Test
    void testHash() {
        ZonedDateTime ts = TimestampFactory.createTimestamp("2014-11-24T22:34:00.761764812Z");
        Set<ZonedDateTime> set = new HashSet<>();
        set.add(ref);
        set.add(ts);
        Assertions.assertEquals(1, set.size());
    }

    @Test
    void testNanoCompare() {
        ZonedDateTime ts = TimestampFactory.createTimestamp("2014-11-24T12:00:00.761764811Z");
        Assertions.assertTrue(ref.compareTo(ts) > 0);
    }

    @Test
    void testNoNanos() {
        ZonedDateTime ts = TimestampFactory.createTimestamp("2014-11-24T12:00:00Z");
        Assertions.assertTrue(ref.compareTo(ts) > 0);
    }

    @Test
    void testInvalidSpec() {
        Assertions.assertThrows(DateTimeParseException.class, () -> TimestampFactory.createTimestamp(""));
    }

    @Test
    void testNumericTimeZoneOffset() {
        ZonedDateTime ts3p = TimestampFactory.createTimestamp("2016-03-16T17:06:30.714387000+03:00");
        ZonedDateTime ts530p = TimestampFactory.createTimestamp("2016-03-16T17:06:30.714387000+05:30");
        ZonedDateTime ts4 = TimestampFactory.createTimestamp("2016-03-16T17:06:30.714387000-04:00");
        ZonedDateTime ts2 = TimestampFactory.createTimestamp("2016-03-16T17:06:30.714387000-02:00");
        ZonedDateTime tsz = TimestampFactory.createTimestamp("2016-03-16T17:06:30.714387000Z");
        Assertions.assertTrue(ts2.compareTo(ts4) < 0);
        Assertions.assertTrue(tsz.compareTo(ts2) < 0);
        Assertions.assertTrue(ts3p.compareTo(ts4) < 0);
        Assertions.assertTrue(ts530p.compareTo(ts3p) < 0);
    }

    @Test
    void testNullSpec() {
        Assertions.assertThrows(NullPointerException.class, () -> TimestampFactory.createTimestamp(null));
    }

    @Test
    void testInvalidNanos() {
        Assertions.assertThrows(DateTimeParseException.class, () -> TimestampFactory.createTimestamp("2014-11-24T12:00:00.abzeZ"));
    }
}
