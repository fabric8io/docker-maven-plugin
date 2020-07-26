package integration;

import org.apache.maven.plugin.logging.SystemStreamLog;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import io.fabric8.maven.docker.config.DockerMachineConfiguration;
import io.fabric8.maven.docker.util.AnsiLogger;
import io.fabric8.maven.docker.access.DockerMachine;

/*
 * if run from your ide, this test assumes you have docker-machine installed
 */
@Ignore
public class DockerMachineIT {

    @Test
    public void testLaunchDockerMachine() throws Exception {
        DockerMachineConfiguration mc = new DockerMachineConfiguration("default","true","true");
        DockerMachine de = new DockerMachine(new AnsiLogger(new SystemStreamLog(), true, "build"), mc);
        Assert.assertTrue(de.getConnectionParameter(null) != null);
    }
}
