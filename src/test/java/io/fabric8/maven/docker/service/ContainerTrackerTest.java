package io.fabric8.maven.docker.service;
/*
 *
 * Copyright 2016 Roland Huss
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.util.*;

import io.fabric8.maven.docker.config.RunImageConfiguration;
import io.fabric8.maven.docker.util.GavLabel;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.config.WaitConfiguration;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * @author roland
 * @since 14/02/16
 */
class ContainerTrackerTest {

    private ContainerTracker tracker;

    @BeforeEach
    void setUp() {
        tracker = new ContainerTracker();
    }

    @Test
    void lookup() {
        tracker.registerContainer("1234", getImageConfiguration("name", "alias"), getPomLabel("test1"));

        Assertions.assertEquals("1234", tracker.lookupContainer("name"));
        Assertions.assertEquals("1234", tracker.lookupContainer("alias"));
        Assertions.assertNull(tracker.lookupContainer("blub"));
    }

    @Test
    void removeContainer() {
        String[][] data = new String[][] {
            { "1", "name1", "alias1", "100", "200", "stop1", "label1", "false" },
            { "2", "name2", "alias2", null, null, null, "label2", "true" }
        };

        List<ContainerTracker.ContainerShutdownDescriptor> descs = registerAtTracker(data);

        ContainerTracker.ContainerShutdownDescriptor desc = tracker.removeContainer("1");
        verifyDescriptor(data[0], desc);
        Assertions.assertNull(tracker.lookupContainer("1"));
        Assertions.assertNull(tracker.removeContainer("1"));

        Assertions.assertTrue(tracker.removeShutdownDescriptors(getPomLabel("label1")).isEmpty());
        Assertions.assertFalse(tracker.removeShutdownDescriptors(getPomLabel("label2")).isEmpty());
        Assertions.assertTrue(tracker.removeShutdownDescriptors(getPomLabel("label2")).isEmpty());
    }

    @Test
    void removeDescriptors() {

        String[][] data = new String[][] {
            { "1", "name1", "alias1", "100", "200", "stop1", "label1", "true" },
            { "2", "name2", "alias2", null, null, null, "label1", "false" },
            { "3", "name3", null, null, null, null, "label2", "true" }
        };

        List<ContainerTracker.ContainerShutdownDescriptor> descs = registerAtTracker(data);

        Collection<ContainerTracker.ContainerShutdownDescriptor> removed = tracker.removeShutdownDescriptors(getPomLabel("label1"));
        Assertions.assertEquals(2, removed.size());
        Iterator<ContainerTracker.ContainerShutdownDescriptor> it = removed.iterator();
        // Reverse order
        verifyDescriptor(data[1], it.next());
        verifyDescriptor(data[0], it.next());

        Assertions.assertNull(tracker.lookupContainer("name1"));
        Assertions.assertNull(tracker.lookupContainer("alias1"));
        Assertions.assertNull(tracker.lookupContainer("name2"));
        Assertions.assertNull(tracker.lookupContainer("alias2"));
        Assertions.assertNotNull(tracker.lookupContainer("name3"));
    }

    @Test
    void removeAll() {
        String[][] data = new String[][] {
            { "1", "name1", "alias1", "100", "200", "stop1", "label1", "true" },
            { "2", "name2", "alias2", null, null, null, "label1", "false" },
            { "3", "name3", null, null, null, null, "label2", "false" }
        };

        List<ContainerTracker.ContainerShutdownDescriptor> descs = registerAtTracker(data);
        Collection<ContainerTracker.ContainerShutdownDescriptor> removed = tracker.removeShutdownDescriptors(null);

        Assertions.assertEquals(3, removed.size());
        Iterator<ContainerTracker.ContainerShutdownDescriptor> it = removed.iterator();
        // Reverse order
        verifyDescriptor(data[2], it.next());
        verifyDescriptor(data[1], it.next());
        verifyDescriptor(data[0], it.next());

        Assertions.assertEquals(0, tracker.removeShutdownDescriptors(null).size());
    }

    @Test
    void getEmptyDescriptors() {
        List<ContainerTracker.ContainerShutdownDescriptor> actual = tracker.getShutdownDescriptors(getPomLabel("label1"));

        Assertions.assertNotNull(actual);
        Assertions.assertEquals(0, actual.size());
    }

    @Test
    void getDescriptors() {
        String[][] data = new String[][] {
            { "1", "name1", "alias1", "100", "200", "stop1", "label1", "true" },
            { "2", "name2", "alias2", null, null, null, "label1", "false" },
            { "3", "name3", null, null, null, null, "label2", "true" }
        };
        registerAtTracker(data);

        List<ContainerTracker.ContainerShutdownDescriptor> actual = tracker.getShutdownDescriptors(getPomLabel("label1"));

        Assertions.assertEquals(2, actual.size());
        verifyDescriptor(data[0], actual.get(0));
        verifyDescriptor(data[1], actual.get(1));

        Assertions.assertNotNull(tracker.lookupContainer("name1"));
        Assertions.assertNotNull(tracker.lookupContainer("alias1"));
        Assertions.assertNotNull(tracker.lookupContainer("name2"));
        Assertions.assertNotNull(tracker.lookupContainer("alias2"));
        Assertions.assertNotNull(tracker.lookupContainer("name3"));
    }

    @Test
    void getAllDescriptors() {
        String[][] data = new String[][] {
            { "1", "name1", "alias1", "100", "200", "stop1", "label1", "true" },
            { "2", "name2", "alias2", null, null, null, "label1", "false" },
            { "3", "name3", null, null, null, null, "label2", "false" }
        };
        registerAtTracker(data);

        List<ContainerTracker.ContainerShutdownDescriptor> actual = tracker.getShutdownDescriptors(null);

        Assertions.assertEquals(3, actual.size());
        verifyDescriptor(data[0], actual.get(0));
        verifyDescriptor(data[1], actual.get(1));
        verifyDescriptor(data[2], actual.get(2));

        Assertions.assertEquals(3, tracker.getShutdownDescriptors(null).size());
    }

    private void verifyDescriptor(String[] d, ContainerTracker.ContainerShutdownDescriptor desc) {
        Assertions.assertNotNull(desc);
        Assertions.assertEquals(desc.getContainerId(), d[0]);
        Assertions.assertEquals(desc.getImage(), d[1]);
        if (d[2] != null) {
            Assertions.assertTrue(desc.getDescription().contains(d[2]));
        }
        Assertions.assertEquals(desc.getShutdownGracePeriod(), parseInt(d[3]));
        Assertions.assertEquals(desc.getKillGracePeriod(), parseInt(d[4]));
        Assertions.assertEquals(desc.getPreStop(), d[5]);
        Assertions.assertEquals(desc.isBreakOnError(), Boolean.parseBoolean(d[7]));
        Assertions.assertNotNull(desc.getImageConfiguration());
    }

    private List<ContainerTracker.ContainerShutdownDescriptor> registerAtTracker(String[][] data) {
        List<ContainerTracker.ContainerShutdownDescriptor> descriptors = new ArrayList<>();
        for (String[] d : data) {
            ImageConfiguration imageConfig =
                getImageConfiguration(d[1], d[2], parseInt(d[3]), parseInt(d[4]), d[5], Boolean.parseBoolean(d[7]));

            tracker.registerContainer(d[0],
                imageConfig,
                getPomLabel(d[6]));
            descriptors.add(new ContainerTracker.ContainerShutdownDescriptor(imageConfig, d[0]));
        }
        return descriptors;
    }

    private int parseInt(String s) {
        return s != null ? Integer.parseInt(s) : 0;
    }

    private GavLabel getPomLabel(String artifactId) {
        return new GavLabel("io.fabric8", artifactId, "1.0.0");
    }

    private ImageConfiguration getImageConfiguration(String name, String alias) {
        return getImageConfiguration(name, alias, 0, 0, null, false);
    }

    private ImageConfiguration getImageConfiguration(String name, String alias, int shutdown, int kill, String preStop, boolean breakOnError) {
        WaitConfiguration waitConfig = null;
        if (shutdown != 0 && kill != 0) {
            WaitConfiguration.Builder builder = new WaitConfiguration.Builder()
                .shutdown(shutdown)
                .kill(kill);
            if (preStop != null) {
                builder.preStop(preStop);
                builder.breakOnError(breakOnError);
            }
            waitConfig = builder.build();
        }
        return new ImageConfiguration.Builder()
            .name(name)
            .alias(alias)
            .runConfig(new RunImageConfiguration.Builder()
                .wait(waitConfig)
                .build())
            .build();
    }
}
