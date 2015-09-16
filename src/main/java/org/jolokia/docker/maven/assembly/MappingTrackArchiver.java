package org.jolokia.docker.maven.assembly;/*
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

import org.apache.maven.shared.utils.io.DirectoryScanner;
import org.codehaus.plexus.archiver.FileSet;
import org.codehaus.plexus.archiver.diags.TrackingArchiver;
import org.codehaus.plexus.components.io.resources.PlexusIoFileResource;

/**
 * An archiver which remembers all resolved files and directories and returns them
 * on request
 *
 * @author roland
 * @since 15/06/15
 */
public class MappingTrackArchiver extends TrackingArchiver {

    public void clear() {
        added.clear();
    }

    /**
     * Get all files depicted by this assembly.
     *
     * @return assembled files
     */
    public AssemblyFiles getAssemblyFiles() {
        AssemblyFiles ret = new AssemblyFiles();
        for (Addition addition : added) {
            Object resource = addition.resource;
            if (resource instanceof File && addition.destination != null) {
                ret.addEntry((File) resource, new File(addition.destination));
            } else if (resource instanceof PlexusIoFileResource) {
                ret.addEntry(((PlexusIoFileResource) resource).getFile(),new File(addition.destination));
            } else if (resource instanceof FileSet) {
                FileSet fs = (FileSet) resource;
                DirectoryScanner ds = new DirectoryScanner();
                File base = addition.directory;
                ds.setBasedir(base);
                ds.setIncludes(fs.getIncludes());
                ds.setExcludes(fs.getExcludes());;
                ds.setCaseSensitive(fs.isCaseSensitive());
                ds.scan();
                for (String f : ds.getIncludedFiles()) {
                    ret.addEntry(new File(base,f),new File(addition.destination,f));
                }
            } else {
                throw new IllegalStateException("Unknown resource type " + resource.getClass() + ": " + resource);
            }
        }
        return ret;
    };
}
