package io.fabric8.maven.docker.access;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.fabric8.maven.docker.util.Logger;
import org.apache.maven.plugin.MojoExecutionException;

import io.fabric8.maven.docker.config.DockerMachineConfiguration;

/**
 * launch docker-machine to obtain environment settings
 */
public class DockerMachine {

    private final Logger log;
    private final DockerMachineConfiguration machine;

    public DockerMachine(Logger log, DockerMachineConfiguration machine) throws MojoExecutionException {
        this.log = log;
        this.machine = machine;

        Status status = new StatusCommand().getStatus();
        switch (status) {
        case DoesNotExist:
            if (Boolean.TRUE == machine.getAutoCreate()) {
                new CreateCommand().execute();
            } else {
                throw new MojoExecutionException(machine.getName() + " does not exist and docker.machine.autoCreate is false");
            }
            break;
        case Running:
            break;
        case Stopped:
            new StartCommand().execute();
            break;
        }
    }

    enum Status {
        DoesNotExist, Running, Stopped
    }

    public Map<String, String> getEnvironment() throws MojoExecutionException {
        return new EnvCommand().getEnvironment();
    }

    abstract class DockerCommand {
        private final ExecutorService executor = Executors.newFixedThreadPool(2);
        int statusCode;

        void execute() throws MojoExecutionException {
            final Process process = startDockerMachineProcess();
            try {
                closeOutputStream(process.getOutputStream());
                Future<IOException> stderrFuture = startStreamPump(process.getErrorStream());
                outputStreamPump(process.getInputStream());

                stopStreamPump(stderrFuture);
                checkProcessExit(process);
            } catch (MojoExecutionException e) {
                process.destroy();
                throw e;
            }
            if (statusCode != 0) {
                throw new MojoExecutionException("docker-machine exited with status " + statusCode);
            }
        }

        private void checkProcessExit(Process process) {
            try {
                executor.shutdown();
                executor.awaitTermination(10, TimeUnit.SECONDS);
                statusCode = process.exitValue();
            } catch (IllegalThreadStateException | InterruptedException e) {
                process.destroy();
                statusCode = -1;
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

        private Future<IOException> startStreamPump(final InputStream errorStream) {
            return executor.submit(new Callable<IOException>() {
                @Override
                public IOException call() {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(errorStream));) {
                        for (;;) {
                            String line = reader.readLine();
                            if (line == null) {
                                break;
                            }
                            synchronized (log) {
                                log.error(line);
                            }
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

    // docker-machine env <name>
    class EnvCommand extends DockerCommand {

        private final Map<String, String> env = new HashMap<>();

        @Override
        protected String[] getArgs() {
            return new String[] { "docker-machine", "env", machine.getName(), "--shell", "cmd" };
        }

        @Override
        protected void processLine(String line) {
        	if(log.isDebugEnabled()) {
        		log.verbose("%s", line);
        	}
            if (line.startsWith(SET_PREFIX)) {
                setEnvironmentVariable(line.substring(SET_PREFIX_LEN));
            }
        }

        // parse line like SET DOCKER_HOST=tcp://192.168.99.100:2376
        private void setEnvironmentVariable(String line) {
            int equals = line.indexOf('=');
            if (equals < 0) {
                return;
            }
            String name = line.substring(0, equals);
            String value = line.substring(equals + 1);
            log.info(name + "=" + value);
            env.put(name, value);
        }

        public Map<String, String> getEnvironment() throws MojoExecutionException {
            execute();
            return env;
        }
    }

    // docker-machine status <name>
    class StatusCommand extends DockerCommand {

        private Status status;
        private String message;

        @Override
        protected String[] getArgs() {
            return new String[] { "docker-machine", "status", machine.getName() };
        }

        @Override
        protected void processLine(String line) {
            log.verbose(line);
            if ("Running".equals(line)) {
                status = Status.Running;
            } else if ("Stopped".equals(line)) {
                status = Status.Stopped;
            } else {
                message = "Unknown status - " + line;
            }
        }

        public Status getStatus() throws MojoExecutionException {
            try {
                execute();
            }
            catch(MojoExecutionException ex) {
                if(statusCode==1) {
                    status = Status.DoesNotExist;
                }
                else {
                    throw ex;
                }
            }
            if (message != null) {
                throw new MojoExecutionException(message);
            }
            return status;
        }
    }

    // docker-machine create --driver virtualbox <name>
    class CreateCommand extends DockerCommand {

        @Override
        protected String[] getArgs() {
            List<String> args = new ArrayList<>();
            args.add("docker-machine");
            args.add("create");
            if (machine.getCreateOptions() != null) {
                for (Map.Entry<String, String> entry : machine.getCreateOptions().entrySet()) {
                    args.add("--" + entry.getKey());
                    String value = entry.getValue();
                    if (value != null && !value.isEmpty()) {
                        args.add(value);
                    }
                }
            }
            args.add(machine.getName());
            return args.toArray(new String[args.size()]);
        }
    }

    // docker-machine start <name>
    class StartCommand extends DockerCommand {

        @Override
        protected String[] getArgs() {
            return new String[] { "docker-machine", "start", machine.getName() };
        }
    }
}
