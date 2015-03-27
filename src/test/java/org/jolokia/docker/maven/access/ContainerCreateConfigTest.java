package org.jolokia.docker.maven.access;

import java.io.File;
import java.io.IOException;
import java.util.*;

import org.apache.commons.io.FileUtils;
import org.json.JSONArray;
import org.json.JSONObject;
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
 * @since 27/03/15
 */
public class ContainerCreateConfigTest {

    @Test
    public void testEnvironment() throws Exception {
        ContainerCreateConfig cc = new ContainerCreateConfig("testImage");
        Map<String, String> envMap = getEnvMap();
        cc.environment(copyPropsToFile(), envMap);
        JSONArray env = getEnvArray(cc);
        assertNotNull(env);
        assertEquals(3, env.length());
        List<String> envAsString = convertToList(env);
        assertTrue(envAsString.contains("JAVA_OPTS=-Xmx512m"));
        assertTrue(envAsString.contains("TEST_SERVICE=SECURITY"));
        assertTrue(envAsString.contains("EXTERNAL_ENV=TRUE"));
    }

    @Test
    public void testEnvironmentEmptyPropertiesFile() {
        ContainerCreateConfig cc = new ContainerCreateConfig("testImage");
        cc.environment(null, getEnvMap());
        JSONArray env = getEnvArray(cc);
        assertEquals(2, env.length());
    }

    @Test
    public void testNullEnvironment() {
        ContainerCreateConfig cc= new ContainerCreateConfig("testImage");
        cc.environment(null,null);
        JSONObject config = new JSONObject(cc.toJson());
        assertFalse(config.has("Env"));
    }

    @Test
    public void testEnvNoMap() throws IOException {
        ContainerCreateConfig cc= new ContainerCreateConfig("testImage");
        cc.environment(copyPropsToFile(),null);
        JSONArray env = getEnvArray(cc);
        assertEquals(2, env.length());
        List<String> envAsString = convertToList(env);
        assertTrue(envAsString.contains("EXTERNAL_ENV=TRUE"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNoPropFile() {
        ContainerCreateConfig cc= new ContainerCreateConfig("testImage");
        cc.environment("/not/really/a/file",null);
    }


    private JSONArray getEnvArray(ContainerCreateConfig cc) {
        JSONObject config = new JSONObject(cc.toJson());
        return (JSONArray) config.get("Env");
    }

    private String copyPropsToFile() throws IOException {
        File tempFile = File.createTempFile("dockertest", "props");
        FileUtils.copyInputStreamToFile(getClass().getResourceAsStream("test-environment.props"), tempFile);
        return tempFile.getAbsolutePath();
    }


    private List<String> convertToList(JSONArray env) {
        List<String> envAsString = new ArrayList<>();
        for (int i = 0; i < env.length(); i++) {
            envAsString.add(env.getString(i));
        }
        return envAsString;
    }

    private Map<String, String> getEnvMap() {
        Map<String,String> envMap = new HashMap<>();
        envMap.put("JAVA_OPTS", "-Xmx512m");
        envMap.put("TEST_SERVICE", "LOGGING");
        return envMap;
    }

}