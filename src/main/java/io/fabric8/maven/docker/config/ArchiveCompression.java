package io.fabric8.maven.docker.config;
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

import java.io.IOException;
import java.io.OutputStream;

import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream;
import org.codehaus.plexus.archiver.tar.TarArchiver;

/**
 * Enumeration for determine the compression mode when creating docker
 * build archives.
 *
 * @author roland
 * @since 26/10/15
 */
public enum ArchiveCompression {

    none(TarArchiver.TarCompressionMethod.none, "tar"),

    gzip(TarArchiver.TarCompressionMethod.gzip,"tar.gz") {
        @Override
        public OutputStream wrapOutputStream(OutputStream out) throws IOException {
            return new GZIPOutputStream(out);
        }
    },

    bzip2(TarArchiver.TarCompressionMethod.bzip2,"tar.bz") {
        @Override
        public OutputStream wrapOutputStream(OutputStream out) throws IOException {
            return new BZip2CompressorOutputStream(out);
        }
    };

    // ====================================================================

    private final TarArchiver.TarCompressionMethod tarCompressionMethod;
    private final String fileSuffix;

    ArchiveCompression(TarArchiver.TarCompressionMethod tarCompressionMethod, String fileSuffix) {
        this.tarCompressionMethod = tarCompressionMethod;
        this.fileSuffix = fileSuffix;
    }

    public TarArchiver.TarCompressionMethod getTarCompressionMethod() {
        return tarCompressionMethod;
    }

    public String getFileSuffix() {
        return fileSuffix;
    }

    public OutputStream wrapOutputStream(OutputStream outputStream) throws IOException {
        return outputStream;
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

    private static final int GZIP_BUFFER_SIZE = 65536;
    // According to https://bugs.openjdk.java.net/browse/JDK-8142920, 3 is a better default
    private static final int GZIP_COMPRESSION_LEVEL = 3;

    private static class GZIPOutputStream extends java.util.zip.GZIPOutputStream {
        private GZIPOutputStream(OutputStream out) throws IOException {
            super(out, GZIP_BUFFER_SIZE);
            def.setLevel(GZIP_COMPRESSION_LEVEL);
        }
    }
}
