package io.fabric8.maven.docker.assembly;/*
 * 
 * Copyright 2014 Roland Huss
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

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Collection of assembly files which need to be monitored for checking when
 * to rebuild an image.
 *
 * @author roland
 * @since 15/06/15
 */
public class AssemblyFiles {

    private final File assemblyDirectory;
    private List<Entry> entries = new ArrayList<>();

    /**
     * Create a collection of assembly files
     *
     * @param assemblyDirectory directory into which the files are copied
     */
    public AssemblyFiles(File assemblyDirectory) {
        this.assemblyDirectory = new File(assemblyDirectory.getParentFile(), DockerAssemblyManager.ASSEMBLY_NAME);
    }

    /**
     * Add a entry to the list of assembly files which possible should be monitored
     *
     * @param srcFile source file to monitor. The source file must exist.
     * @param destFile the destination to which it is eventually copied. The destination file must be relative.
     */
    public void addEntry(File srcFile, File destFile) {
        entries.add(new Entry(srcFile,destFile));
    }

    /**
     * Get the list of all updated entries i.e. all entries which have modification date
     * which is newer than the last time check. ATTENTION: As a side effect this method also
     * updates the timestamp of entries.
     *
     * @return list of all entries which has been updated since the last call to this method or an empty list
     */
    public List<Entry> getUpdatedEntriesAndRefresh() {
        List<Entry> ret = new ArrayList<>();
        for (Entry entry : entries) {
            if (entry.isUpdated()) {
                ret.add(entry);
            }
        }
        return ret;
    }

    /**
     * Returns true if there are no entries
     */
    public boolean isEmpty() {
        return entries.isEmpty();
    }

    /**
     * Return destination directory where the files are copied into
     *
     * @return top-level directory holding the assembled files
     */
    public File getAssemblyDirectory() {
        return assemblyDirectory;
    }

    // ===============================================================================
    // Inner class remembering the modification date of a source file and its destination

    public static class Entry {

        private long lastModified;
        private File srcFile;
        private File destFile;

        private Entry(File srcFile, File destFile) {
            this.srcFile = srcFile;
            this.destFile = destFile;
            if (!srcFile.exists()) {
                throw new IllegalArgumentException("Source " + srcFile + " does not exist");
            }
            if (!destFile.exists()) {
                throw new IllegalArgumentException("Destination " + destFile + " does not exist");
            }
            if (srcFile.isDirectory()) {
                throw new IllegalArgumentException("Can only watch files, not directories: " + srcFile);
            }
            this.lastModified = this.srcFile.lastModified();
        }

        public File getSrcFile() {
            return srcFile;
        }

        /**
         * @return destination file which is absolute (and withing AssemblyFiles.assemblyDirectory)
         */
        public File getDestFile() {
            return destFile;
        }

        boolean isUpdated() {
            if (srcFile.lastModified() > lastModified) {
                // Update last modified as a side effect
                lastModified = srcFile.lastModified();
                return true;
            } else {
                return false;
            }
        }
    }
}
