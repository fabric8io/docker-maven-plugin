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
import io.fabric8.maven.docker.util.PomLabel;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.config.WaitConfiguration;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author roland
 * @since 14/02/16
 */
public class ContainerTrackerTest {

    private ContainerTracker tracker;

    @Before
    public void setUp() throws Exception {
        tracker = new ContainerTracker();
    }

    @Test
    public void lookup() throws Exception {
        tracker.registerContainer("1234",getImageConfiguration("name","alias"),getPomLabel("test1"));

        assertEquals("1234",tracker.lookupContainer("name"));
        assertEquals("1234",tracker.lookupContainer("alias"));
        assertNull(tracker.lookupContainer("blub"));
    }

    @Test
    public void removeContainer() throws Exception {
        String data[][] = new String[][] {
            {"1", "name1", "alias1", "100", "200", "stop1", "label1"},
            {"2", "name2", "alias2", null, null, null, "label2"}
        };

        List<ContainerTracker.ContainerShutdownDescriptor> descs = registerAtTracker(data);

        ContainerTracker.ContainerShutdownDescriptor desc = tracker.removeContainer("1");
        verifyDescriptor(data[0],desc);
        assertNull(tracker.lookupContainer("1"));
        assertNull(tracker.removeContainer("1"));

        assertTrue(tracker.removeShutdownDescriptors(getPomLabel("label1")).isEmpty());
        assertFalse(tracker.removeShutdownDescriptors(getPomLabel("label2")).isEmpty());
        assertTrue(tracker.removeShutdownDescriptors(getPomLabel("label2")).isEmpty());
    }

    @Test
    public void removeDescriptors() throws Exception {

        String data[][] = new String[][] {
            { "1", "name1", "alias1", "100", "200", "stop1", "label1" },
            { "2", "name2", "alias2", null, null, null, "label1" },
            { "3", "name3", null, null, null, null, "label2" }
        };

        List<ContainerTracker.ContainerShutdownDescriptor> descs = registerAtTracker(data);

        Collection<ContainerTracker.ContainerShutdownDescriptor> removed = tracker.removeShutdownDescriptors(getPomLabel("label1"));
        assertEquals(2,removed.size());
        Iterator<ContainerTracker.ContainerShutdownDescriptor> it = removed.iterator();
        // Reverse order
        verifyDescriptor(data[1],it.next());
        verifyDescriptor(data[0],it.next());

        assertNull(tracker.lookupContainer("name1"));
        assertNull(tracker.lookupContainer("alias1"));
        assertNull(tracker.lookupContainer("name2"));
        assertNull(tracker.lookupContainer("alias2"));
        assertNotNull(tracker.lookupContainer("name3"));
    }

    @Test
    public void removeAll() throws Exception {
        String data[][] = new String[][] {
            { "1", "name1", "alias1", "100", "200", "stop1", "label1" },
            { "2", "name2", "alias2", null, null, null, "label1" },
            { "3", "name3", null, null, null, null, "label2" }
        };

        List<ContainerTracker.ContainerShutdownDescriptor> descs = registerAtTracker(data);
        Collection<ContainerTracker.ContainerShutdownDescriptor> removed = tracker.removeShutdownDescriptors(null);

        assertEquals(3,removed.size());
        Iterator<ContainerTracker.ContainerShutdownDescriptor> it = removed.iterator();
        // Reverse order
        verifyDescriptor(data[2],it.next());
        verifyDescriptor(data[1],it.next());
        verifyDescriptor(data[0],it.next());

        assertEquals(0,tracker.removeShutdownDescriptors(null).size());
    }

    private void verifyDescriptor(String[] d, ContainerTracker.ContainerShutdownDescriptor desc) {
        assertNotNull(desc);
        assertEquals(desc.getContainerId(),d[0]);
        assertEquals(desc.getImage(),d[1]);
        if (d[2] != null) {
            assertTrue(desc.getDescription().contains(d[2]));
        }
        assertEquals(desc.getShutdownGracePeriod(),parseInt(d[3]));
        assertEquals(desc.getKillGracePeriod(),parseInt(d[4]));
        assertEquals(desc.getPreStop(),d[5]);
        assertNotNull(desc.getImageConfiguration());
    }

    private List<ContainerTracker.ContainerShutdownDescriptor> registerAtTracker(String[][] data) {
        List<ContainerTracker.ContainerShutdownDescriptor> descriptors = new ArrayList<>();
        for (String[] d : data) {
            ImageConfiguration imageConfig =
                getImageConfiguration(d[1], d[2], parseInt(d[3]), parseInt(d[4]), d[5]);

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

    private PomLabel getPomLabel(String artifactId) {
        return new PomLabel("io.fabric8",artifactId,"1.0.0");
    }

    private ImageConfiguration getImageConfiguration(String name, String alias) {
        return getImageConfiguration(name, alias, 0,0,null);
    }

    private ImageConfiguration getImageConfiguration(String name, String alias, int shutdown,int kill, String preStop) {
        WaitConfiguration waitConfig = null;
        if (shutdown != 0 && kill != 0) {
            WaitConfiguration.Builder builder = new WaitConfiguration.Builder()
            .shutdown(shutdown)
            .kill(kill);
            if (preStop != null) {
                builder.preStop(preStop);
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
