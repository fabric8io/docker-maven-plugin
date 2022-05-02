package io.fabric8.maven.docker.log;

import io.fabric8.maven.docker.access.log.LogCallback;
import io.fabric8.maven.docker.access.log.LogCallback.DoneException;
import io.fabric8.maven.docker.util.TimestampFactory;
import org.apache.maven.shared.utils.io.FileUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

class DefaultLogCallbackTest {

    private File file;

    private LogOutputSpec spec;

    private LogCallback callback;

    private ZonedDateTime ts;

    private static final int NR_LOOPS = 100;

    @BeforeEach
    void before() throws IOException {
        file = File.createTempFile("logcallback", ".log");
        file.deleteOnExit();
        spec = new LogOutputSpec.Builder().prefix("callback-test> ")
            .file(file.toString()).build();
        callback = new DefaultLogCallback(spec);
        callback.open();
        ts = TimestampFactory.createTimestamp("2016-12-21T15:09:00.999666333Z");
    }

    @Test
    void shouldLogSequentially() throws IOException, DoneException {
        callback.log(1, ts, "line 1");
        callback.log(1, ts, "line 2");
        callback.close();

        Assertions.assertEquals(
            Arrays.asList("callback-test> line 1", "callback-test> line 2"),
            Arrays.asList(FileUtils.fileReadArray(file)));
    }

    @Test
    void shouldLogError() throws IOException, DoneException {
        callback.error("error 1");
        callback.log(1, ts, "line 2");
        callback.error("error 3");
        callback.close();

        Assertions.assertEquals(
            Arrays.asList("error 1", "callback-test> line 2", "error 3"),
            Arrays.asList(FileUtils.fileReadArray(file)));
    }

    @Test
    void shouldLogToStdout() throws IOException, DoneException {
        // we don't need the default stream for this test
        callback.close();

        file = File.createTempFile("logcallback-stdout", ".log");
        file.deleteOnExit();
        FileOutputStream os = new FileOutputStream(file);
        PrintStream ps = new PrintStream(os);
        PrintStream stdout = System.out;
        try {
            System.setOut(ps);
            spec = new LogOutputSpec.Builder().prefix("stdout> ")
                .build();
            callback = new DefaultLogCallback(spec);
            callback.open();
            DefaultLogCallback callback2 = new DefaultLogCallback(spec);
            callback2.open();

            callback.log(1, ts, "line 1");
            callback2.log(1, ts, "line 2");
            callback.log(1, ts, "line 3");
            callback.close();
            callback2.log(1, ts, "line 4");
            callback2.close();

            Assertions.assertEquals(
                Arrays.asList("stdout> line 1", "stdout> line 2", "stdout> line 3", "stdout> line 4"),
                Arrays.asList(FileUtils.fileReadArray(file)));
        } finally {
            System.setOut(stdout);
        }
    }

    @Test
    void shouldKeepStreamOpen() throws IOException, DoneException {
        DefaultLogCallback callback2 = new DefaultLogCallback(spec);
        callback2.open();
        callback.log(1, ts, "line 1");
        callback2.log(1, ts, "line 2");
        callback.log(1, ts, "line 3");
        callback.close();
        callback2.log(1, ts, "line 4");
        callback2.close();

        Assertions.assertEquals(
            Arrays.asList("callback-test> line 1", "callback-test> line 2", "callback-test> line 3", "callback-test> line 4"),
            Arrays.asList(FileUtils.fileReadArray(file)));
    }

    @Test
    void shouldLogInParallel() throws IOException, InterruptedException {
        DefaultLogCallback callback2 = new DefaultLogCallback(spec);
        callback2.open();

        ExecutorService executorService = Executors.newFixedThreadPool(2);
        LoggerTask task1 = new LoggerTask(callback, 1);
        LoggerTask task2 = new LoggerTask(callback2, 1 + NR_LOOPS);
        executorService.submit(task1);
        executorService.submit(task2);
        executorService.awaitTermination(1, TimeUnit.SECONDS);

        List<String> lines = Arrays.asList(FileUtils.fileReadArray(file));
        Assertions.assertEquals(NR_LOOPS * 2, lines.size());

        // fill set with expected line numbers
        Set<Integer> indexes = new HashSet<>();
        for (int i = 1; i <= 2 * NR_LOOPS; i++) {
            indexes.add(i);
        }

        String prefix = "callback-test> line ";
        // remove found line numbers from set
        for (String line : lines) {
            Assertions.assertTrue(line.startsWith(prefix));
            String suffix = line.substring(prefix.length());
            indexes.remove(Integer.parseInt(suffix));
        }

        // expect empty set
        Assertions.assertEquals(0, indexes.size());
    }

    @Test
    void shouldCreateParentDirs() throws IOException {
        Path dir = Files.createTempDirectory("log");
        try {
            file = dir.resolve("non/existing/dirs/file.log").toFile();
            spec = new LogOutputSpec.Builder().prefix("callback-test> ")
                .file(file.toString()).build();
            callback = new DefaultLogCallback(spec);
            callback.open();
            Assertions.assertTrue(file.exists());
        } finally {
            Files.walk(dir)
                .sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
        }
    }

    private class LoggerTask implements Runnable {

        private final LogCallback cb;
        private final int start;

        LoggerTask(LogCallback cb, int start) {
            this.cb = cb;
            this.start = start;
        }

        @Override
        public void run() {
            for (int i = 0; i < NR_LOOPS; i++) {
                try {
                    callback.log(1, ts, "line " + (start + i));
                } catch (DoneException e) {
                    // ignore
                }
            }
            cb.close();
        }
    }
}
