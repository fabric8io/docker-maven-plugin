package io.fabric8.maven.docker.access;

import java.io.*;
import java.util.*;

import io.fabric8.maven.docker.access.util.EnvCommand;
import io.fabric8.maven.docker.access.util.ExternalCommand;
import io.fabric8.maven.docker.config.DockerMachineConfiguration;
import io.fabric8.maven.docker.util.Logger;

/**
 * launch docker-machine to obtain environment settings
 */
public class DockerMachine implements DockerConnectionDetector.DockerHostProvider {

    private final Logger log;
    private final DockerMachineConfiguration machine;
    private boolean initialized = false;
    private Map<String, String> envMap;

    public DockerMachine(Logger log, DockerMachineConfiguration machine) {
        this.log = log;
        this.machine = machine;
    }

    enum Status {
        DoesNotExist, Running, Stopped
    }

    public synchronized DockerConnectionDetector.ConnectionParameter getConnectionParameter(String certPath) throws IOException {
        if (machine == null) {
            return null;
        }
        if (envMap == null) {
            envMap = getEnvironment();
        }
        String value = envMap.get("DOCKER_HOST");
        if (value == null) {
            return null;
        }
        log.info("DOCKER_HOST from docker-machine \"%s\" : %s", machine.getName(), value);
        return new DockerConnectionDetector.ConnectionParameter(value, certPath != null ? certPath : envMap.get("DOCKER_CERT_PATH"));
    }

    @Override
    public int getPriority() {
        // Just after environment variable priority-wise
        return 90;
    }

    private Map<String, String> getEnvironment() throws IOException {
        lazyInit();
        return new MachineEnvCommand().getEnvironment();
    }

    private synchronized void lazyInit() throws IOException {
        if (!initialized) {
            Status status = new StatusCommand().getStatus();
            switch (status) {
                case DoesNotExist:
                    if (Boolean.TRUE == machine.getAutoCreate()) {
                        new CreateCommand().execute();
                    } else {
                        throw new IllegalStateException(machine.getName() + " does not exist and docker.machine.autoCreate is false");
                    }
                    break;
                case Running:
                    break;
                case Stopped:
                    new StartCommand().execute();
                    if (Boolean.TRUE == machine.getRegenerateCertsAfterStart()) {
                        new RegenerateCertsCommand().execute();
                    }
                    break;
            }
        }
        initialized = true;
    }

    // docker-machine env <name>
    private class MachineEnvCommand extends EnvCommand {

        MachineEnvCommand() {
            super(DockerMachine.this.log, "SET ");
        }

        @Override
        protected String[] getArgs() {
            // use windows style with "SET "
            return new String[]{"docker-machine", "env", machine.getName(), "--shell", "cmd"};
        }
    }

    // docker-machine status <name>
    private class StatusCommand extends ExternalCommand {

        private Status status;
        private String message;

        StatusCommand() {
            super(DockerMachine.this.log);
        }

        @Override
        protected String[] getArgs() {
            return new String[]{"docker-machine", "status", machine.getName()};
        }

        @Override
        protected void processLine(String line) {
            log.info("Docker machine \"%s\" is %s",machine.getName(),line.toLowerCase());
            if ("Running".equals(line)) {
                status = Status.Running;
            } else if ("Stopped".equals(line)) {
                status = Status.Stopped;
            } else {
                message = "Unknown status - " + line;
            }
        }

        public Status getStatus() throws IOException {
            try {
                execute();
            } catch (IOException ex) {
                if (getStatusCode() == 1) {
                    status = Status.DoesNotExist;
                } else {
                    throw ex;
                }
            }
            if (message != null) {
                throw new IOException(message);
            }
            return status;
        }
    }

    // docker-machine create --driver virtualbox <name>
    private class CreateCommand extends ExternalCommand {

        private long start;

        CreateCommand() {
            super(DockerMachine.this.log);
        }

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

        @Override
        protected void start() {
            log.info("Creating docker machine \"%s\" with args %s",
                     machine.getName(),
                     machine.getCreateOptions() != null ? machine.getCreateOptions().toString() : "");
            log.info("This might take a while ...");
            start = System.currentTimeMillis();
        }

        @Override
        protected void end() {
            log.info("Created docker machine \"%s\" in %d seconds",machine.getName(), (System.currentTimeMillis() - start) / 1000);
        }
    }

    // docker-machine start <name>
    private class StartCommand extends ExternalCommand {

        private long start;

        StartCommand() {
            super(DockerMachine.this.log);
        }

        @Override
        protected String[] getArgs() {
            return new String[]{"docker-machine", "start", machine.getName()};
        }

        @Override
        protected void start() {
            log.info("Starting docker machine \"%s\"", machine.getName());
            start = System.currentTimeMillis();
        }

        @Override
        protected void end() {
            log.info("Started docker machine \"%s\" in %d seconds",machine.getName(), (System.currentTimeMillis() - start) / 1000);
        }
    }

    // docker-machine regenerate-certs <name>
    private class RegenerateCertsCommand extends ExternalCommand {

        private long start;

        RegenerateCertsCommand() {
            super(DockerMachine.this.log);
        }

        @Override
        protected String[] getArgs() {
            return new String[]{"docker-machine", "regenerate-certs", "-f", machine.getName()};
        }

        @Override
        protected void start() {
            log.info("Regenerating certificates for \"%s\"", machine.getName());
            start = System.currentTimeMillis();
        }

        @Override
        protected void end() {
            log.info("Regenerated certificates for \"%s\" in %d seconds",machine.getName(), (System.currentTimeMillis() - start) / 1000);
        }
    }
}
