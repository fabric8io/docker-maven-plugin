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
public class ContainerLabelTest {

    String g = "org.jolokia";
    String a = "demo";
    String v = "0.0.1";
    String coord = g + ":" + a + ":" + v;

    @Test
    public void simple() throws Exception {
        ContainerLabel label = new ContainerLabel(g,a,v);
        assertTrue(label.toString().startsWith(coord));
        assertTrue(label.toString().length() > coord.length());
    }

    @Test
    public void withNullRunId() {
        ContainerLabel label = new ContainerLabel(g,a,v,null);
        assertEquals(label.toString(), coord);
    }

    @Test
    public void withRunId() {
        ContainerLabel label = new ContainerLabel(g,a,v,"blub");
        assertEquals(label.toString(),coord + ":blub");
    }

    @Test
    public void matchesAll() throws Exception {
        ContainerLabel label = new ContainerLabel(g, a, v,null);
        assertTrue(label.matches(new ContainerLabel(g, a, v)));
    }

    @Test
    public void dontMatch() {
        ContainerLabel label = new ContainerLabel(g, a, v);
        assertFalse(label.matches(new ContainerLabel(g, a, v)));
    }

    @Test
    public void match() {
        ContainerLabel label = new ContainerLabel(g, a, v, "bla");
        assertTrue(label.matches(new ContainerLabel(g, a, v, "bla")));
    }

    @Test
    public void parse() {
        ContainerLabel label = new ContainerLabel(coord);
        assertEquals(coord, label.toString());
    }

    @Test
    public void parse2() {
        ContainerLabel label = new ContainerLabel(coord + ":blub");
        assertEquals(coord + ":blub",label.toString());
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalid() {
        new ContainerLabel("bla");
    }
}