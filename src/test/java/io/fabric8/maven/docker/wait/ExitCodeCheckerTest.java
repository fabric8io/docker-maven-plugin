package io.fabric8.maven.docker.wait;

import io.fabric8.maven.docker.access.DockerAccessException;
import io.fabric8.maven.docker.model.Container;
import io.fabric8.maven.docker.service.QueryService;
import mockit.Expectations;
import mockit.Mocked;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;


public class ExitCodeCheckerTest {

    private static final String CONTAINER_ID = "1234";

    @Mocked
    private QueryService queryService;
    @Mocked
    private Container container;

    @Test
    public void checkReturnsFalseIfContainerDoesNotExist() throws Exception {

        new Expectations() {{
            Exception e = new DockerAccessException("Cannot find container %s", CONTAINER_ID);
            queryService.getMandatoryContainer(CONTAINER_ID); result = e;
        }};

        ExitCodeChecker checker = new ExitCodeChecker(0, queryService, CONTAINER_ID);
        assertThat(checker.check()).isFalse();
    }

    @Test
    public void checkReturnsFalseIfContainerIsStillRunning() throws Exception {

        new Expectations() {{
            container.getExitCode(); result = null;
        }};

        ExitCodeChecker checker = new ExitCodeChecker(0, queryService, CONTAINER_ID);
        assertThat(checker.check()).isFalse();
    }

    @Test
    public void checkReturnsFalseIfActualExitCodeDoesNotMatchExpectedExitCode() throws Exception {

        new Expectations() {{
            container.getExitCode(); result = 1;
        }};

        ExitCodeChecker checker = new ExitCodeChecker(0, queryService, CONTAINER_ID);
        assertThat(checker.check()).isFalse();
    }

    @Test
    public void checkReturnsTrueIfActualExitCodeMatchesExpectedExitCode() throws Exception {

        new Expectations() {{
            container.getExitCode(); result = 0;
        }};

        ExitCodeChecker checker = new ExitCodeChecker(0, queryService, CONTAINER_ID);
        assertThat(checker.check()).isTrue();
    }
}
