package io.fabric8.maven.docker.log;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.Assert.assertThat;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.maven.shared.utils.io.FileUtils;
import org.junit.Before;
import org.junit.Test;

import io.fabric8.maven.docker.access.log.LogCallback;
import io.fabric8.maven.docker.access.log.LogCallback.DoneException;
import io.fabric8.maven.docker.util.Timestamp;

public class DefaultLogCallbackTest {

    private Path path;

    private LogOutputSpec spec;

    private LogCallback callback;

    private Timestamp ts;

    @Before
    public void before() throws FileNotFoundException {
        path = Paths.get("target", "one.log");
        path.toFile().delete();
        spec = new LogOutputSpec.Builder().prefix("callback-test")
                .file(path.toString()).build();
        callback = new DefaultLogCallback(spec);
        callback.open();
        ts = new Timestamp("2016-12-21T15:09:00.999666333Z");
    }

    @Test
    public void shouldLogSequentially() throws IOException, DoneException {
        callback.log(1, ts, "line 1");
        callback.log(1, ts, "line 2");
        callback.close();

        List<String> lines = Arrays.asList(FileUtils.fileReadArray(path.toFile()));
        assertThat(lines, contains("callback-test> line 1", "callback-test> line 2"));
    }

    @Test
    public void shouldLogError() throws IOException, DoneException {
        callback.error("error 1");
        callback.log(1, ts, "line 2");
        callback.error("error 3");
        callback.close();

        List<String> lines = Arrays.asList(FileUtils.fileReadArray(path.toFile()));
        assertThat(lines, contains("error 1", "callback-test> line 2", "error 3"));
    }

    @Test
    public void shouldLogToStdout() throws IOException, DoneException {
        // we don't need the default stream for this test
        callback.close();

        path = Paths.get("target", "stdout.log");
        FileOutputStream os = new FileOutputStream(path.toFile());
        PrintStream ps = new PrintStream(os);
        PrintStream stdout = System.out;
        try {
            System.setOut(ps);
            spec = new LogOutputSpec.Builder().prefix("stdout")
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

            List<String> lines = Arrays.asList(FileUtils.fileReadArray(path.toFile()));
            assertThat(lines, contains("stdout> line 1", "stdout> line 2", "stdout> line 3", "stdout> line 4"));
        } finally {
            System.setOut(stdout);
        }
    }

    @Test
    public void shouldKeepStreamOpen() throws IOException, DoneException {
        DefaultLogCallback callback2 = new DefaultLogCallback(spec);
        callback2.open();
        callback.log(1, ts, "line 1");
        callback2.log(1, ts, "line 2");
        callback.log(1, ts, "line 3");
        callback.close();
        callback2.log(1, ts, "line 4");
        callback2.close();

        List<String> lines = Arrays.asList(FileUtils.fileReadArray(path.toFile()));
        assertThat(lines,
                contains("callback-test> line 1", "callback-test> line 2", "callback-test> line 3", "callback-test> line 4"));
    }

    @Test
    public void shouldLogInParallel() throws IOException, DoneException, InterruptedException {
        DefaultLogCallback callback2 = new DefaultLogCallback(spec);
        callback2.open();

        ExecutorService executorService = Executors.newFixedThreadPool(2);
        LoggerTask task1 = new LoggerTask(callback, 1);
        LoggerTask task2 = new LoggerTask(callback2, 11);
        executorService.submit(task1);
        executorService.submit(task2);
        executorService.shutdown();
        executorService.awaitTermination(1, TimeUnit.SECONDS);

        List<String> lines = Arrays.asList(FileUtils.fileReadArray(path.toFile()));
        assertThat(lines.size(), is(20));

        // fill set with expected line numbers
        Set<Integer> indexes = new HashSet<>();
        for (int i = 1; i <= 20; i++) {
            indexes.add(i);
        }

        // remove found line numbers from set
        for (String line : lines) {
            String prefix = "callback-test> line ";
            assertThat(line, startsWith(prefix));
            String suffix = line.substring(prefix.length());
            indexes.remove(Integer.parseInt(suffix));
        }

        // expect empty set
        assertThat(indexes, is(empty()));
    }

    private class LoggerTask implements Runnable {

        private LogCallback cb;

        private int start;

        LoggerTask(LogCallback cb, int start) {
            this.cb = cb;
            this.start = start;
        }

        @Override
        public void run() {
            for (int i = 0; i < 10; i++) {
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
