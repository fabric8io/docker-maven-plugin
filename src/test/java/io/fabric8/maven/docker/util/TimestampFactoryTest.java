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

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author roland
 * @since 25/11/14
 */
public class TimestampFactoryTest {

    ZonedDateTime ref;

    @Before
    public void setUp() throws Exception {
        ref = TimestampFactory.createTimestamp("2014-11-24T22:34:00.761764812Z");
    }


    @Test
    public void testParse() throws Exception {
        ZonedDateTime dt = ZonedDateTime.parse("2014-11-24T22:34:00.761Z");
        assertEquals(dt,ref);
    }

    @Test
    public void testDateCompare() throws Exception {
        ZonedDateTime ts = TimestampFactory.createTimestamp("2014-12-24T12:00:00.761764812Z");
        assertTrue(ref.compareTo(ts) < 0);
    }



    @Test
    public void testEquals() throws Exception {
        ZonedDateTime ts = TimestampFactory.createTimestamp("2014-11-24T22:34:00.761764812Z");
        assertEquals(0,ref.compareTo(ts));
        assertEquals(ref,ts);
    }

    @Test
    public void testHash() throws Exception {
        ZonedDateTime ts = TimestampFactory.createTimestamp("2014-11-24T22:34:00.761764812Z");
        Set<ZonedDateTime> set = new HashSet<>();
        set.add(ref);
        set.add(ts);
        assertEquals(1,set.size());
    }

    @Test
    public void testNanoCompare() throws Exception {
        ZonedDateTime ts = TimestampFactory.createTimestamp("2014-11-24T12:00:00.761764811Z");
        assertTrue(ref.compareTo(ts) > 0);
    }

    @Test
    public void testNoNanos() throws Exception {
        ZonedDateTime ts = TimestampFactory.createTimestamp("2014-11-24T12:00:00Z");
        assertTrue(ref.compareTo(ts) > 0);
    }

    @Test(expected = DateTimeParseException.class)
    public void testInvalidSpec() throws Exception {
        TimestampFactory.createTimestamp("");
    }

    @Test
    public void testNumericTimeZoneOffset() throws Exception {
        ZonedDateTime ts3p = TimestampFactory.createTimestamp("2016-03-16T17:06:30.714387000+03");
        ZonedDateTime ts530p = TimestampFactory.createTimestamp("2016-03-16T17:06:30.714387000+05:30");
        ZonedDateTime ts4 = TimestampFactory.createTimestamp("2016-03-16T17:06:30.714387000-04:00");
        ZonedDateTime ts2 = TimestampFactory.createTimestamp("2016-03-16T17:06:30.714387000-02:00");
        ZonedDateTime tsz = TimestampFactory.createTimestamp("2016-03-16T17:06:30.714387000Z");
        assertTrue(ts2.compareTo(ts4) < 0);
        assertTrue(tsz.compareTo(ts2) < 0);
        assertTrue(ts3p.compareTo(ts4) < 0);
        assertTrue(ts530p.compareTo(ts3p) < 0);
    }

    @Test(expected = NullPointerException.class)
    public void testNullSpec() throws Exception {
        TimestampFactory.createTimestamp(null);
    }

    @Test(expected = DateTimeParseException.class)
    public void testInvalidNanos() throws Exception {
        TimestampFactory.createTimestamp("2014-11-24T12:00:00.abzeZ");
    }
}
