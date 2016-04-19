package io.fabric8.maven.docker.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import org.apache.maven.plugin.MojoExecutionException;

/**
 * launch docker-machine to obtain environment settings
 */
public class DockerEnvironment {

    private final Map<String, String> env;
    private final Logger log;

    public DockerEnvironment(Logger log) throws MojoExecutionException {
        this.log = log;

        Map<String, Boolean> machines = new LsCommand().getMachines();
        String machine = "default";
        if (machines.isEmpty()) {
            new CreateCommand();
        } else {
            if (!machines.containsKey(machine)) {
                for (Map.Entry<String, Boolean> entry : machines.entrySet()) {
                    machine = entry.getKey();
                    if (entry.getValue()) {
                        // choose first running machine
                        break;
                    }
                }
            }
            if (!machines.get(machine)) {
                // "default" or chosen machine may need to be started
                new StartCommand(machine);
            }
        }

        env = new EnvCommand(machine).getEnvironment();
    }

    public Map<String, String> getEnvironment() {
        return env;
    }

    // docker-machine create --driver virtualbox default
    // docker-machine env [default]
    // docker-machine ls
    abstract class DockerCommand {
        private final ExecutorService executor = Executors.newFixedThreadPool(2);

        void execute() throws MojoExecutionException {
            final Process process = startDockerMachineProcess();
            try {
                closeOutputStream(process.getOutputStream());
                Future<IOException> stderrFuture = startStreamPump(process.getErrorStream(), (line) -> {
                    synchronized (log) {
                        log.error(line);
                    }
                });

                outputStreamPump(process.getInputStream());

                stopStreamPump(stderrFuture);
                checkProcessExit(process);
            } catch (MojoExecutionException e) {
                process.destroy();
                throw e;
            }
        }

        private void checkProcessExit(Process process) {
            try {
                executor.shutdown();
                executor.awaitTermination(10, TimeUnit.SECONDS);
                int rc = process.exitValue();
                if (rc != 0) {
                    log.info("docker-machine exited with status " + rc);
                }
            } catch (IllegalThreadStateException | InterruptedException e) {
                process.destroy();
            }
        }

        private void closeOutputStream(OutputStream outputStream) {
            try {
                outputStream.close();
            } catch (IOException e) {
                log.info("failed to close docker-machine output stream: " + e.getMessage());
            }
        }

        private Process startDockerMachineProcess(String... args) throws MojoExecutionException {
            try {
                return Runtime.getRuntime().exec(getArgs());
            } catch (IOException e) {
                throw new MojoExecutionException("failed to start docker-machine", e);
            }
        }

        protected abstract String[] getArgs();

        private void outputStreamPump(final InputStream inputStream) throws MojoExecutionException {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));) {
                for (;;) {
                    String line = reader.readLine();
                    if (line == null) {
                        break;
                    }
                    processLine(line);
                }
            } catch (IOException e) {
                throw new MojoExecutionException("failed to read docker-machine output", e);
            }
        }

        protected void processLine(String line) {
            log.info(line);
        }

        private Future<IOException> startStreamPump(final InputStream errorStream, Consumer<String> logLine) {
            return executor.submit(new Callable<IOException>() {
                @Override
                public IOException call() {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(errorStream));) {
                        for (;;) {
                            String line = reader.readLine();
                            if (line == null) {
                                break;
                            }
                            logLine.accept(line);
                        }
                        return null;
                    } catch (IOException e) {
                        return e;
                    }
                }
            });
        }

        private void stopStreamPump(Future<IOException> future) throws MojoExecutionException {
            try {
                IOException e = future.get(2, TimeUnit.SECONDS);
                if (e != null) {
                    throw new MojoExecutionException("failed to read docker-machine error stream", e);
                }
            } catch (InterruptedException ignore) {
                Thread.currentThread().interrupt();
            } catch (ExecutionException | TimeoutException e) {
                throw new MojoExecutionException("failed to stop docker-machine error stream", e);
            }
        }
    }

    private static final String SET_PREFIX = "SET ";
    private static final int SET_PREFIX_LEN = SET_PREFIX.length();

    // docker-machine env machine
    class EnvCommand extends DockerCommand {

        private final Map<String, String> env = new HashMap<>();
        private final String machine;

        public EnvCommand(String machine) throws MojoExecutionException {
            this.machine = machine;
            execute();
        }

        @Override
        protected String[] getArgs() {
            return new String[] { "docker-machine", "env", machine, "--shell", "cmd" };
        }

        @Override
        protected void processLine(String line) {
            log.verbose(line);
            if (line.startsWith(SET_PREFIX)) {
                setEnvironmentVariable(line.substring(SET_PREFIX_LEN));
            }
        }

        // parse line like SET DOCKER_HOST=tcp://192.168.99.100:2376
        private void setEnvironmentVariable(String setLine) {
            int equals = setLine.indexOf('=');
            if (equals < 0) {
                return;
            }
            String name = setLine.substring(0, equals);
            String value = setLine.substring(equals + 1);
            log.info(name + "=" + value);
            env.put(name, value);
        }

        public Map<String, String> getEnvironment() throws MojoExecutionException {
            return env;
        }
    }

    // docker-machine ls --format "{{.Name}} {{.State}}"
    class LsCommand extends DockerCommand {

        private final Map<String, Boolean> machines = new HashMap<>();

        public LsCommand() throws MojoExecutionException {
            execute();
        }

        @Override
        protected String[] getArgs() {
            return new String[] { "docker-machine", "ls", "--format", "{{.Name}} {{.State}}" };
        }

        @Override
        protected void processLine(String line) {
            log.verbose(line);
            int space = line.indexOf(' ');
            String machineName = line.substring(0, space);
            String state = line.substring(space + 1);
            machines.put(machineName, "Running".equalsIgnoreCase(state));
        }

        public Map<String, Boolean> getMachines() {
            return machines;
        }
    }

    // docker-machine create --driver virtualbox default
    class CreateCommand extends DockerCommand {

        public CreateCommand() throws MojoExecutionException {
            execute();
        }

        @Override
        protected String[] getArgs() {
            return new String[] { "docker-machine", "create", "--driver", "virtualbox", "default" };
        }
    }

    // docker-machine start machine
    class StartCommand extends DockerCommand {
        private final String machine;

        public StartCommand(String machine) throws MojoExecutionException {
            this.machine = machine;
            execute();
        }

        @Override
        protected String[] getArgs() {
            return new String[] { "docker-machine", "start", machine };
        }
    }
}
