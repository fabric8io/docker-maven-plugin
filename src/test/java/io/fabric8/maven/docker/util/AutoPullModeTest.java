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

import org.junit.Test;

import static io.fabric8.maven.docker.util.AutoPullMode.*;
import static org.junit.Assert.assertEquals;

/**
 * @author roland
 * @since 01/03/15
 */
public class AutoPullModeTest {

    @Test
    public void simple() {
        assertEquals(ON, fromString("on"));
        assertEquals(ON, fromString("true"));
        assertEquals(OFF, fromString("Off"));
        assertEquals(OFF, fromString("falsE"));
        assertEquals(ALWAYS, fromString("alWays"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void unknown() {
        fromString("unknown");
    }
}
