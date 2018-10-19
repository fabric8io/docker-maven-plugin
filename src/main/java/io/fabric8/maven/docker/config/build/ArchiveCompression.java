package io.fabric8.maven.docker.config.build;
/*
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

/**
 * Enumeration for determine the compression mode when creating docker
 * build archives.
 *
 * @author roland
 * @since 26/10/15
 */
public enum ArchiveCompression {

    none("tar"),
    gzip("tar.gz"),
    bzip2("tar.bz");

    // ====================================================================

    private final String fileSuffix;

    ArchiveCompression(String fileSuffix) {
        this.fileSuffix = fileSuffix;
    }

    public String getFileSuffix() {
        return fileSuffix;
    }

    public static ArchiveCompression fromFileName(String filename) {
		if (filename.endsWith(".tar.gz") || filename.endsWith(".tgz")) {
			return ArchiveCompression.gzip;
		}

        if (filename.endsWith(".tar.bz") || filename.endsWith(".tar.bzip2") || filename.endsWith(".tar.bz2")) {
            return ArchiveCompression.bzip2;
        }
        return ArchiveCompression.none;
    }

}
