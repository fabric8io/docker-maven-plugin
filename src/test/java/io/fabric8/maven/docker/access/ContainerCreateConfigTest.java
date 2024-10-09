package io.fabric8.maven.docker.access;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import io.fabric8.maven.docker.config.Arguments;
import io.fabric8.maven.docker.config.HealthCheckConfiguration;
import io.fabric8.maven.docker.config.HealthCheckMode;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.File;
import java.io.IOException;
import java.util.*;

import io.fabric8.maven.docker.util.JsonFactory;

import static org.junit.jupiter.api.Assertions.assertTrue;

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
class ContainerCreateConfigTest {

    @Test
    void testEnvironment() throws Exception {
        ContainerCreateConfig cc = new ContainerCreateConfig("testImage");
        Map<String, String> envMap = getEnvMap();
        cc.environment(copyPropsToFile(), envMap, Collections.<String, String>emptyMap());
        JsonArray env = getEnvArray(cc);
        Assertions.assertNotNull(env);
        Assertions.assertEquals(6, env.size());
        List<String> envAsString = convertToList(env);
        assertTrue(envAsString.contains("JAVA_OPTS=-Xmx512m"));
        assertTrue(envAsString.contains("TEST_SERVICE=SECURITY"));
        assertTrue(envAsString.contains("EXTERNAL_ENV=TRUE"));
        assertTrue(envAsString.contains("TEST_HTTP_ADDR=${docker.container.consul.ip}"));
        assertTrue(envAsString.contains("TEST_CONSUL_IP=+${docker.container.consul.ip}:8080"));
        assertTrue(envAsString.contains("TEST_CONSUL_IP_WITHOUT_DELIM=${docker.container.consul.ip}:8225"));
    }

    @Test
    void testEnvironmentEmptyPropertiesFile() {
        ContainerCreateConfig cc = new ContainerCreateConfig("testImage");
        cc.environment(null, getEnvMap(),Collections.<String, String>emptyMap());
        JsonArray env = getEnvArray(cc);
        Assertions.assertEquals(5, env.size());
    }

    @ParameterizedTest
    @CsvSource({
        "C:\\this\\is\\my\\path:/data,    /data",
        "/home/user:/user,                /user",
        "C:\\this\\too:/data:ro,          /data",
        "C:\\this\\is\\my\\path:C:\\data, C:\\data", // Tests #1713
        // without host binding
        "/data,                           /data",
        "C:\\data,                        C:\\data"
    })
    void testBind(String binding, String expectedContainerPath) {
        ContainerCreateConfig cc = new ContainerCreateConfig("testImage");
        cc.binds(Collections.singletonList(binding));

        JsonObject volumes = (JsonObject) JsonFactory.newJsonObject(cc.toJson()).get("Volumes");
        Assertions.assertEquals(1, volumes.size());
        assertTrue(volumes.has(expectedContainerPath));
    }


    @Test
    void testNullEnvironment() {
        ContainerCreateConfig cc= new ContainerCreateConfig("testImage");
        cc.environment(null,null,Collections.<String, String>emptyMap());
        JsonObject config = JsonFactory.newJsonObject(cc.toJson());
        Assertions.assertFalse(config.has("Env"));
    }

    @Test
    void testEnvNoMap() throws IOException {
        ContainerCreateConfig cc= new ContainerCreateConfig("testImage");
        cc.environment(copyPropsToFile(),null,Collections.<String, String>emptyMap());
        JsonArray env = getEnvArray(cc);
        Assertions.assertEquals(2, env.size());
        List<String> envAsString = convertToList(env);
        assertTrue(envAsString.contains("EXTERNAL_ENV=TRUE"));
    }

    @Test
    void testNoPropFile() {
        ContainerCreateConfig cc= new ContainerCreateConfig("testImage");
        Map<String, String> mavenProps = Collections.emptyMap();
        Assertions.assertThrows(IllegalArgumentException.class, ()-> cc.environment("/not/really/a/file",null, mavenProps));
    }

    @Test
    void platform() {
        ContainerCreateConfig cc= new ContainerCreateConfig("testImage", "linux/arm64");
        Assertions.assertEquals("linux/arm64",  cc.getPlatform());
    }

    @Test
    void testHealthCheckIsFullyPresent() {
        ContainerCreateConfig cc = new ContainerCreateConfig("testImage");
        HealthCheckConfiguration healthCheckConfiguration = new HealthCheckConfiguration.Builder()
                .cmd(new Arguments(Arrays.asList("CMD-SHELL", "some command 2>&1")))
                .startPeriod("1s")
                .interval("500ms")
                .timeout("2s")
                .retries(20)
                .mode(HealthCheckMode.cmd)
                .build();
        cc.healthcheck(healthCheckConfiguration);
        JsonObject config = JsonFactory.newJsonObject(cc.toJson());
        JsonObject healthCheck = (JsonObject) config.get("Healthcheck");
        Assertions.assertEquals(2, healthCheck.get("Test")
                .getAsJsonArray().size());
        Assertions.assertEquals("some command 2>&1", healthCheck.get("Test")
                .getAsJsonArray().get(1).getAsString());
        Assertions.assertEquals(20, healthCheck.get("Retries").getAsInt());
        Assertions.assertEquals(500000000, healthCheck.get("Interval").getAsInt());
        Assertions.assertEquals(1000000000, healthCheck.get("StartPeriod").getAsInt());
        Assertions.assertEquals(2000000000, healthCheck.get("Timeout").getAsInt());
    }

    @Test
    void testHealthCheckIsInherited() {
        ContainerCreateConfig cc = new ContainerCreateConfig("testImage");
        HealthCheckConfiguration healthCheckConfiguration = new HealthCheckConfiguration.Builder()
                .cmd(new Arguments(Arrays.asList()))
                .startPeriod("1s")
                .mode(HealthCheckMode.cmd)
                .build();
        cc.healthcheck(healthCheckConfiguration);
        JsonObject config = JsonFactory.newJsonObject(cc.toJson());
        JsonObject healthCheck = (JsonObject) config.get("Healthcheck");
        Assertions.assertEquals(0, healthCheck.get("Test")
                .getAsJsonArray().size());
        Assertions.assertNull(healthCheck.get("Retries"));
        Assertions.assertNull(healthCheck.get("Interval"));
        Assertions.assertEquals(1000000000, healthCheck.get("StartPeriod").getAsInt());
        Assertions.assertNull(healthCheck.get("Timeout"));
    }

    @Test
    void testHealthCheckIsDisabled() {
        ContainerCreateConfig cc = new ContainerCreateConfig("testImage");
        HealthCheckConfiguration healthCheckConfiguration = new HealthCheckConfiguration.Builder()
                .cmd(new Arguments(Arrays.asList("NONE")))
                .mode(HealthCheckMode.none)
                .retries(2)
                .build();
        cc.healthcheck(healthCheckConfiguration);
        JsonObject config = JsonFactory.newJsonObject(cc.toJson());
        JsonObject healthCheck = (JsonObject) config.get("Healthcheck");
        Assertions.assertEquals(1, healthCheck.get("Test")
                .getAsJsonArray().size());
        Assertions.assertEquals("NONE", healthCheck.get("Test")
                .getAsJsonArray().get(0).getAsString());
        Assertions.assertNull(healthCheck.get("Retries"));
        Assertions.assertNull(healthCheck.get("Interval"));
        Assertions.assertNull(healthCheck.get("StartPeriod"));
        Assertions.assertNull(healthCheck.get("Timeout"));
    }

    private JsonArray getEnvArray(ContainerCreateConfig cc) {
        JsonObject config = JsonFactory.newJsonObject(cc.toJson());
        return (JsonArray) config.get("Env");
    }

    private String copyPropsToFile() throws IOException {
        File tempFile = File.createTempFile("dockertest", "props");
        FileUtils.copyInputStreamToFile(getClass().getResourceAsStream("test-environment.props"), tempFile);
        return tempFile.getAbsolutePath();
    }


    private List<String> convertToList(JsonArray env) {
        List<String> envAsString = new ArrayList<>();
        for (int i = 0; i < env.size(); i++) {
            envAsString.add(env.get(i).getAsString());
        }
        return envAsString;
    }

    private Map<String, String> getEnvMap() {
        Map<String,String> envMap = new HashMap<>();
        envMap.put("JAVA_OPTS", "-Xmx512m");
        envMap.put("TEST_SERVICE", "LOGGING");
        envMap.put("TEST_HTTP_ADDR", "+${docker.container.consul.ip}");
        envMap.put("TEST_CONSUL_IP", "+${docker.container.consul.ip}:8080");
        envMap.put("TEST_CONSUL_IP_WITHOUT_DELIM", "${docker.container.consul.ip}:8225");
        return envMap;
    }

}