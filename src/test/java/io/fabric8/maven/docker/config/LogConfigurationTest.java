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
        LogConfiguration cfg = new LogConfiguration.Builder()
                .enabled(true)
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

    @Test
    public void testDisabled() {
        LogConfiguration cfg = new LogConfiguration.Builder()
                .color("red")
                .enabled(false)
                .build();
        assertFalse(cfg.isEnabled());
        assertFalse(cfg.isActivated());
        assertEquals("red", cfg.getColor());
    }
}
