package org.jolokia.docker.maven.config.handler;/*
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

import java.util.*;

import org.jolokia.docker.maven.config.*;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author roland
 * @since 05/12/14
 */
public class PropertyConfigHandlerTest {


    private PropertyConfigHandler configHandler;
    private ImageConfiguration imageConfiguration;

    @Before
    public void setUp() throws Exception {
        configHandler = new PropertyConfigHandler();
        imageConfiguration = new ImageConfiguration.Builder().build();
    }

    @Test
    public void testType() throws Exception {
        assertNotNull(configHandler.getType());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testEmpty() throws Exception {
        configHandler.resolve(imageConfiguration,props());
    }

    @Test
    public void testPorts() throws Exception {
        List<ImageConfiguration> configs = configHandler.resolve(
                imageConfiguration,props(
                        "docker.name","demo",
                        "docker.ports.1", "jolokia.port:8080",
                        "docker.ports.2", "9090",
                        "docker.ports.3", "0.0.0.0:80:80"
                                        ));
        assertEquals(1,configs.size());
        RunImageConfiguration runConfig = configs.get(0).getRunConfiguration();
        String[] ports = new ArrayList<String>(runConfig.getPorts()).toArray(new String[0]);
        assertArrayEquals(new String[] {
                "jolokia.port:8080",
                "9090",
                "0.0.0.0:80:80"
        },ports);
        BuildImageConfiguration buildConfig = configs.get(0).getBuildConfiguration();
        ports = new ArrayList<String>(buildConfig.getPorts()).toArray(new String[0]);
        assertArrayEquals(new String[] { "8080","9090","80"},ports);
    }

    @Test
    public void testEnv() throws Exception {
        List<ImageConfiguration> configs = configHandler.resolve(
                imageConfiguration,props(
                        "docker.name","demo",
                        "docker.env.HOME", "/tmp",
                        "docker.env.root.dir", "/bla"
                                        ));

        assertEquals(1,configs.size());
        ImageConfiguration calcConfig = configs.get(0);
        for (Map<String,String> env : new Map[] { calcConfig.getBuildConfiguration().getEnv(),
                                                  calcConfig.getRunConfiguration().getEnv()}) {
            assertEquals(2,env.size());
            assertEquals("/tmp",env.get("HOME"));
            assertEquals("/bla",env.get("root.dir"));
        }
    }

    private Properties props(String ... args) {
        Properties ret = new Properties();
        for (int i = 0; i < args.length; i+= 2) {
            ret.setProperty(args[i],args[i+1]);
        }
        return ret;
    }
}
