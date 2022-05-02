package integration;

import org.apache.maven.plugin.logging.SystemStreamLog;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import io.fabric8.maven.docker.config.DockerMachineConfiguration;
import io.fabric8.maven.docker.util.AnsiLogger;
import io.fabric8.maven.docker.access.DockerMachine;

/*
 * if run from your ide, this test assumes you have docker-machine installed
 */
@Disabled
class DockerMachineIT {

    @Test
    void testLaunchDockerMachine() throws Exception {
        DockerMachineConfiguration mc = new DockerMachineConfiguration("default","true","true");
        DockerMachine de = new DockerMachine(new AnsiLogger(new SystemStreamLog(), true, "build"), mc);
        Assertions.assertNotNull(de.getConnectionParameter(null));
    }
}
