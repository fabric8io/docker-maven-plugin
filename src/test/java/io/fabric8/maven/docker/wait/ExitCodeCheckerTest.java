package io.fabric8.maven.docker.wait;

import io.fabric8.maven.docker.access.DockerAccessException;
import io.fabric8.maven.docker.model.Container;
import io.fabric8.maven.docker.service.QueryService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExitCodeCheckerTest {

    private static final String CONTAINER_ID = "1234";

    @Mock
    private QueryService queryService;

    @Mock
    private Container container;

    @Test
    void checkReturnsFalseIfContainerDoesNotExist() throws DockerAccessException {

        Mockito.doThrow(new DockerAccessException("Cannot find container %s", CONTAINER_ID))
            .when(queryService).getMandatoryContainer(CONTAINER_ID);

        ExitCodeChecker checker = new ExitCodeChecker(0, queryService, CONTAINER_ID);
        Assertions.assertFalse(checker.check());
    }

    @Test
    void checkReturnsFalseIfContainerIsStillRunning() throws DockerAccessException {

        Mockito.doReturn(container).when(queryService).getMandatoryContainer(CONTAINER_ID);
        Mockito.doReturn(null).when(container).getExitCode();

        ExitCodeChecker checker = new ExitCodeChecker(0, queryService, CONTAINER_ID);
        Assertions.assertFalse(checker.check());
    }

    @Test
    void checkReturnsFalseIfActualExitCodeDoesNotMatchExpectedExitCode() throws DockerAccessException {

        Mockito.doReturn(container).when(queryService).getMandatoryContainer(CONTAINER_ID);
        Mockito.doReturn(1).when(container).getExitCode();

        ExitCodeChecker checker = new ExitCodeChecker(0, queryService, CONTAINER_ID);
        Assertions.assertFalse(checker.check());
    }

    @Test
    void checkReturnsTrueIfActualExitCodeMatchesExpectedExitCode() throws DockerAccessException {

        Mockito.doReturn(container).when(queryService).getMandatoryContainer(CONTAINER_ID);
        Mockito.doReturn(0).when(container).getExitCode();

        ExitCodeChecker checker = new ExitCodeChecker(0, queryService, CONTAINER_ID);
        Assertions.assertTrue(checker.check());
    }
}
