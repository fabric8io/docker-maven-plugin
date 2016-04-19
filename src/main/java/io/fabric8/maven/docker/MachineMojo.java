package io.fabric8.maven.docker;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import io.fabric8.maven.docker.util.AnsiLogger;
import io.fabric8.maven.docker.util.EnvUtil;
import io.fabric8.maven.docker.util.Logger;

/**
 * launch docker-machine to obtain environment settings
 * set all the returned variables in the System environment map
 */
@Mojo(name = "env", defaultPhase = LifecyclePhase.VALIDATE)
public class MachineMojo extends AbstractMojo {

    private static final String SET_PREFIX = "SET ";
    private static final int SET_PREFIX_LEN = SET_PREFIX.length();

    // Whether to use color
    @Parameter(property = "docker.useColor", defaultValue = "true")
    protected boolean useColor;

    // For verbose output
    @Parameter(property = "docker.verbose", defaultValue = "false")
    protected boolean verbose;

    // Whether to skip docker altogether
    @Parameter(property = "docker.skip", defaultValue = "false")
    private boolean skip;

    private Logger log;
    private ExecutorService executor;

    /**
     * Entry point for this plugin.
     *
     * @throws MojoExecutionException
     * @throws MojoFailureException
     */
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (!skip) {
            log = new AnsiLogger(getLog(), useColor, verbose);
            executor = Executors.newFixedThreadPool(1);
            try {
                int rc = executeDockerMachineEnv();
                if (rc != 0) {
                    throw new MojoExecutionException("docker-machine exited " + rc);
                }
            } finally {
                executor.shutdown();
            }
        }
    }

    private int executeDockerMachineEnv() throws MojoExecutionException {
        final Process process = startDockerMachineProcess();
        try {
            closeOutputStream(process.getOutputStream());
            Future<IOException> future = startErrorStreamPump(process.getErrorStream());
            outputStreamPump(process.getInputStream());
            stopErrorStreamPump(future);
            return checkProcessExit(process);
        } catch (MojoExecutionException e) {
            process.destroy();
            throw e;
        }
    }

    private int checkProcessExit(Process process) {
        try {
            return process.exitValue();
        } catch (IllegalThreadStateException e) {
            process.destroy();
            return 0;
        }
    }

    private void closeOutputStream(OutputStream outputStream) {
        try {
            outputStream.close();
        } catch (IOException e) {
            log.info("failed to close docker-machine output stream: " + e.getMessage());
        }
    }

    private Process startDockerMachineProcess() throws MojoExecutionException {
        try {
            return Runtime.getRuntime().exec(new String[] { "docker-machine", "env", "--shell", "cmd" });
        } catch (IOException e) {
            throw new MojoExecutionException("failed to start docker-machine", e);
        }
    }

    private void outputStreamPump(final InputStream inputStream) throws MojoExecutionException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));) {
            for (;;) {
                String line = reader.readLine();
                if (line == null) {
                    break;
                }
                log.verbose(line);
                if (line.startsWith(SET_PREFIX)) {
                    setEnvironmentVariable(line.substring(SET_PREFIX_LEN));
                }
            }
        } catch (IOException e) {
            throw new MojoExecutionException("failed to read docker-machine output", e);
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
        EnvUtil.putEnv(name, value);
    }

    private Future<IOException> startErrorStreamPump(final InputStream errorStream) {
        return executor.submit(new Callable<IOException>() {
            @Override
            public IOException call() {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(errorStream));) {
                    for (;;) {
                        String line = reader.readLine();
                        if (line == null) {
                            break;
                        }
                        log.error(line);
                    }
                    return null;
                } catch (IOException e) {
                    return e;
                }
            }
        });
    }

    private void stopErrorStreamPump(Future<IOException> future) throws MojoExecutionException {
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
