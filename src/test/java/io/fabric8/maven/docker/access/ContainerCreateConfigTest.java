package io.fabric8.maven.docker.access;

import java.io.File;
import java.io.IOException;
import java.util.*;

import org.apache.commons.io.FileUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
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

    @After
    public void tearDown() throws Exception {
        System.clearProperty("docker.runEnv.otherProp");
    }

    @Test
    public void testEnvironment() throws Exception {
        ContainerCreateConfig cc = new ContainerCreateConfig("testImage");
        Map<String, String> envMap = getEnvMap();
        cc.environment(copyPropsToFile(), envMap, Collections.<String, String>emptyMap());
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
        cc.environment(null, getEnvMap(),Collections.<String, String>emptyMap());
        JSONArray env = getEnvArray(cc);
        assertEquals(2, env.length());
    }

    @Test
    public void testBind() {
        String[] testData = new String[] {
            "c:\\this\\is\\my\\path:/data", "/data",
            "/home/user:/user", "/user",
            "c:\\this\\too:/data:ro", "/data"};
        for (int i = 0; i < testData.length; i += 2) {
            ContainerCreateConfig cc = new ContainerCreateConfig("testImage");
            cc.binds(Arrays.asList(testData[i]));

            JSONObject volumes = (JSONObject) new JSONObject(cc.toJson()).get("Volumes");
            assertEquals(1, volumes.length());
            assertTrue(volumes.has(testData[i+1]));
        }
    }


    @Test
    public void testNullEnvironment() {
        ContainerCreateConfig cc= new ContainerCreateConfig("testImage");
        cc.environment(null,null,Collections.<String, String>emptyMap());
        JSONObject config = new JSONObject(cc.toJson());
        assertFalse(config.has("Env"));
    }

    @Test
    public void testEnvNoMap() throws IOException {
        ContainerCreateConfig cc= new ContainerCreateConfig("testImage");
        cc.environment(copyPropsToFile(),null,Collections.<String, String>emptyMap());
        JSONArray env = getEnvArray(cc);
        assertEquals(2, env.length());
        List<String> envAsString = convertToList(env);
        assertTrue(envAsString.contains("EXTERNAL_ENV=TRUE"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNoPropFile() {
        ContainerCreateConfig cc= new ContainerCreateConfig("testImage");
        cc.environment("/not/really/a/file",null,Collections.<String, String>emptyMap());
    }

    @Test
    public void testEnvFromMavenAndSystemProperties() throws Exception {
        ContainerCreateConfig cc = new ContainerCreateConfig("testImage");

        Map<String, String> envMap = getEnvMap();
        Map<String, String> mavenProps = new HashMap<>();
        mavenProps.put("docker.runEnv.http_proxy", "http://proxy.example.com:8080");
        mavenProps.put("docker.buildArg.https_proxy", "ignored");

        cc.environment(copyPropsToFile(), envMap, mavenProps);

        JSONArray env = getEnvArray(cc);
        assertNotNull(env);
        assertEquals(4, env.length());
        List<String> envAsString = convertToList(env);
        assertTrue(envAsString.contains("JAVA_OPTS=-Xmx512m"));
        assertTrue(envAsString.contains("TEST_SERVICE=SECURITY"));
        assertTrue(envAsString.contains("EXTERNAL_ENV=TRUE"));
        assertTrue(envAsString.contains("http_proxy=http://proxy.example.com:8080"));

        System.setProperty("docker.runEnv.otherProp", "testvalue");
        cc.environment(copyPropsToFile(), envMap, mavenProps);

        env = getEnvArray(cc);
        assertNotNull(env);
        assertEquals(5, env.length());
        envAsString = convertToList(env);
        assertTrue(envAsString.contains("JAVA_OPTS=-Xmx512m"));
        assertTrue(envAsString.contains("TEST_SERVICE=SECURITY"));
        assertTrue(envAsString.contains("EXTERNAL_ENV=TRUE"));
        assertTrue(envAsString.contains("http_proxy=http://proxy.example.com:8080"));
        assertTrue(envAsString.contains("otherProp=testvalue"));
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