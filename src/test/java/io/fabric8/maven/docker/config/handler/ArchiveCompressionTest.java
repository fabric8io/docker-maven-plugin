package io.fabric8.maven.docker.config.handler;

import io.fabric8.maven.docker.config.ArchiveCompression;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ArchiveCompressionTest {

    @Test
    void fromFileName() throws Exception {
        ArchiveCompression c = ArchiveCompression.fromFileName("test.tar");
        Assertions.assertEquals("tar", c.getFileSuffix());

        c = ArchiveCompression.fromFileName("test.tar.bzip2");
        Assertions.assertEquals("tar.bz", c.getFileSuffix());

        c = ArchiveCompression.fromFileName("test.tar.bz2");
        Assertions.assertEquals("tar.bz", c.getFileSuffix());

        c = ArchiveCompression.fromFileName("test.tar.gz");
        Assertions.assertEquals("tar.gz", c.getFileSuffix());

        c = ArchiveCompression.fromFileName("test.tgz");
        Assertions.assertEquals("tar.gz", c.getFileSuffix());
    }
}
