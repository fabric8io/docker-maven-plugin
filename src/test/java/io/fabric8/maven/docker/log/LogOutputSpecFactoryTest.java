package io.fabric8.maven.docker.log;

import java.util.Arrays;
import java.util.Collection;

import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.config.run.LogConfiguration;
import io.fabric8.maven.docker.config.run.RunConfiguration;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import static org.junit.Assert.assertEquals;

/**
 * @author roland
 * @since 04.11.17
 */
@RunWith(Parameterized.class)
public class LogOutputSpecFactoryTest {

    private static String ALIAS = "fcn";
    private static String NAME = "rhuss/fcn:1.0";
    private static String CONTAINER_ID = "1234567890";

    @Parameterized.Parameters(name = "{index}: format \"{0}\" --> \"{1}\"")
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
            { "%z", "" },
            { null, ALIAS + "> "},
            { "%c", CONTAINER_ID.substring(0,6) },
            { "%C: ", CONTAINER_ID + ": " },
            { "%n -- ", NAME + " -- " },
            { "%z%c%n%C %a", CONTAINER_ID.substring(0,6) + NAME + CONTAINER_ID + " " + ALIAS }
           });
    }

    @Parameterized.Parameter(0)
    public String prefixFormat;

    @Parameterized.Parameter(1)
    public String expectedPrefix;

    @Test
    public void prefix() {
        LogOutputSpec spec = createSpec(prefixFormat);
        assertEquals(expectedPrefix, spec.getPrompt(false, null));
    }

    private LogOutputSpec createSpec(String prefix) {
        LogOutputSpecFactory factory = new LogOutputSpecFactory(false, false, null);
        LogConfiguration logConfig = new LogConfiguration.Builder().prefix(prefix).build();
        RunConfiguration runConfig = new RunConfiguration.Builder().log(logConfig).build();
        ImageConfiguration imageConfiguration = new ImageConfiguration.Builder().alias(ALIAS).name(NAME).runConfig(runConfig).build();
        return factory.createSpec(CONTAINER_ID,imageConfiguration);
    }
}
