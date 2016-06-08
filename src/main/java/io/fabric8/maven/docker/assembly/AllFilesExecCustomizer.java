package io.fabric8.maven.docker.assembly;
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

import java.io.File;
import java.io.IOException;

import io.fabric8.maven.docker.util.Logger;
import org.codehaus.plexus.archiver.ArchiveEntry;
import org.codehaus.plexus.archiver.ResourceIterator;
import org.codehaus.plexus.archiver.tar.TarArchiver;
import org.codehaus.plexus.archiver.tar.TarLongFileMode;
import org.codehaus.plexus.components.io.resources.PlexusIoResource;
import org.codehaus.plexus.util.StringUtils;

/**
 * @author roland
 * @since 26/06/16
 */
class AllFilesExecCustomizer implements DockerAssemblyManager.ArchiverCustomizer {
    private DockerAssemblyManager.ArchiverCustomizer innerCustomizer;
    private Logger log;

    AllFilesExecCustomizer(DockerAssemblyManager.ArchiverCustomizer inner, Logger logger) {
        innerCustomizer = inner;
        this.log = logger;
    }

    @Override
    public TarArchiver customize(TarArchiver archiver) throws IOException {
        log.warn("/--------------------- SECURITY WARNING ---------------------\\");
        log.warn("|You are building a Docker image with normalized permissions.|");
        log.warn("|All files and directories added to build context will have  |");
        log.warn("|'-rwxr-xr-x' permissions. It is recommended to double check |");
        log.warn("|and reset permissions for sensitive files and directories.  |");
        log.warn("\\------------------------------------------------------------/");

        if (innerCustomizer != null) {
            archiver = innerCustomizer.customize(archiver);
        }

        TarArchiver newArchiver = new TarArchiver();
        newArchiver.setDestFile(archiver.getDestFile());
        newArchiver.setLongfile(TarLongFileMode.posix);

        ResourceIterator resources = archiver.getResources();
        while (resources.hasNext()) {
            ArchiveEntry ae = resources.next();
            String fileName = ae.getName();
            PlexusIoResource resource = ae.getResource();
            String name = StringUtils.replace(fileName, File.separatorChar, '/');

            // See docker source:
            // https://github.com/docker/docker/blob/3d13fddd2bc4d679f0eaa68b0be877e5a816ad53/pkg/archive/archive_windows.go#L45
            int mode = ae.getMode() & 0777;
            int newMode = mode;
            newMode &= 0755;
            newMode |= 0111;

            if (newMode != mode) {
                log.debug("Changing permissions of '%s' from %o to %o.", name, mode, newMode);
            }

            newArchiver.addResource(resource, name, newMode);
        }

        archiver = newArchiver;

        return archiver;
    }
}
