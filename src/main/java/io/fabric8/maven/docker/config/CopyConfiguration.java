package io.fabric8.maven.docker.config;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.apache.maven.plugins.annotations.Parameter;

import io.fabric8.maven.docker.util.DeepCopy;

public class CopyConfiguration implements Serializable {

    public static final String CONTAINER_PATH_PROPERTY = "containerPath";
    public static final String HOST_DIRECTORY_PROPERTY = "hostDirectory";

    public static class Entry implements Serializable {

        /**
         * Full path to container file or container directory which needs to copied.
         */
        private String containerPath;

        /**
         * Path to a host directory where the files copied from container need to be placed. If relative path is
         * provided then project base directory is considered as a base of that relative path. Can be <code>null</code>
         * meaning the same as empty string. Note that if containerPath points to a directory, then a directory with the
         * same name will be created in the hostDirectory, i.e. not just content of directory is copied, but the same
         * name directory is created too.
         */
        private String hostDirectory;

        public Entry() {}

        public Entry(String containerPath, String hostDirectory) {
            this.containerPath = containerPath;
            this.hostDirectory = hostDirectory;
        }

        public String getContainerPath() {
            return containerPath;
        }

        public String getHostDirectory() {
            return hostDirectory;
        }

        public void setContainerPath(String containerPath) {
            this.containerPath = containerPath;
        }

        public void setHostDirectory(String hostDirectory) {
            this.hostDirectory = hostDirectory;
        }
    }

    /**
     * Items to copy from container.
     */
    @Parameter
    private List<Entry> entries;

    public List<Entry> getEntries() {
        return entries;
    }

    public List<Properties> getEntriesAsListOfProperties() {
        if (entries == null) {
            return null;
        }
        final List<Properties> properties = new ArrayList<>(entries.size());
        for (Entry entry : entries) {
            final Properties entryProperties = new Properties();
            entryProperties.put(CONTAINER_PATH_PROPERTY, entry.getContainerPath());
            final String hostDirectory = entry.getHostDirectory();
            if (hostDirectory != null) {
                entryProperties.put(HOST_DIRECTORY_PROPERTY, hostDirectory);
            }
            properties.add(entryProperties);
        }
        return properties;
    }

    public static class Builder {

        private final CopyConfiguration config;

        public Builder() {
            this(null);
        }

        public Builder(CopyConfiguration that) {
            if (that == null) {
                config = new CopyConfiguration();
            } else {
                config = DeepCopy.copy(that);
            }
        }

        public Builder entries(List<Entry> entries) {
            final List<Entry> entriesCopy = new ArrayList<>(entries.size());
            for (Entry entry : entries) {
                entriesCopy.add(new Entry(entry.getContainerPath(), entry.getHostDirectory()));
            }
            config.entries = entriesCopy;
            return this;
        }

        public Builder entriesAsListOfProperties(List<Properties> entries) {
            config.entries = getEntriesFromProperties(entries);
            return this;
        }

        public CopyConfiguration build() {
            return config;
        }

        private static List<Entry> getEntriesFromProperties(List<Properties> properties) {
            if (properties == null) {
                return null;
            }
            final List<Entry> entries = new ArrayList<>(properties.size());
            int i = 0;
            for (Properties entryProperties : properties) {
                String containerPath = entryProperties.getProperty(CONTAINER_PATH_PROPERTY);
                final String hostDirectory = entryProperties.getProperty(HOST_DIRECTORY_PROPERTY);
                if (containerPath == null && hostDirectory == null) {
                    // Shortcut for the case when we need to define just containerPath of copy entry
                    containerPath = entryProperties.getProperty("");
                }
                if (containerPath == null) {
                    throw new IllegalArgumentException(
                            String.format("Mandatory property [%s] of entry [%d] is not defined",
                                    CONTAINER_PATH_PROPERTY, i));
                }
                entries.add(new Entry(containerPath, hostDirectory));
                ++i;
            }
            return entries;
        }
    }
}
