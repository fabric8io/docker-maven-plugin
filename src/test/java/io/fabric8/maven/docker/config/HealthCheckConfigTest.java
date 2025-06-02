package io.fabric8.maven.docker.config;

import io.fabric8.maven.docker.config.HealthCheckConfiguration.DurationParser;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.util.stream.Stream;

/**
 * Tests the health check configuration
 */
class HealthCheckConfigTest {
    
    static Stream<Object> goodExamples() {
        return Stream.of(
            new HealthCheckConfiguration.Builder().mode(HealthCheckMode.cmd).cmd(new Arguments("exit 0")).build(),
            new HealthCheckConfiguration.Builder().mode(HealthCheckMode.cmd).cmd(new Arguments("exit 0")).retries(1).build(),
            new HealthCheckConfiguration.Builder().mode(HealthCheckMode.cmd).cmd(new Arguments("exit 0")).retries(1).interval("2s").build(),
            new HealthCheckConfiguration.Builder().mode(HealthCheckMode.cmd).cmd(new Arguments("exit 0")).retries(1).interval("2s").timeout("3s").build(),
            new HealthCheckConfiguration.Builder().mode(HealthCheckMode.shell).cmd(new Arguments("exit 0")).retries(1).interval("2s").timeout("3s").startPeriod("30s").build(),
            new HealthCheckConfiguration.Builder().mode(HealthCheckMode.shell).cmd(new Arguments("exit 0")).retries(1).interval("2s").timeout("3s").startPeriod("4s").build(),
            new HealthCheckConfiguration.Builder().mode(HealthCheckMode.inherit).retries(1).interval("2s").timeout("3s").startPeriod("4s").build(),
            new HealthCheckConfiguration.Builder().mode(HealthCheckMode.none).build()
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
            
            // No mode given is invalid
            new HealthCheckConfiguration.Builder().interval("2s").build(),
            
            // When mode is "none" there should be no options nor commands
            new HealthCheckConfiguration.Builder().mode(HealthCheckMode.none).interval("2s").build(),
            new HealthCheckConfiguration.Builder().mode(HealthCheckMode.none).retries(1).build(),
            new HealthCheckConfiguration.Builder().mode(HealthCheckMode.none).timeout("3s").build(),
            new HealthCheckConfiguration.Builder().mode(HealthCheckMode.none).startPeriod("30s").cmd(new Arguments("echo a")).build(),
            new HealthCheckConfiguration.Builder().mode(HealthCheckMode.none).cmd(new Arguments("echo a")).build(),
            
            // No empty command when cmd or shell mode
            new HealthCheckConfiguration.Builder().mode(HealthCheckMode.cmd).build(),
            new HealthCheckConfiguration.Builder().mode(HealthCheckMode.shell).build(),
            
            // No command when inherit mode
            new HealthCheckConfiguration.Builder().mode(HealthCheckMode.inherit).cmd(new Arguments("echo a")).build(),
            
            // No invalid durations
            new HealthCheckConfiguration.Builder().mode(HealthCheckMode.cmd).cmd(new Arguments("echo a")).interval("1m2h").build(),
            new HealthCheckConfiguration.Builder().mode(HealthCheckMode.cmd).cmd(new Arguments("echo a")).timeout("1m2h").build(),
            new HealthCheckConfiguration.Builder().mode(HealthCheckMode.cmd).cmd(new Arguments("echo a")).startPeriod("1m2h").build(),
            new HealthCheckConfiguration.Builder().mode(HealthCheckMode.shell).cmd(new Arguments("echo a")).interval("1m2h").build(),
            new HealthCheckConfiguration.Builder().mode(HealthCheckMode.shell).cmd(new Arguments("echo a")).timeout("1m2h").build(),
            new HealthCheckConfiguration.Builder().mode(HealthCheckMode.shell).cmd(new Arguments("echo a")).startPeriod("1m2h").build(),
            new HealthCheckConfiguration.Builder().mode(HealthCheckMode.inherit).interval("1m2h").build(),
            new HealthCheckConfiguration.Builder().mode(HealthCheckMode.inherit).timeout("1m2h").build(),
            new HealthCheckConfiguration.Builder().mode(HealthCheckMode.inherit).startPeriod("1m2h").build(),
            
            // No invalid retries
            new HealthCheckConfiguration.Builder().mode(HealthCheckMode.cmd).cmd(new Arguments("echo a")).retries(-1).build(),
            new HealthCheckConfiguration.Builder().mode(HealthCheckMode.shell).cmd(new Arguments("echo a")).retries(-1).build()
        );
    }
    
    @ParameterizedTest
    @MethodSource("badExamples")
    void badHealthCheck(HealthCheckConfiguration sut) {
        Assertions.assertThrows(IllegalArgumentException.class, sut::validate);
    }
    
    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class DurationParserTest {
    
        Stream<org.junit.jupiter.params.provider.Arguments> goodExamples() {
            return Stream.of(
                org.junit.jupiter.params.provider.Arguments.of("0", Duration.ZERO),
                org.junit.jupiter.params.provider.Arguments.of("0h30m1s", Duration.ofMinutes(30).plusSeconds(1)),
                org.junit.jupiter.params.provider.Arguments.of("1h30m1s", Duration.ofHours(1).plusMinutes(30).plusSeconds(1)),
                org.junit.jupiter.params.provider.Arguments.of("1h1s", Duration.ofHours(1).plusSeconds(1)),
                org.junit.jupiter.params.provider.Arguments.of("01h01s", Duration.ofHours(1).plusSeconds(1)),
                org.junit.jupiter.params.provider.Arguments.of("10h30m", Duration.ofHours(10).plusMinutes(30)),
                org.junit.jupiter.params.provider.Arguments.of("20h30m", Duration.ofHours(20).plusMinutes(30)),
                org.junit.jupiter.params.provider.Arguments.of("23h30m", Duration.ofHours(23).plusMinutes(30)),
                org.junit.jupiter.params.provider.Arguments.of("30m1s", Duration.ofMinutes(30).plusSeconds(1)),
                org.junit.jupiter.params.provider.Arguments.of("1s", Duration.ofSeconds(1)),
                org.junit.jupiter.params.provider.Arguments.of("10ms", Duration.ofMillis(10)),
                org.junit.jupiter.params.provider.Arguments.of("30m30ms", Duration.ofMinutes(30).plusMillis(30)),
                org.junit.jupiter.params.provider.Arguments.of("30m1us", Duration.ofMinutes(30).plusNanos(1000)),
                org.junit.jupiter.params.provider.Arguments.of("1h30m1.2s", Duration.ofHours(1).plusMinutes(30).plusSeconds(1).plusMillis(200)),
                org.junit.jupiter.params.provider.Arguments.of("1.234s", Duration.ofSeconds(1).plusMillis(234))
            );
        }
        
        @ParameterizedTest
        @MethodSource("goodExamples")
        void success(String sut, Duration expected) {
            Assertions.assertTrue(DurationParser.matchesDuration(sut));
            Assertions.assertEquals(expected, DurationParser.parseDuration(sut));
        }
        
        @Test
        void nullSafe() {
            Assertions.assertFalse(DurationParser.matchesDuration(null));
            Assertions.assertNull(DurationParser.parseDuration(null));
        }
        
        @ParameterizedTest
        @ValueSource(strings = {"", "   ", "test", "1d1m", "1s1m", "1.2h", "24h1m", "1h60m", "3m100s", "3m1s2000ms", "1ns"})
        void failure(String sut) {
            Assertions.assertFalse(DurationParser.matchesDuration(sut));
            Assertions.assertNull(DurationParser.parseDuration(sut));
        }
    
    }

}
