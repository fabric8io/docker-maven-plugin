package io.fabric8.maven.docker.util;

import io.fabric8.maven.docker.config.NamingStrategy;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author marcus
 * @since 1.0.0
 */
public class ContainerNameTest {

    @Test
    public void aliasWins() {
        final String calculatedValue = ContainerName.calculate("alias", NamingStrategy.alias, NamingStrategy.auto, "prefix", "test.org/jolokia/jolokia_demo:latest");
        assertEquals("alias", calculatedValue);
    }

    @Test
    public void globalLevelAuto() {
        final String calculatedValue = ContainerName.calculate("alias", null, NamingStrategy.auto, "prefix", "test.org/jolokia/jolokia_demo:latest");
        assertEquals("prefix_jolokia_demo", calculatedValue);
    }

    @Test
    public void imageLevelAuto() {
        final String calculatedValue = ContainerName.calculate("alias", NamingStrategy.auto, NamingStrategy.none, "prefix", "test.org/jolokia/jolokia_demo:latest");
        assertEquals("prefix_jolokia_demo", calculatedValue);
    }

    @Test
    public void globalLevelAlias() {
        final String calculatedValue = ContainerName.calculate("alias", null, NamingStrategy.alias, "prefix", "test.org/jolokia/jolokia_demo:latest");
        assertEquals("alias", calculatedValue);
    }

    @Test(expected = IllegalArgumentException.class)
    public void globalLevelAliasRequiresAlias() {
        ContainerName.calculate( null, null, NamingStrategy.alias, "prefix", "test.org/jolokia/jolokia_demo:latest");
    }

    @Test(expected = IllegalArgumentException.class)
    public void imagelLevelAliasRequiresAlias() {
        ContainerName.calculate( null, NamingStrategy.alias, NamingStrategy.alias, "prefix", "test.org/jolokia/jolokia_demo:latest");
    }

    @Test(expected = IllegalArgumentException.class)
    public void globalLevelAutoRequiresPrefix() {
        ContainerName.calculate( null, null, NamingStrategy.auto, null, "test.org/jolokia/jolokia_demo:latest");
    }

    @Test(expected = IllegalArgumentException.class)
    public void imageLevelAutoRequiresPrefix() {
        ContainerName.calculate( null, NamingStrategy.auto, NamingStrategy.auto, null, "test.org/jolokia/jolokia_demo:latest");
    }

    @Test
    public void globalLevelNone() {
        final String calculatedValue = ContainerName.calculate( null, null, NamingStrategy.none, null, "test.org/jolokia/jolokia_demo:latest");
        assertEquals(calculatedValue, null);
    }

    @Test
    public void imageLevelNone() {
        final String calculatedValue = ContainerName.calculate( null, NamingStrategy.none, null, null, "test.org/jolokia/jolokia_demo:latest");
        assertEquals(calculatedValue, null);
    }
}
