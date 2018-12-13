package io.fabric8.maven.docker.util;

import java.util.*;

import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.config.run.RunConfiguration;
import io.fabric8.maven.docker.service.QueryService;
import mockit.Expectations;
import mockit.Mocked;
import org.junit.Before;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.fail;

/**
 * @author roland
 * @since 16.10.14
 */
public class StartOrderResolverTest {

    @Mocked
    private QueryService queryService;

    @Before
    @SuppressWarnings("unused")
    public void setup() throws Exception {
        new Expectations() {{
            queryService.hasContainer((String) withNotNull());
            result = false;
            minTimes = 1;
        }};
    }

    @Test
    public void simple() {
        checkData(new Object[][]{
                { new T[]{new T("1")}, new T[]{new T("1")}},
                { new T[]{new T("1", "2"), new T("2")}, new T[]{new T("2"), new T("1", "2")} },
                { new T[]{new T("1", "2", "3"), new T("2", "3"), new T("3")}, new T[]{new T("3"), new T("2", "3"), new T("1", "2", "3")} },
        });
    }

    @Test(expected = IllegalStateException.class)
    public void circularDep() {
        checkData(new Object[][] {
                {new T[]{new T("1", "2"), new T("2", "1")}, new T[]{new T("1", "2"), new T("2", "1")}}
        });
        fail();
    }

    private void checkData(Object[][] data) {
        for (Object[] aData : data) {
            ImageConfiguration[] input = (ImageConfiguration[]) aData[0];
            ImageConfiguration[] expected = (ImageConfiguration[]) aData[1];
            List<ImageConfiguration> result = new StartOrderResolver(queryService).resolve(Arrays.asList(input));
            assertThat(result.size()).isEqualTo(expected.length);
            for (int i = 0; i < expected.length; i++) {
                assertThat(result.get(i).getName()).isEqualTo(expected[i].getName());
                assertThat(result.get(i).getRunConfiguration().getLinks()).containsExactly(
                    expected[i].getRunConfiguration().getLinks().toArray(new String[0]));
            }
        }
    }


    // ============================================================================

    private static class T extends ImageConfiguration {

        private String id;
        private List<String> deps;

        private T(String id,String ... dep) {
            this.id = id;
            deps = new ArrayList<>();
            Collections.addAll(deps, dep);
        }

        @Override
        public String getName() {
            return id;
        }

        @Override
        public RunConfiguration getRunConfiguration() {
            return new RunConfiguration() {
                @Override
                public List<String> getLinks() {
                    return deps;
                }
            };
        }
    }
}
