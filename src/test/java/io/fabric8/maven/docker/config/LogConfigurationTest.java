package io.fabric8.maven.docker.config;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests LogConfiguration
 */
public class LogConfigurationTest {
    @Test
    public void testDefaultConfiguration() {
        LogConfiguration cfg = LogConfiguration.DEFAULT;
        assertNull(cfg.isEnabled());
        assertFalse(cfg.isActivated());
    }

    @Test
    public void testEmptyBuiltConfiguration() {
        LogConfiguration cfg = new LogConfiguration.Builder()
                .build();
        assertNull(cfg.isEnabled());
        assertFalse(cfg.isActivated());
    }

    @Test
    public void testNonEmptyBuiltConfiguration() {
        LogConfiguration cfg = new LogConfiguration.Builder()
                .file("test")
                .build();
        assertNull(cfg.isEnabled());
        assertTrue(cfg.isActivated());

        cfg = new LogConfiguration.Builder()
                .color("test")
                .build();
        assertNull(cfg.isEnabled());
        assertTrue(cfg.isActivated());

        cfg = new LogConfiguration.Builder()
                .logDriverName("test")
                .build();
        assertNull(cfg.isEnabled());
        assertTrue(cfg.isActivated());

        cfg = new LogConfiguration.Builder()
                .date("1234")
                .build();
        assertNull(cfg.isEnabled());
        assertTrue(cfg.isActivated());
    }

    @Test
    public void testEnabled() {
        for (Boolean enabled : new Boolean[]{Boolean.TRUE, new Boolean(true)}) {
            LogConfiguration cfg = new LogConfiguration.Builder()
                .enabled(enabled)
                .build();
            assertTrue(cfg.isEnabled());
            assertTrue(cfg.isActivated());

            cfg = new LogConfiguration.Builder()
                .enabled(true)
                .color("red")
                .build();
            assertTrue(cfg.isEnabled());
            assertTrue(cfg.isActivated());
            assertEquals("red", cfg.getColor());
        }
    }

    @Test
    public void testDisabled() {
        for (Boolean disabled : new Boolean[]{Boolean.FALSE, new Boolean(false)}) {
            LogConfiguration cfg = new LogConfiguration.Builder()
                .color("red")
                .enabled(disabled)
                .build();
            assertFalse(cfg.isEnabled());
            assertFalse(cfg.isActivated());
            assertEquals("red", cfg.getColor());
        }
    }
}
