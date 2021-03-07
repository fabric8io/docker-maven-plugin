package io.fabric8.maven.docker.util;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;

import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.config.RunImageConfiguration;
import io.fabric8.maven.docker.model.Container;
import mockit.Expectations;
import mockit.Mocked;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author marcus
 * @since 1.0.0
 */

public class ContainerNamingUtilTest {

    @Test
    public void testDefault() {
        Assert.assertEquals("jolokia_demo-1",
                            ContainerNamingUtil.formatContainerName(
                                imageConfiguration("jolokia/jolokia_demo","nameAlias", null),
                                null,
                                new Date(123456),
                                Collections.emptySet()));
    }

    @Test
    public void testAlias() {
        Assert.assertEquals("nameAlias",
                            ContainerNamingUtil.formatContainerName(
                                imageConfiguration("jolokia/jolokia_demo","nameAlias", "%a"),
                                null,
                                new Date(123456),
                                Collections.emptySet()));
    }

    @Test
    public void testTimestamp() {
        Assert.assertEquals("123456",
                            ContainerNamingUtil.formatContainerName(
                                imageConfiguration("jolokia/jolokia_demo","nameAlias", "%t"),
                                null,
                                new Date(123456),
                                Collections.emptySet()));
    }

    @Test
    public void testImageName() {
        Assert.assertEquals("jolokia_demo",
                            ContainerNamingUtil.formatContainerName(
                                imageConfiguration("jolokia/jolokia_demo","nameAlias", "%n"),
                                null,
                                new Date(123456),
                                Collections.emptySet()));
    }

    @Test
    public void testIndex() {
        Assert.assertEquals("1",
                            ContainerNamingUtil.formatContainerName(
                                imageConfiguration("jolokia/jolokia_demo","nameAlias", "%i"),
                                null,
                                new Date(123456),
                                Collections.emptySet()));
    }

    @Test
    public void testAll() {
        Assert.assertEquals("prefix-1-nameAlias-jolokia_demo-123456-postfix",
                            ContainerNamingUtil.formatContainerName(
                                imageConfiguration("jolokia/jolokia_demo","nameAlias", "prefix-%i-%a-%n-%t-postfix"),
                                null,
                                new Date(123456),
                                Collections.emptySet()));
    }

    @Mocked
    Container container1;

    @Mocked
    Container container2;

    @Test
    public void testIndexAdvanced() {
        new Expectations() {{
            container1.getName();result = "container-1";
        }};
        Assert.assertEquals("container-2",
                            ContainerNamingUtil.formatContainerName(
                                imageConfiguration("jolokia/jolokia_demo","nameAlias", "container-%i"),
                                null,
                                new Date(123456),
                                Collections.singleton(container1)));
    }


    @Test
    public void testContainersToStop() {
        new Expectations() {{
            container1.getName();result = "container-1";
            container2.getName();result = "container-2";

        }};
        Collection<Container> containers = Arrays.asList(container1, container2);
        Collection<Container> filtered = ContainerNamingUtil.getContainersToStop(
            imageConfiguration("jolokia/jolokia_demo","nameAlias", "container-%i"),
                                null,
            new Date(123456),
            containers);
        Assert.assertEquals(1, filtered.size());
        Assert.assertEquals(container2, filtered.iterator().next());
    }

    private ImageConfiguration imageConfiguration(String name, String alias, String containerNamePattern) {
        ImageConfiguration.Builder builder = new ImageConfiguration.Builder().name(name).alias(alias);
        if (containerNamePattern != null) {
            RunImageConfiguration runConfig = new RunImageConfiguration.Builder().containerNamePattern(containerNamePattern).build();
            builder.runConfig(runConfig);
        }
        return builder.build();
    }
}
