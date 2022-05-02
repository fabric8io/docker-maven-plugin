package io.fabric8.maven.docker.util;

import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.config.RunImageConfiguration;
import io.fabric8.maven.docker.model.Container;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Set;

/**
 * @author marcus
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
class ContainerNamingUtilTest {

    @Test
    void testDefault() {
        Assertions.assertEquals("jolokia_demo-1",
                            ContainerNamingUtil.formatContainerName(
                                imageConfiguration("jolokia/jolokia_demo","nameAlias", null),
                                null,
                                new Date(123456),
                                Collections.emptySet()));
    }

    @Test
    void testAlias() {
        Assertions.assertEquals("nameAlias",
                            ContainerNamingUtil.formatContainerName(
                                imageConfiguration("jolokia/jolokia_demo","nameAlias", "%a"),
                                null,
                                new Date(123456),
                                Collections.emptySet()));
    }

    @Test
    void testRandom() {
        Assertions.assertNull(ContainerNamingUtil.formatContainerName(
            imageConfiguration("jolokia/jolokia_demo", "nameAlias", ContainerNamingUtil.EMPTY_NAME_PLACEHOLDER),
            null,
            new Date(123456),
            Collections.emptySet()));
    }

    @Test
    void testMixedRandom() {
        ImageConfiguration imageConfiguration = imageConfiguration("jolokia/jolokia_demo", "nameAlias", ContainerNamingUtil.EMPTY_NAME_PLACEHOLDER + "-extra");
        Date buildTimestamp = new Date(123456);
        Set<Container> emptySet = Collections.emptySet();
        Assertions.assertThrows(IllegalArgumentException.class, () -> ContainerNamingUtil.formatContainerName(imageConfiguration, null, buildTimestamp, emptySet));
    }

    @Test
    void testTimestamp() {
        Assertions.assertEquals("123456",
                            ContainerNamingUtil.formatContainerName(
                                imageConfiguration("jolokia/jolokia_demo","nameAlias", "%t"),
                                null,
                                new Date(123456),
                                Collections.emptySet()));
    }

    @Test
    void testImageName() {
        Assertions.assertEquals("jolokia_demo",
                            ContainerNamingUtil.formatContainerName(
                                imageConfiguration("jolokia/jolokia_demo","nameAlias", "%n"),
                                null,
                                new Date(123456),
                                Collections.emptySet()));
    }

    @Test
    void testIndex() {
        Assertions.assertEquals("1",
                            ContainerNamingUtil.formatContainerName(
                                imageConfiguration("jolokia/jolokia_demo","nameAlias", "%i"),
                                null,
                                new Date(123456),
                                Collections.emptySet()));
    }

    @Test
    void testAll() {
        Assertions.assertEquals("prefix-1-nameAlias-jolokia_demo-123456-postfix",
                            ContainerNamingUtil.formatContainerName(
                                imageConfiguration("jolokia/jolokia_demo","nameAlias", "prefix-" + ContainerNamingUtil.INDEX_PLACEHOLDER + "-%a-%n-%t-postfix"),
                                null,
                                new Date(123456),
                                Collections.emptySet()));
    }

    @Mock
    Container container1;

    @Mock
    Container container2;

    @Test
    void testIndexAdvanced() {
        Mockito.doReturn("container-1").when(container1).getName();
        Assertions.assertEquals("container-2",
                            ContainerNamingUtil.formatContainerName(
                                imageConfiguration("jolokia/jolokia_demo","nameAlias", "container-%i"),
                                null,
                                new Date(123456),
                                Collections.singleton(container1)));
    }


    @Test
    void testContainersToStop() {
        Mockito.doReturn("container-1").when(container1).getName();
        Mockito.doReturn("container-2").when(container2).getName();

        Collection<Container> containers = Arrays.asList(container1, container2);
        Collection<Container> filtered = ContainerNamingUtil.getContainersToStop(
            imageConfiguration("jolokia/jolokia_demo","nameAlias", "container-%i"),
                                null,
            new Date(123456),
            containers);
        Assertions.assertEquals(1, filtered.size());
        Assertions.assertEquals(container2, filtered.iterator().next());
    }

    @Test
    void testContainersToStopWithAlias() {
        Mockito.doReturn("container-1").when(container1).getName();
        Mockito.doReturn("container-2").when(container2).getName();

        Collection<Container> containers = Arrays.asList(container1, container2);
        Collection<Container> filtered = ContainerNamingUtil.getContainersToStop(
                imageConfiguration("jolokia/jolokia_demo","container-2", "%a"),
                null,
                new Date(123456),
                containers);
        Assertions.assertEquals(1, filtered.size());
        Assertions.assertEquals(container2, filtered.iterator().next());
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
