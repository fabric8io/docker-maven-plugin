package io.fabric8.maven.docker.util;

import com.google.common.collect.ImmutableSet;
import io.fabric8.maven.docker.config.RunImageConfiguration;
import io.fabric8.maven.docker.model.Container;
import io.fabric8.maven.docker.util.ContainerNamingUtil;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;
import java.util.Date;

/**
 * @author marcus
 * @since 1.0.0
 */
public class ContainerNamingUtilTest {

    @Test
    public void testDefault() {
        Assert.assertEquals("jolokia_demo-1",
                            ContainerNamingUtil.calculateContainerName(
                                null,
                                "jolokia/jolokia_demo",
                                "nameAlias",
                                new Date(123456),
                                Collections.<String>emptySet()));
    }

    @Test
    public void testAlias() {
        Assert.assertEquals("nameAlias",
                            ContainerNamingUtil.calculateContainerName(
                                "%a",
                                "jolokia/jolokia_demo",
                                "nameAlias",
                                new Date(123456),
                                Collections.<String>emptySet()));
    }

    @Test
    public void testTimestamp() {
        Assert.assertEquals("123456",
                            ContainerNamingUtil.calculateContainerName(
                                "%t",
                                "jolokia/jolokia_demo",
                                "nameAlias",
                                new Date(123456),
                                Collections.<String>emptySet()));
    }

    @Test
    public void testImageName() {
        Assert.assertEquals("jolokia_demo",
                            ContainerNamingUtil.calculateContainerName(
                                "%n",
                                "jolokia/jolokia_demo",
                                "nameAlias",
                                new Date(123456),
                                Collections.<String>emptySet()));
    }

    @Test
    public void testIndex() {
        Assert.assertEquals("1",
                            ContainerNamingUtil.calculateContainerName(
                                "%i",
                                "jolokia/jolokia_demo",
                                "nameAlias",
                                new Date(123456),
                                Collections.<String>emptySet()));
    }

    @Test
    public void testAll() {
        Assert.assertEquals("prefix-1-nameAlias-jolokia_demo-123456-postfix",
                            ContainerNamingUtil.calculateContainerName(
                                "prefix-%i-%a-%n-%t-postfix",
                                "jolokia/jolokia_demo",
                                "nameAlias",
                                new Date(123456),
                                Collections.<String>emptySet()));
    }

    @Test
    public void testIndexAdvanced() {
        Assert.assertEquals("container-2",
                            ContainerNamingUtil.calculateContainerName(
                                "container-%i",
                                "jolokia/jolokia_demo",
                                "nameAlias",
                                new Date(123456),
                                Collections.<String>singleton("container-1")));
    }

    @Test
    public void testCalculateLastContainerName() {
        Assert.assertEquals("container-1",
                            ContainerNamingUtil.calculateLastContainerName(
                                "container-%i",
                                "jolokia/jolokia_demo",
                                "nameAlias",
                                new Date(123456),
                                Collections.<String>singleton("container-1")));
    }

    @Test
    public void testCalculateLastContainerNameSecond() {
        Assert.assertEquals("container-2",
                            ContainerNamingUtil.calculateLastContainerName(
                                "container-%i",
                                "jolokia/jolokia_demo",
                                "nameAlias",
                                new Date(123456),
                                ImmutableSet.of("container-1","container-2")));

    }

}
