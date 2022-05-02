package io.fabric8.maven.docker.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.fabric8.maven.docker.config.CopyConfiguration.Builder;
import io.fabric8.maven.docker.config.CopyConfiguration.Entry;

import static io.fabric8.maven.docker.config.CopyConfiguration.CONTAINER_PATH_PROPERTY;
import static io.fabric8.maven.docker.config.CopyConfiguration.HOST_DIRECTORY_PROPERTY;


class CopyConfigurationTest {

    @Test
    void entryGetters() {
        final String containerPath = "container";
        final String hostDirectory = "host";
        final Entry entry = new Entry(containerPath, hostDirectory);
        Assertions.assertEquals(containerPath, entry.getContainerPath());
        Assertions.assertEquals(hostDirectory, entry.getHostDirectory());
    }

    @Test
    void entrySetters() {
        final String containerPath = "container";
        final String hostDirectory = "host";
        final Entry entry = new Entry();
        entry.setContainerPath(containerPath);
        entry.setHostDirectory(hostDirectory);
        Assertions.assertEquals(containerPath, entry.getContainerPath());
        Assertions.assertEquals(hostDirectory, entry.getHostDirectory());
    }

    @Test
    void empty() {
        final CopyConfiguration cfg = new Builder().build();
        assertEntries(cfg, null);
        assertEntriesAsProperties(cfg, null);
    }

    @Test
    void notEmpty() {
        final List<Entry> expected = entries();
        final CopyConfiguration cfg = new Builder().entries(expected).build();
        assertEntries(cfg, expected);
        assertEntriesAsProperties(cfg, expected);
    }

    @Test
    void fromProperties() {
        final List<Entry> expected = entries();
        final List<Properties> entriesAsProperties = entriesAsListOfProperties(expected);
        final CopyConfiguration cfg = new Builder().entriesAsListOfProperties(entriesAsProperties).build();
        assertEntries(cfg, expected);
        assertEntriesAsProperties(cfg, expected);
    }

    @Test
    void fromNullProperties() {
        final CopyConfiguration cfg = new Builder().entriesAsListOfProperties(null).build();
        assertEntries(cfg, null);
        assertEntriesAsProperties(cfg, null);
    }

    @Test
    void fromBrokenProperties() {
        Builder builder = new Builder();
        List<Properties> entries = entriesAsListOfProperties(brokenEntries());
        Assertions.assertThrows(IllegalArgumentException.class, () -> builder.entriesAsListOfProperties(entries));
    }

    @Test
    void copyOfNull() {
        final CopyConfiguration copy = new Builder(null).build();
        assertEntries(copy, null);
        assertEntriesAsProperties(copy, null);
    }

    @Test
    void copyOfNotNull() {
        final List<Entry> expected = entries();
        final CopyConfiguration original = new Builder().entries(expected).build();
        final CopyConfiguration copy = new Builder(original).build();
        assertEntries(copy, expected);
        assertEntriesAsProperties(copy, expected);
    }

    private List<Entry> entries() {
        final List<Entry> entries = new ArrayList<>();
        entries.add(new Entry("container1", "host1"));
        entries.add(new Entry("container2", "host2"));
        entries.add(new Entry("container3", null));
        return entries;
    }

    private List<Entry> brokenEntries() {
        final List<Entry> entries = new ArrayList<>();
        entries.add(new Entry("container1", "host1"));
        entries.add(new Entry(null, "host2"));
        entries.add(new Entry("container3", null));
        return entries;
    }

    private List<Properties> entriesAsListOfProperties(Collection<Entry> expected) {
        if (expected == null) {
            return null;
        }
        final List<Properties> propertiesList = new ArrayList<>(expected.size());
        for (Entry entry : expected) {
            final Properties properties = new Properties();
            final String containerPath = entry.getContainerPath();
            final String hostDirectory = entry.getHostDirectory();
            if (containerPath != null && hostDirectory == null) {
                properties.put("", containerPath);
            } else {
                if (containerPath != null) {
                    properties.put(CONTAINER_PATH_PROPERTY, containerPath);
                }
                if (hostDirectory != null) {
                    properties.put(HOST_DIRECTORY_PROPERTY, hostDirectory);
                }
            }
            properties.put("some.garbage", "which should be ignored");
            propertiesList.add(properties);
        }
        return propertiesList;
    }

    private void assertEntries(final CopyConfiguration cfg, Collection<Entry> expected) {
        final List<Entry> actual = cfg.getEntries();
        if (expected == null) {
            Assertions.assertNull(actual);
            return;
        }
        Assertions.assertNotNull(actual);
        Assertions.assertEquals(expected.size(), actual.size());
        final Iterator<Entry> actualIterator = actual.iterator();
        for (Entry expectedEntry : expected) {
            final Entry actualEntry = actualIterator.next();
            Assertions.assertEquals(expectedEntry.getContainerPath(), actualEntry.getContainerPath());
            Assertions.assertEquals(expectedEntry.getHostDirectory(), actualEntry.getHostDirectory());
        }
    }

    private void assertEntriesAsProperties(final CopyConfiguration cfg, Collection<Entry> expected) {
        final List<Properties> actual = cfg.getEntriesAsListOfProperties();
        if (expected == null) {
            Assertions.assertNull(actual);
            return;
        }
        Assertions.assertNotNull(actual);
        Assertions.assertEquals(expected.size(), actual.size());
        final Iterator<Properties> actualIterator = actual.iterator();
        for (Entry expectedEntry : expected) {
            final Properties actualEntry = actualIterator.next();
            Assertions.assertEquals(expectedEntry.getContainerPath(), actualEntry.get(CONTAINER_PATH_PROPERTY));
            Assertions.assertEquals(expectedEntry.getHostDirectory(), actualEntry.get(HOST_DIRECTORY_PROPERTY));
        }
    }
}
