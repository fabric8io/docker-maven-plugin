package io.fabric8.maven.docker.config.handler;

import io.fabric8.maven.docker.config.build.ArchiveCompression;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ArchiveCompressionTest {

    @Test
    public void fromFileName() throws Exception {
        ArchiveCompression c = ArchiveCompression.fromFileName("test.tar");
        assertEquals("tar", c.getFileSuffix());

        c = ArchiveCompression.fromFileName("test.tar.bzip2");
        assertEquals("tar.bz", c.getFileSuffix());

        c = ArchiveCompression.fromFileName("test.tar.bz2");
        assertEquals("tar.bz", c.getFileSuffix());

        c = ArchiveCompression.fromFileName("test.tar.gz");
        assertEquals("tar.gz", c.getFileSuffix());

        c = ArchiveCompression.fromFileName("test.tgz");
        assertEquals("tar.gz", c.getFileSuffix());
    }
}
