package org.jolokia.docker.maven.util;

import org.junit.Test;

import static org.junit.Assert.*;
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
 * @author roland
 * @since 31/03/15
 */
public class PomLabelTest {

    String g = "org.jolokia";
    String a = "demo";
    String v = "0.0.1";
    String coord = g + ":" + a + ":" + v;

    @Test
    public void simple() throws Exception {
        PomLabel label = new PomLabel(g,a,v);
        assertTrue(label.getValue().startsWith(coord));
        assertTrue(label.getValue().length() > coord.length());
    }

    @Test
    public void withNullRunId() {
        PomLabel label = new PomLabel(g,a,v,null);
        assertEquals(label.getValue(), coord);
    }

    @Test
    public void withRunId() {
        PomLabel label = new PomLabel(g,a,v,"blub");
        assertEquals(label.getValue(),coord + ":blub");
    }

    @Test
    public void matchesAll() throws Exception {
        PomLabel label = new PomLabel(g, a, v,null);
        assertTrue(label.matches(new PomLabel(g, a, v)));
    }

    @Test
    public void dontMatch() {
        PomLabel label = new PomLabel(g, a, v);
        assertFalse(label.matches(new PomLabel(g, a, v)));
    }

    @Test
    public void dontIncludeRunId() {
        PomLabel label = new PomLabel(g, a, v, "bla");
        assertTrue(label.matches(new PomLabel(g, a, v, "foo"), false));
    }
    
    @Test
    public void match() {
        PomLabel label = new PomLabel(g, a, v, "bla");
        assertTrue(label.matches(new PomLabel(g, a, v, "bla")));
    }

    @Test
    public void parse() {
        PomLabel label = new PomLabel(coord);
        assertEquals(coord, label.getValue());
    }

    @Test
    public void parse2() {
        PomLabel label = new PomLabel(coord + ":blub");
        assertEquals(coord + ":blub",label.getValue());
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalid() {
        new PomLabel("bla");
    }
}