package io.fabric8.maven.docker.assembly;

/*
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

import io.fabric8.maven.docker.util.AnsiLogger;
import org.apache.commons.io.FileUtils;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * @author roland
 * @since 02/07/15
 */
@ExtendWith(MockitoExtension.class)
class MappingTrackArchiverTest {

    @InjectMocks
    private MavenSession session;

    private MappingTrackArchiver archiver;

    @BeforeEach
    void setup() {
        archiver = new MappingTrackArchiver();
        archiver.init(new AnsiLogger(new SystemStreamLog(), false, "build"), "maven");
    }

    @Test
    void noDirectory() {
        archiver.setDestFile(new File("."));
        archiver.addDirectory(new File(System.getProperty("user.home")), "tmp");
        Assertions.assertThrows(IllegalArgumentException.class, () -> archiver.getAssemblyFiles(session));
    }

    @Test
    void simple() throws IOException, InterruptedException {
        archiver.setDestFile(new File("target/test-data/maven.tracker"));
        new File(archiver.getDestFile(), "maven").mkdirs();

        File tempFile = File.createTempFile("tracker", "txt");
        File destination = new File("target/test-data/maven/test.txt");
        org.codehaus.plexus.util.FileUtils.copyFile(tempFile, destination);

        archiver.addFile(tempFile, "test.txt");
        AssemblyFiles files = archiver.getAssemblyFiles(session);
        Assertions.assertNotNull(files);
        List<AssemblyFiles.Entry> entries = files.getUpdatedEntriesAndRefresh();
        Assertions.assertEquals(0, entries.size());
        Thread.sleep(1000);
        FileUtils.touch(tempFile);
        entries = files.getUpdatedEntriesAndRefresh();
        Assertions.assertEquals(1, entries.size());
        AssemblyFiles.Entry entry = entries.get(0);
        Assertions.assertEquals(tempFile, entry.getSrcFile());
        Assertions.assertEquals(destination, entry.getDestFile());
    }
}
