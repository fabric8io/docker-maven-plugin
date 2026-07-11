package io.fabric8.maven.docker;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Verifies that {@link RunMojo} follows container logs by default (unlike {@code docker:start}),
 * while still honouring an explicit {@code <follow>} configuration element. See #1797.
 */
class RunMojoTest {

    @Test
    @DisplayName("docker:run follows logs by default (default true)")
    void runFollowDefaultsToTrue() {
        Assertions.assertTrue(new RunMojo().followLogs());
    }

    @Test
    @DisplayName("docker:run honours the <follow> configuration element")
    void runHonoursConfiguredFollow() {
        RunMojo mojo = new RunMojo();

        mojo.follow = Boolean.FALSE;
        Assertions.assertFalse(mojo.followLogs());

        mojo.follow = Boolean.TRUE;
        Assertions.assertTrue(mojo.followLogs());
    }
}
