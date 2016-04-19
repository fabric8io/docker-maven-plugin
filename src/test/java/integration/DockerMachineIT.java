package integration;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import io.fabric8.maven.docker.MachineMojo;
import io.fabric8.maven.docker.util.EnvUtil;

/*
 * if run from your ide, this test assumes you have docker-machine installed
 */
@Ignore
public class DockerMachineIT {

    @Test
    public void testBuildImage() throws Exception {
        Assert.assertTrue(System.getenv("DOCKER_HOST")==null);

        MachineMojo mm = new MachineMojo();
        mm.execute();

        Assert.assertTrue(EnvUtil.getEnv("DOCKER_HOST")!=null);
    }
}
