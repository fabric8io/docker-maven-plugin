package io.fabric8.maven.docker.config;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ImageConfiguration}, focusing on {@link ImageConfiguration#getDependencies()}
 * (and its per-source helpers) plus a few plain accessors that were previously uncovered.
 */
class ImageConfigurationTest {

    @Test
    void getDependenciesIsEmptyWithoutRunConfiguration() {
        // No run configuration -> getRunConfiguration() falls back to RunImageConfiguration.DEFAULT,
        // which has no volumes, links, container network or dependsOn.
        ImageConfiguration config = new ImageConfiguration.Builder().name("test-image").build();

        Assertions.assertTrue(config.getDependencies().isEmpty());
    }

    @Test
    void getDependenciesCollectsVolumesLinksAndContainerNetwork() {
        RunVolumeConfiguration volumes = new RunVolumeConfiguration.Builder()
                .from(Arrays.asList("data-image", "config-image"))
                .build();
        RunImageConfiguration run = new RunImageConfiguration.Builder()
                .volumes(volumes)
                .links(Arrays.asList("db:database", "cache:redis"))
                // "container:<name>" is a standard (non-custom) network that exposes a container alias.
                .network(new NetworkConfig("container:shared"))
                // dependsOn must be ignored while the network is not a custom one.
                .dependsOn(Arrays.asList("ignored-when-not-custom"))
                .build();
        ImageConfiguration config = new ImageConfiguration.Builder().name("test-image").runConfig(run).build();

        List<String> dependencies = config.getDependencies();

        // Order matches getDependencies(): volumes, then link targets (part before the last colon),
        // then the container network alias. dependsOn is excluded for non-custom networks.
        Assertions.assertEquals(
                Arrays.asList("data-image", "config-image", "db", "cache", "shared"),
                dependencies);
    }

    @Test
    void getDependenciesUsesDependsOnAndIgnoresLinksForCustomNetwork() {
        RunImageConfiguration run = new RunImageConfiguration.Builder()
                // An unknown network name is treated as a custom network.
                .network(new NetworkConfig("my-custom-net"))
                // Links are only considered for non-custom networks, so these must be ignored.
                .links(Arrays.asList("db:database"))
                .dependsOn(Arrays.asList("svc-a", "svc-b"))
                .build();
        ImageConfiguration config = new ImageConfiguration.Builder().name("test-image").runConfig(run).build();

        // Only dependsOn contributes; a custom network has no container alias.
        Assertions.assertEquals(Arrays.asList("svc-a", "svc-b"), config.getDependencies());
    }

    @Test
    void setAliasUpdatesAlias() {
        ImageConfiguration config = new ImageConfiguration();

        config.setAlias("my-alias");

        Assertions.assertEquals("my-alias", config.getAlias());
    }

    @Test
    void toStringContainsNameAndAlias() {
        ImageConfiguration config = new ImageConfiguration.Builder()
                .name("my-image")
                .alias("my-alias")
                .build();

        Assertions.assertEquals("ImageConfiguration {name='my-image', alias='my-alias'}", config.toString());
    }
}
