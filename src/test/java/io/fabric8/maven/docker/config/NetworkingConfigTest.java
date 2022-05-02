package io.fabric8.maven.docker.config;
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

import java.util.Arrays;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static io.fabric8.maven.docker.config.NetworkConfig.Mode.*;

/**
 * @author roland
 * @since 12/02/16
 */
class NetworkingConfigTest {

    @Test
    void simple() {
        Object[] data = {
            bridge, null, "BRiDge", "bridge", "true", "false", null, null,
            host, null, "host", "host", "true", "false", null, null,
            container, "alpha", "container:alpha", "container:containerId", "true", "false", "alpha", null,
            null, "blubber", "blubber", "custom", "false", "true", null, "blubber",
            custom, "blubber", "blubber", "custom", "false", "true", null, "blubber",
            none, null, "None", "none", "true", "false", null, null
        };
        for (int i = 0; i < data.length; i += 8) {
            for (NetworkConfig config : new NetworkConfig[]{
                new NetworkConfig((NetworkConfig.Mode) data[i],(String) data[i + 1]),
                new NetworkConfig((String) data[i + 2])}) {
                if (config.isStandardNetwork()) {
                    Assertions.assertEquals(data[i + 3], config.getStandardMode("containerId"));
                } else {
                    Assertions.assertThrows(IllegalArgumentException.class, () ->config.getStandardMode("fail"));
                }
                Assertions.assertEquals(Boolean.parseBoolean((String) data[i + 4]), config.isStandardNetwork());
                Assertions.assertEquals(Boolean.parseBoolean((String) data[i + 5]), config.isCustomNetwork());
                Assertions.assertEquals(data[i + 6], config.getContainerAlias());
                Assertions.assertEquals(data[i + 7], config.getCustomNetwork());
            }
        }
    }

    @Test
    void empty() {
        for (String str : new String[]{ null, "" }) {
            NetworkConfig config = new NetworkConfig(str);
            Assertions.assertFalse(config.isStandardNetwork());
            Assertions.assertFalse(config.isCustomNetwork());
            Assertions.assertNull(config.getContainerAlias());
            Assertions.assertNull(config.getCustomNetwork());
        }
    }

    @Test
    void builder() {
        NetworkConfig config = new NetworkConfig.Builder().build();
        Assertions.assertNull(config);

        config = new NetworkConfig.Builder().name("hello").aliases(Arrays.asList("alias1", "alias2")).build();
        Assertions.assertTrue(config.isCustomNetwork());
        Assertions.assertEquals("hello",config.getCustomNetwork());
        Assertions.assertEquals(2,config.getAliases().size());
    }
}
