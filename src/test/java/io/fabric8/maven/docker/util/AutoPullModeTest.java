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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static io.fabric8.maven.docker.util.AutoPullMode.*;


/**
 * @author roland
 * @since 01/03/15
 */
class AutoPullModeTest {

    @Test
    void simple() {
        Assertions.assertEquals(ON, fromString("on"));
        Assertions.assertEquals(ON, fromString("true"));
        Assertions.assertEquals(OFF, fromString("Off"));
        Assertions.assertEquals(OFF, fromString("falsE"));
        Assertions.assertEquals(ALWAYS, fromString("alWays"));
    }

    @Test
    void unknown() {
        Assertions.assertThrows( IllegalArgumentException.class, () ->fromString("unknown"));
    }
}
