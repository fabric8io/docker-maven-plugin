package io.fabric8.maven.docker.config;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import java.util.stream.Stream;

/**
 * Tests the health check configuration
 */
class HealthCheckConfigTest {
    
    static Stream<Object> goodExamples() {
        return Stream.of(
            // Defaulting to cmd mode
            new HealthCheckConfiguration.Builder().cmd(new Arguments("exit 0")).build(),
            new HealthCheckConfiguration.Builder().cmd(new Arguments("exit 0")).retries(1).build(),
            new HealthCheckConfiguration.Builder().cmd(new Arguments("exit 0")).retries(1).interval("2s").build(),
            new HealthCheckConfiguration.Builder().cmd(new Arguments("exit 0")).retries(1).interval("2s").timeout("3s").build(),
            new HealthCheckConfiguration.Builder().mode(HealthCheckMode.none).build(),
        );
    }
    
    @ParameterizedTest
    @MethodSource("goodExamples")
    void goodHealthCheck(HealthCheckConfiguration sut) {
        Assertions.assertDoesNotThrow(sut::validate);
    }
    
    static Stream<Object> badExamples() {
        return Stream.of(
            // No completely empty config is valid
            new HealthCheckConfiguration.Builder().build(),
            
            // When mode is "none" there should be no options nor commands
            new HealthCheckConfiguration.Builder().mode(HealthCheckMode.none).interval("2s").build(),
            new HealthCheckConfiguration.Builder().mode(HealthCheckMode.none).retries(1).build(),
            new HealthCheckConfiguration.Builder().mode(HealthCheckMode.none).timeout("3s").build(),
            new HealthCheckConfiguration.Builder().mode(HealthCheckMode.none).startPeriod("30s").cmd(new Arguments("echo a")).build(),
            new HealthCheckConfiguration.Builder().mode(HealthCheckMode.none).cmd(new Arguments("echo a")).build(),
            
            
            // No empty command when cmd or shell mode
            new HealthCheckConfiguration.Builder().mode(HealthCheckMode.cmd).build(),
            
            // No invalid durations
            new HealthCheckConfiguration.Builder().mode(HealthCheckMode.cmd).cmd(new Arguments("echo a")).interval("1m2h").build(),
            new HealthCheckConfiguration.Builder().mode(HealthCheckMode.cmd).cmd(new Arguments("echo a")).timeout("1m2h").build(),
            new HealthCheckConfiguration.Builder().mode(HealthCheckMode.cmd).cmd(new Arguments("echo a")).startPeriod("1m2h").build(),
            
            // No invalid retries
            new HealthCheckConfiguration.Builder().mode(HealthCheckMode.cmd).cmd(new Arguments("echo a")).retries(-1).build(),
        );
    }
    
    @ParameterizedTest
    @MethodSource("badExamples")
    void badHealthCheck(HealthCheckConfiguration sut) {
        Assertions.assertThrows(IllegalArgumentException.class, sut::validate);
    }
    }

}
