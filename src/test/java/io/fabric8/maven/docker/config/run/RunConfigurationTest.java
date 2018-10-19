package io.fabric8.maven.docker.config.run;

import java.util.List;

import org.junit.Test;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author roland
 * @since 19.10.18
 */
public class RunConfigurationTest {


    @Test
    public void splitAtCommas() {
        List data[] = new List[] {
            asList("db,postgres:9:db", "postgres:db"), asList("db", "postgres:9:db", "postgres:db"),
            asList("a1,b2,,c3", null, "m4 ,   m9", null), asList("a1", "b2", "c3", "m4", "m9"),
            asList(",,,   ,,,, , ,,, ,", ",   ,,, , , ,,      a"), asList("a")
        };

        for (int i = 0; i < data.length; i += 2) {
            RunConfiguration config = prepareWithLinks(data[i]);
            assertThat(config.getLinks()).containsExactly((String[]) data[i + 1].toArray());
        }
    }

    @Test
    public void splitAtCommasEmpty() {
        assertThat(prepareWithLinks().getLinks()).isEmpty();
        assertThat(prepareWithLinks("").getLinks()).isEmpty();
        assertThat(new RunConfiguration.Builder().build().getLinks()).isEmpty();
    }


    private RunConfiguration prepareWithLinks(String ... links) {
        return prepareWithLinks(asList(links));
    }

    private RunConfiguration prepareWithLinks(List<String> links) {
        return new RunConfiguration.Builder()
            .links(links)
            .build();
    }

}
