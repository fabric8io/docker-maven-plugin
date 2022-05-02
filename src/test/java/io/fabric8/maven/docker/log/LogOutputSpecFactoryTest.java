package io.fabric8.maven.docker.log;

import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.config.LogConfiguration;
import io.fabric8.maven.docker.config.RunImageConfiguration;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

/**
 * @author roland
 * @since 04.11.17
 */
class LogOutputSpecFactoryTest {

    private static String ALIAS = "fcn";
    private static String NAME = "rhuss/fcn:1.0";
    private static String CONTAINER_ID = "1234567890";

    private static Stream<Arguments> data() {
        return Stream.of(
            Arguments.of("%z", ""),
            Arguments.of(null, ALIAS + "> "),
            Arguments.of("%c", CONTAINER_ID.substring(0, 6)),
            Arguments.of("%C: ", CONTAINER_ID + ": "),
            Arguments.of("%n -- ", NAME + " -- "),
            Arguments.of("%z%c%n%C %a", CONTAINER_ID.substring(0, 6) + NAME + CONTAINER_ID + " " + ALIAS)
        );
    }

    @ParameterizedTest(name = "{index}: format \"{0}\" --> \"{1}\"")
    @MethodSource("data")
    void prefix(String prefixFormat, String expectedPrefix) {
        LogOutputSpec spec = createSpec(prefixFormat);
        Assertions.assertEquals(expectedPrefix, spec.getPrompt(false, null));
    }

    private LogOutputSpec createSpec(String prefix) {
        LogOutputSpecFactory factory = new LogOutputSpecFactory(false, false, null);
        LogConfiguration logConfig = new LogConfiguration.Builder().prefix(prefix).build();
        RunImageConfiguration runConfig = new RunImageConfiguration.Builder().log(logConfig).build();
        ImageConfiguration imageConfiguration = new ImageConfiguration.Builder().alias(ALIAS).name(NAME).runConfig(runConfig).build();
        return factory.createSpec(CONTAINER_ID, imageConfiguration);
    }
}
