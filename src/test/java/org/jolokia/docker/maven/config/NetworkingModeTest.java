package org.jolokia.docker.maven.config;
/*
 * 
 * Copyright 2016 Roland Huss
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

import static org.junit.Assert.*;

/**
 * @author roland
 * @since 12/02/16
 */
public class NetworkingModeTest {

    @Test
    public void simple() {
        String[] data = {
            "BRiDge", "bridge", "true", "false", null, null,
            "host", "host", "true", "false", null, null,
            "container:alpha", "container:containerId", "true", "false", "alpha", null,
            "blubber", "custom", "false", "true", null, "blubber",
            "None", "none", "true", "false", null, null
        };
        for (int i = 0; i < data.length; i+=6) {
            NetworkingMode mode = new NetworkingMode(data[i]);
            if (mode.isStandardMode()) {
                assertEquals(data[i + 1], mode.getStandardMode("containerId"));
            } else {
                try {
                    mode.getStandardMode("fail");
                    fail();
                } catch (IllegalArgumentException exp) {

                }
            }
            assertEquals(Boolean.parseBoolean(data[i+2]),mode.isStandardMode());
            assertEquals(Boolean.parseBoolean(data[i+3]),mode.isCustomNetwork());
            assertEquals(data[i+4],mode.getContainerAlias());
            assertEquals(data[i+5],mode.getCustomNetwork());
        }
    }

    @Test
    public void empty() {
        for (String str : new String[]{null, ""}) {
            NetworkingMode mode = new NetworkingMode(str);
            assertFalse(mode.isStandardMode());
            assertFalse(mode.isCustomNetwork());
            assertNull(mode.getContainerAlias());
            assertNull(mode.getCustomNetwork());
        }
    }
}
