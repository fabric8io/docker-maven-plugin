package io.fabric8.maven.docker.assembly;

import io.fabric8.maven.docker.util.AnsiLogger;
import org.apache.commons.io.FileUtils;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.util.List;

@ExtendWith(MockitoExtension.class)
class TrackArchiverCollectionTest {
    @Mock
    private MavenSession session;

    @Test
    void multipleAssemblies() throws Exception {
        TrackArchiverCollection archiverCollection = new TrackArchiverCollection();

        AnsiLogger logger = new AnsiLogger(new SystemStreamLog(), false, "build");
        archiverCollection.init(logger, "maven");
        archiverCollection.init(logger, "deps");

        // Add files to "maven" assembly
        MappingTrackArchiver mavenArchiver = archiverCollection.get("maven");
        mavenArchiver.setDestFile(new File("target/test-data/maven.tracker"));
        new File(mavenArchiver.getDestFile(), "maven").mkdirs();

        File tempFile = File.createTempFile("tracker", "txt");
        File destination = new File("target/test-data/maven/test.txt");
        org.codehaus.plexus.util.FileUtils.copyFile(tempFile, destination);

        // Add files to "deps" assembly
        MappingTrackArchiver depsArchiver = archiverCollection.get("deps");
        depsArchiver.setDestFile(new File("target/test-data/deps.tracker"));
        new File(mavenArchiver.getDestFile(), "deps").mkdirs();

        File tempFile2 = File.createTempFile("tracker", "txt");
        File destination2 = new File("target/test-data/deps/deps.txt");
        org.codehaus.plexus.util.FileUtils.copyFile(tempFile2, destination2);

        mavenArchiver.addFile(tempFile, "test.txt");
        depsArchiver.addFile(tempFile2, "deps.txt");

        // Verify tracking of files in "maven" assembly
        AssemblyFiles files = archiverCollection.getAssemblyFiles(session, "maven");
        Assertions.assertNotNull(files);
        List<AssemblyFiles.Entry> entries = files.getUpdatedEntriesAndRefresh();
        Assertions.assertEquals(0, entries.size());
        Thread.sleep(1000); // Wait before touching the file so the modified time is different
        FileUtils.touch(tempFile);
        entries = files.getUpdatedEntriesAndRefresh();
        Assertions.assertEquals(1, entries.size());
        AssemblyFiles.Entry entry = entries.get(0);
        Assertions.assertEquals(tempFile, entry.getSrcFile());
        Assertions.assertEquals(destination, entry.getDestFile());

        // Verify tracking of files in "deps" assembly
        AssemblyFiles deps = archiverCollection.getAssemblyFiles(session, "deps");
        Assertions.assertNotNull(deps);
        entries = deps.getUpdatedEntriesAndRefresh();
        Assertions.assertEquals(0, entries.size());
        Thread.sleep(1000); // Wait before touching the file so the modified time is different
        FileUtils.touch(tempFile2);
        entries = deps.getUpdatedEntriesAndRefresh();
        Assertions.assertEquals(1, entries.size());
        entry = entries.get(0);
        Assertions.assertEquals(tempFile2, entry.getSrcFile());
        Assertions.assertEquals(destination2, entry.getDestFile());

        // Verify that updating a file in "deps" doesn't pollute "maven"
        entries = files.getUpdatedEntriesAndRefresh();
        Assertions.assertEquals(0, entries.size());
    }
}
