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

import org.junit.Test;
import org.junit.experimental.ParallelComputer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * @author roland
 * @since 19/07/16
 */
public class UlimitConfigTest {

    @Test
    public void simple() {
        Object[] data = new Object[] {
            "memlock=1024:2048", "memlock", 1024, 2048,
            "memlock=:2048", "memlock", null, 2048,
            "memlock=1024", "memlock", 1024, null,
            "memlock=1024:", "memlock", 1024, null
        };

        for (int i = 0; i < data.length; i+=4) {
            UlimitConfig config = new UlimitConfig(data[0].toString());
            assertEquals(data[1], config.getName());
            assertEquals(data[2], config.getHard());
            assertEquals(data[3], config.getSoft());
        }
    }

    @Test
    public void illegalFormat() {
        String data[] = new String[] {
            "memlock",
            "memlock:1024",
        };

        for (String test : data) {
            try {
                new UlimitConfig(test);
                fail();
            } catch (IllegalArgumentException exp) {
                // expected
            }
        }
    }

    @Test
    public void invalidNumber() {
        String data[] = new String[] {
            "memlock=bla",
            "memlock=bla:blub",
            "memlock=1024:blub"
        };

        for (String test : data) {
            try {
                new UlimitConfig(test);
                fail();
            } catch (NumberFormatException exp) {
                // expected
            }
        }
    }
}
