package org.jolokia.docker.maven.model;

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

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author roland
 * @since 16/07/15
 */
public class ContainerTest {

    @Test
    public void details() throws Exception {
        JSONObject data = new JSONObject();
        data.put("Created", "2015-01-06T15:47:31.485331387Z");
        data.put("Id", "1234AF");
        data.put("Name", "/milkman-kindness");
        data.put("Config", new JSONObject("{ 'Image': '9876CE'}"));
        data.put("State", new JSONObject("{'Running' : true }"));
        Container cont = new ContainerDetails(data);
        assertEquals(1420559251485L, cont.getCreated());
        assertEquals("1234AF", cont.getId());
        assertEquals("milkman-kindness", cont.getName());
        assertEquals("9876CE",cont.getImage());
        assertTrue(cont.isRunning());
    }

    @Test
    public void listElement() throws Exception {
        JSONObject data = new JSONObject();
        data.put("Created",1420559251485L);
        data.put("Id", "1234AF");
        data.put("Image", "9876CE");
        data.put("Status", "Up 16 seconds");
        Container cont = new ContainersListElement(data);
        assertEquals(1420559251485L, cont.getCreated());
        assertEquals("1234AF", cont.getId());
        assertEquals("9876CE", cont.getImage());
        assertTrue(cont.isRunning());

    }

    @Test(expected = UnsupportedOperationException.class)
    public void noNameInListElement() {
        new ContainersListElement(new JSONObject()).getName();
    }
}