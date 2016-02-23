package io.fabric8.maven.docker.config;/*
 * 
 * Copyright 2015 Roland Huss
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

import org.codehaus.plexus.archiver.tar.TarArchiver;

/**
 * Enumeration for determine the compression mode when creating docker
 * build archives.
 *
 * @author roland
 * @since 26/10/15
 */
public enum BuildTarArchiveCompression {

    none(TarArchiver.TarCompressionMethod.none, "tar"),
    gzip(TarArchiver.TarCompressionMethod.gzip,"tar.gz"),
    bzip2(TarArchiver.TarCompressionMethod.bzip2,"tar.bz");

    private final TarArchiver.TarCompressionMethod tarCompressionMethod;
    private final String fileSuffix;

    BuildTarArchiveCompression(TarArchiver.TarCompressionMethod tarCompressionMethod, String fileSuffix) {
        this.tarCompressionMethod = tarCompressionMethod;
        this.fileSuffix = fileSuffix;
    }

    public TarArchiver.TarCompressionMethod getTarCompressionMethod() {
        return tarCompressionMethod;
    }

    public String getFileSuffix() {
        return fileSuffix;
    }
}
