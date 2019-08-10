package io.fabric8.maven.docker.access.util;
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

import java.io.*;
import java.util.concurrent.*;

import io.fabric8.maven.docker.util.AnsiLogger;
import io.fabric8.maven.docker.util.Logger;
import org.apache.maven.shared.utils.StringUtils;

/**
 * @author roland
 * @since 14/09/16
 */
public abstract class ExternalCommand {
    protected final Logger log;

    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    private int statusCode;

    public ExternalCommand(Logger log) {
        this.log = log;
    }

    public void execute() throws IOException {
        execute(null);
    }

    public void execute(String processInput) throws IOException {
        final Process process = startProcess();
        start();
        try {
            inputStreamPump(process.getOutputStream(),processInput);

            Future<IOException> stderrFuture = startStreamPump(process.getErrorStream());
            outputStreamPump(process.getInputStream());

            stopStreamPump(stderrFuture);
            checkProcessExit(process);
        } catch (IOException e) {
            process.destroy();
            throw e;
        } finally {
            end();
        }
        if (statusCode != 0) {
            throw new IOException(String.format("Process '%s' exited with status %d",
                                                getCommandAsString(), statusCode));
        }

    }

    // Hooks for logging ...
    protected void start() {}

    protected void end() {}

    protected int getStatusCode() {
        return statusCode;
    }

    private void checkProcessExit(Process process) {
        try {
            statusCode = process.waitFor();
            executor.shutdown();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        } catch (IllegalThreadStateException | InterruptedException e) {
            process.destroy();
            statusCode = -1;
        }
    }

    private void inputStreamPump(OutputStream outputStream,String processInput) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream))) {
            if (processInput != null) {
                writer.write(processInput);
                writer.flush();
            }
        } catch (IOException e) {
            log.info("Failed to close process output stream: %s", e.getMessage());
        }
    }

    private Process startProcess() throws IOException {
        try {
            return Runtime.getRuntime().exec(getArgs());
        } catch (IOException e) {
            throw new IOException(String.format("Failed to start '%s' : %s",
                                                getCommandAsString(),
                                                e.getMessage()), e);
        }
    }

    protected String getCommandAsString() {
        return StringUtils.join(getArgs(), " ");
    }

    protected abstract String[] getArgs();

    private void outputStreamPump(final InputStream inputStream) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            for (; ; ) {
                String line = reader.readLine();
                if (line == null) {
                    break;
                }
                processLine(line);
            }
        } catch (IOException e) {
            throw new IOException(String.format("Failed to read process '%s' output: %s",
                                                getCommandAsString(),
                                                e.getMessage()), e);
        }
    }

    protected void processLine(String line) {
        log.verbose(Logger.LogVerboseCategory.BUILD,line);
    }

    private Future<IOException> startStreamPump(final InputStream errorStream) {
        return executor.submit(new Callable<IOException>() {
            @Override
            public IOException call() {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(errorStream));) {
                    for (; ; ) {
                        String line = reader.readLine();
                        if (line == null) {
                            break;
                        }
                        synchronized (log) {
                            log.warn(line);
                        }
                    }
                    return null;
                } catch (IOException e) {
                    return e;
                }
            }
        });
    }

    private void stopStreamPump(Future<IOException> future) throws IOException {
        try {
            IOException e = future.get(2, TimeUnit.SECONDS);
            if (e != null) {
                throw new IOException(String.format("Failed to read process '%s' error stream",
                                                    getCommandAsString()), e);
            }
        } catch (InterruptedException ignore) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException | TimeoutException e) {
            throw new IOException(String.format("Failed to stop process '%s' error stream",
                                                getCommandAsString()), e);
        }
    }
}
