package io.fabric8.maven.docker;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Verifies that the {@code follow} log-following flag can be driven from the {@code <follow>}
 * configuration element (and not only the {@code docker.follow} system property), and that
 * {@code docker:start} does not follow logs unless configured. See #1797.
 */
class StartMojoTest {

    @Test
    @DisplayName("docker:start does not follow logs unless configured (default false)")
    void startFollowDefaultsToFalse() {
        Assertions.assertFalse(new StartMojo().followLogs());
    }

    @Test
    @DisplayName("docker:start honours the <follow> configuration element")
    void startHonoursConfiguredFollow() {
        StartMojo mojo = new StartMojo();

        mojo.follow = Boolean.TRUE;
        Assertions.assertTrue(mojo.followLogs());

        mojo.follow = Boolean.FALSE;
        Assertions.assertFalse(mojo.followLogs());
    }
}
