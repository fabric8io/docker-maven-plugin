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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import static io.fabric8.maven.docker.config.CleanupMode.*;


/**
 * @author roland
 * @since 01/03/16
 */
class CleanupModeTest {

    @Test
    void parse() {

        Object[] data = {
            null, TRY_TO_REMOVE,
            "try", TRY_TO_REMOVE,
            "FaLsE", NONE,
            "NONE", NONE,
            "true", REMOVE,
            "removE", REMOVE
        };

        for (int i = 0; i < data.length; i += 2) {
            Assertions.assertEquals(data[i+1],CleanupMode.parse((String) data[i]));
        }
    }

    @Test
    void invalid() {
        String message= Assertions.assertThrows(IllegalArgumentException.class, () ->CleanupMode.parse("blub")).getMessage();
        Assertions.assertTrue(message.contains("blub"));
        Assertions.assertTrue(message.contains("try"));
        Assertions.assertTrue(message.contains("none"));
        Assertions.assertTrue(message.contains("remove"));
    }
}
