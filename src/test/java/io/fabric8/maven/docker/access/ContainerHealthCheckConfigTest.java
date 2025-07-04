package io.fabric8.maven.docker.access;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.fabric8.maven.docker.config.Arguments;
import io.fabric8.maven.docker.config.HealthCheckConfiguration;
import io.fabric8.maven.docker.config.HealthCheckMode;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ContainerHealthCheckConfigTest {
    
    @Test
    void testNanoSecondTransition() {
        HealthCheckConfiguration hcc = new HealthCheckConfiguration.Builder()
            .mode(HealthCheckMode.cmd)
            .cmd(Arguments.Builder.get().withShell("test").build())
            .timeout("3s")
            .interval("2s")
            .startPeriod("1s")
            .build();
        
        ContainerHealthCheckConfig chcc = new ContainerHealthCheckConfig(hcc);
        JsonObject sut = chcc.toJsonObject();
        
        Assertions.assertEquals(3000000000L, sut.get("Timeout").getAsLong());
        Assertions.assertEquals(2000000000L, sut.get("Interval").getAsLong());
        Assertions.assertEquals(1000000000L, sut.get("StartPeriod").getAsLong());
    }
    
    
    // Zero as duration option means "inherit" the options from the image / base image options
    // See also https://docs.docker.com/engine/api/latest/#tag/Container/operation/ContainerCreate
    @Test
    void testZeroAsDuration() {
        HealthCheckConfiguration hcc = new HealthCheckConfiguration.Builder()
            .mode(HealthCheckMode.cmd)
            .cmd(Arguments.Builder.get().withShell("test").build())
            .timeout("0")
            .interval("0")
            .startPeriod("0")
            .build();
        
        ContainerHealthCheckConfig chcc = new ContainerHealthCheckConfig(hcc);
        JsonObject sut = chcc.toJsonObject();
        
        Assertions.assertEquals(0, sut.get("Timeout").getAsLong());
        Assertions.assertEquals(0, sut.get("Interval").getAsLong());
        Assertions.assertEquals(0, sut.get("StartPeriod").getAsLong());
    }
    
    @Test
    void testCmdSplitting() {
        HealthCheckConfiguration hcc = new HealthCheckConfiguration.Builder()
            .mode(HealthCheckMode.cmd)
            .cmd(Arguments.Builder.get().withShell("test -f /bin/bash").build())
            .build();
        
        ContainerHealthCheckConfig chcc = new ContainerHealthCheckConfig(hcc);
        JsonObject sut = chcc.toJsonObject();
        
        JsonArray expected = new JsonArray();
        expected.add("CMD");
        expected.add("test");
        expected.add("-f");
        expected.add("/bin/bash");
        
        Assertions.assertEquals(expected, sut.get("Test").getAsJsonArray());
    }
}