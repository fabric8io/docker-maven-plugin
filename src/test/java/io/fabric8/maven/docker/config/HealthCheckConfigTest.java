package io.fabric8.maven.docker.config;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests the health check configuration
 */
class HealthCheckConfigTest {

    @Test
    void testGoodHealthCheck1() {
        new HealthCheckConfiguration.Builder()
            .cmd(new Arguments("exit 0"))
            .build()
            .validate();
    }

    @Test
    void testGoodHealthCheck2() {
        new HealthCheckConfiguration.Builder()
            .cmd(new Arguments("exit 0"))
            .retries(1)
            .build()
            .validate();
    }

    @Test
    void testGoodHealthCheck3() {
        new HealthCheckConfiguration.Builder()
            .cmd(new Arguments("exit 0"))
            .retries(1)
            .interval("2s")
            .build()
            .validate();
    }

    @Test
    void testGoodHealthCheck4() {
        new HealthCheckConfiguration.Builder()
            .cmd(new Arguments("exit 0"))
            .retries(1)
            .interval("2s")
            .timeout("3s")
            .build()
            .validate();
    }

    @Test
    void testGoodHealthCheck5() {
        new HealthCheckConfiguration.Builder()
            .mode(HealthCheckMode.cmd)
            .cmd(new Arguments("exit 0"))
            .retries(1)
            .interval("2s")
            .timeout("3s")
            .startPeriod("30s")
            .build()
            .validate();
    }

    @Test
    void testGoodHealthCheck6() {
        new HealthCheckConfiguration.Builder()
            .mode(HealthCheckMode.cmd)
            .cmd(new Arguments("exit 0"))
            .retries(1)
            .interval("2s")
            .timeout("3s")
            .startPeriod("4s")
            .build()
            .validate();
    }

    @Test
    void testGoodHealthCheck7() {
        new HealthCheckConfiguration.Builder()
            .mode(HealthCheckMode.none)
            .build()
            .validate();
    }

    @Test
    void testBadHealthCheck1() {
        HealthCheckConfiguration healthCheckConfiguration = new HealthCheckConfiguration.Builder()
            .mode(HealthCheckMode.none)
            .interval("2s")
            .build();
        Assertions.assertThrows(IllegalArgumentException.class, healthCheckConfiguration::validate);
    }

    @Test
    void testBadHealthCheck2() {
        HealthCheckConfiguration healthCheckConfiguration = new HealthCheckConfiguration.Builder()
            .mode(HealthCheckMode.none)
            .retries(1)
            .build();
        Assertions.assertThrows(IllegalArgumentException.class, healthCheckConfiguration::validate);

    }

    @Test
    void testBadHealthCheck3() {
        HealthCheckConfiguration healthCheckConfiguration = new HealthCheckConfiguration.Builder()
            .mode(HealthCheckMode.none)
            .timeout("3s")
            .build();
        Assertions.assertThrows(IllegalArgumentException.class, healthCheckConfiguration::validate);
    }

    @Test
    void testBadHealthCheck4() {
        HealthCheckConfiguration healthCheckConfiguration = new HealthCheckConfiguration.Builder()
            .mode(HealthCheckMode.none)
            .startPeriod("30s")
            .cmd(new Arguments("echo a"))
            .build();
        Assertions.assertThrows(IllegalArgumentException.class, healthCheckConfiguration::validate);
    }

    @Test
    void testBadHealthCheck5() {
        HealthCheckConfiguration healthCheckConfiguration = new HealthCheckConfiguration.Builder()
            .mode(HealthCheckMode.none)
            .cmd(new Arguments("echo a"))
            .build();
        Assertions.assertThrows(IllegalArgumentException.class, healthCheckConfiguration::validate);
    }

    @Test
    void testBadHealthCheck6() {
        HealthCheckConfiguration healthCheckConfiguration = new HealthCheckConfiguration.Builder()
            .build();
        Assertions.assertThrows(IllegalArgumentException.class, healthCheckConfiguration::validate);
    }

    @Test
    void testBadHealthCheck7() {
        HealthCheckConfiguration healthCheckConfiguration = new HealthCheckConfiguration.Builder()
            .mode(HealthCheckMode.cmd)
            .build();
        Assertions.assertThrows(IllegalArgumentException.class, healthCheckConfiguration::validate);
    }

}
