package io.fabric8.maven.docker.config;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;



/**
 * Tests LogConfiguration
 */
class LogConfigurationTest {
    @Test
    void testDefaultConfiguration() {
        LogConfiguration cfg = LogConfiguration.DEFAULT;
        Assertions.assertNull(cfg.isEnabled());
        Assertions.assertFalse(cfg.isActivated());
    }

    @Test
    void testEmptyBuiltConfiguration() {
        LogConfiguration cfg = new LogConfiguration.Builder()
                .build();
        Assertions.assertNull(cfg.isEnabled());
        Assertions.assertFalse(cfg.isActivated());
    }

    @Test
    void testNonEmptyBuiltConfiguration() {
        LogConfiguration cfg = new LogConfiguration.Builder()
                .file("test")
                .build();
        Assertions.assertNull(cfg.isEnabled());
        Assertions.assertTrue(cfg.isActivated());

        cfg = new LogConfiguration.Builder()
                .color("test")
                .build();
        Assertions.assertNull(cfg.isEnabled());
        Assertions.assertTrue(cfg.isActivated());

        cfg = new LogConfiguration.Builder()
                .logDriverName("test")
                .build();
        Assertions.assertNull(cfg.isEnabled());
        Assertions.assertTrue(cfg.isActivated());

        cfg = new LogConfiguration.Builder()
                .date("1234")
                .build();
        Assertions.assertNull(cfg.isEnabled());
        Assertions.assertTrue(cfg.isActivated());
    }

    @Test
    void testEnabled() {
        for (Boolean enabled : new Boolean[]{Boolean.TRUE, Boolean.TRUE }) {
            LogConfiguration cfg = new LogConfiguration.Builder()
                .enabled(enabled)
                .build();
            Assertions.assertTrue(cfg.isEnabled());
            Assertions.assertTrue(cfg.isActivated());

            cfg = new LogConfiguration.Builder()
                .enabled(true)
                .color("red")
                .build();
            Assertions.assertTrue(cfg.isEnabled());
            Assertions.assertTrue(cfg.isActivated());
            Assertions.assertEquals("red", cfg.getColor());
        }
    }

    @Test
    void testDisabled() {
        for (Boolean disabled : new Boolean[]{Boolean.FALSE, Boolean.FALSE }) {
            LogConfiguration cfg = new LogConfiguration.Builder()
                .color("red")
                .enabled(disabled)
                .build();
            Assertions.assertFalse(cfg.isEnabled());
            Assertions.assertFalse(cfg.isActivated());
            Assertions.assertEquals("red", cfg.getColor());
        }
    }
}
