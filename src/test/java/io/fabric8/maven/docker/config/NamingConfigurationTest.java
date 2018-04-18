package io.fabric8.maven.docker.config;

import com.google.common.collect.ImmutableSet;
import io.fabric8.maven.docker.model.Container;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;
import java.util.Date;

/**
 * @author marcus
 * @since 1.0.0
 */
public class NamingConfigurationTest {

    @Test
    public void testDefault() {
        final NamingConfiguration namingConfiguration = NamingConfiguration.create(null, null, "jolokia/jolokia_demo", "nameAlias", new Date(123456), Collections.<Container>emptyList());
        Assert.assertEquals("jolokia_demo-1", namingConfiguration.calculateContainerName());
    }

    @Test
    public void testBackwardCompatibilityAlias() {
        final NamingConfiguration namingConfiguration = NamingConfiguration.create(null, RunImageConfiguration.NamingStrategy.alias, "jolokia/jolokia_demo", "nameAlias", new Date(123456), Collections.<Container>emptyList());
        Assert.assertEquals("nameAlias", namingConfiguration.calculateContainerName());
    }

    @Test
    public void testBackwardCompatibilityNone() {
        final NamingConfiguration namingConfiguration = NamingConfiguration.create(null, RunImageConfiguration.NamingStrategy.none, "jolokia/jolokia_demo", "nameAlias", new Date(123456), Collections.<Container>emptyList());
        Assert.assertEquals("jolokia_demo-1", namingConfiguration.calculateContainerName());
    }

    @Test
    public void testAlias() {
        final NamingConfiguration namingConfiguration = NamingConfiguration.create("%a", null, "jolokia/jolokia_demo", "nameAlias", new Date(123456), Collections.<Container>emptyList());
        Assert.assertEquals("nameAlias", namingConfiguration.calculateContainerName());
    }

    @Test
    public void testTimestamp() {
        final NamingConfiguration namingConfiguration = NamingConfiguration.create("%t", null, "jolokia/jolokia_demo", "nameAlias", new Date(123456), Collections.<Container>emptyList());
        Assert.assertEquals("123456", namingConfiguration.calculateContainerName());
    }

    @Test
    public void testImageName() {
        final NamingConfiguration namingConfiguration = NamingConfiguration.create("%n", null, "jolokia/jolokia_demo", "nameAlias", new Date(123456), Collections.<Container>emptyList());
        Assert.assertEquals("jolokia_demo", namingConfiguration.calculateContainerName());
    }

    @Test
    public void testIndex() {
        final NamingConfiguration namingConfiguration = NamingConfiguration.create("%i", null, "jolokia/jolokia_demo", "nameAlias", new Date(123456), Collections.<Container>emptyList());
        Assert.assertEquals("1", namingConfiguration.calculateContainerName());
    }

    @Test
    public void testAll() {
        final NamingConfiguration namingConfiguration = NamingConfiguration.create("prefix-%i-%a-%n-%t-postfix", null, "jolokia/jolokia_demo", "nameAlias", new Date(123456), Collections.<Container>emptyList());
        Assert.assertEquals("prefix-1-nameAlias-jolokia_demo-123456-postfix", namingConfiguration.calculateContainerName());
    }

    @Test
    public void testIndexAdvanced() {
        final NamingConfiguration namingConfiguration = new NamingConfiguration("container-%i", "jolokia/jolokia_demo", "nameAlias", "123456",
                Collections.singleton("container-1"));
        Assert.assertEquals("container-2", namingConfiguration.calculateContainerName());
    }

    @Test
    public void testCalculateLastContainerName() {
        final NamingConfiguration namingConfiguration = new NamingConfiguration("container-%i", "jolokia/jolokia_demo", "nameAlias", "123456",
                Collections.singleton("container-1"));
        Assert.assertEquals("container-1", namingConfiguration.calculateLastContainerName());
    }

    @Test
    public void testCalculateLastContainerNameSecond() {
        final NamingConfiguration namingConfiguration = new NamingConfiguration("container-%i", "jolokia/jolokia_demo", "nameAlias", "123456",
                ImmutableSet.of("container-1", "container-2"));
        Assert.assertEquals("container-2", namingConfiguration.calculateLastContainerName());
    }

}
