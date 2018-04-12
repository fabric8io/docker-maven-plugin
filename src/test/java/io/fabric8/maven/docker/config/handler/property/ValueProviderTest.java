package io.fabric8.maven.docker.config.handler.property;

import java.util.*;

import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class ValueProviderTest {
    private ValueProvider provider;
    private Properties props;

    @Before
    public void setUp() throws Exception {
        props = new Properties();
    }

    private void configure(PropertyMode mode) {
        provider = new ValueProvider("docker", props, mode);
    }

    @Test
    public void testGetString_Only() {
        configure(PropertyMode.Only);
        assertEquals(null, provider.getString(ConfigKey.NAME, (String)null));
        assertEquals(null, provider.getString(ConfigKey.NAME, "ignored"));

        props.put("docker.name", "myname");
        assertEquals("myname", provider.getString(ConfigKey.NAME, (String)null));
        assertEquals("myname", provider.getString(ConfigKey.NAME, "ignored"));
    }

    @Test
    public void testGetString_Skip() {
        configure(PropertyMode.Skip);
        assertEquals(null, provider.getString(ConfigKey.NAME, (String)null));
        assertEquals("fromconfig", provider.getString(ConfigKey.NAME, "fromconfig"));

        props.put("docker.name", "ignored");
        assertEquals(null, provider.getString(ConfigKey.NAME, (String)null));
        assertEquals("fromconfig", provider.getString(ConfigKey.NAME, "fromconfig"));
    }

    @Test
    public void testGetString_Fallback() {
        configure(PropertyMode.Fallback);
        assertEquals(null, provider.getString(ConfigKey.NAME, (String)null));
        assertEquals("fromconfig", provider.getString(ConfigKey.NAME, "fromconfig"));

        props.put("docker.name", "fromprop");
        assertEquals("fromprop", provider.getString(ConfigKey.NAME, (String)null));
        assertEquals("fromconfig", provider.getString(ConfigKey.NAME, "fromconfig"));
    }

    @Test
    public void testGetString_Override() {
        configure(PropertyMode.Override);
        assertEquals(null, provider.getString(ConfigKey.NAME, (String)null));
        assertEquals("fromconfig", provider.getString(ConfigKey.NAME, "fromconfig"));

        props.put("docker.name", "fromprop");
        assertEquals("fromprop", provider.getString(ConfigKey.NAME, (String)null));
        assertEquals("fromprop", provider.getString(ConfigKey.NAME, "fromconfig"));
    }


    @Test
    public void testGetInt() {
        configure(PropertyMode.Only);
        assertEquals(null, provider.getInteger(ConfigKey.SHMSIZE, null));
        assertEquals(null, provider.getInteger(ConfigKey.SHMSIZE, 100));

        props.put("docker.shmsize", "200");
        assertEquals(200, (int)provider.getInteger(ConfigKey.SHMSIZE, null));
        assertEquals(200, (int)provider.getInteger(ConfigKey.SHMSIZE, 100));
    }


    @Test
    public void testGetList() {
        configure(PropertyMode.Only);
        assertEquals(null, provider.getList(ConfigKey.PORTS, null));
        assertEquals(null, provider.getList(ConfigKey.PORTS, Collections.singletonList("8080")));

        props.put("docker.ports.1", "200");

        assertThat(provider.getList(ConfigKey.PORTS, null), Matchers.contains("200"));
        assertThat(provider.getList(ConfigKey.PORTS, Collections.singletonList("8080")), Matchers.contains("200"));

        props.put("docker.ports.1", "200");
        props.put("docker.ports.2", "8080");
        assertThat(provider.getList(ConfigKey.PORTS, null), Matchers.contains("200", "8080"));
        assertThat(provider.getList(ConfigKey.PORTS, Collections.singletonList("123")), Matchers.contains("200", "8080"));

        configure(PropertyMode.Fallback);

        assertThat(provider.getList(ConfigKey.PORTS, null), Matchers.contains("200", "8080"));
        assertThat(provider.getList(ConfigKey.PORTS, Collections.singletonList("123")), Matchers.contains("123", "200", "8080"));

        configure(PropertyMode.Override);
        assertThat(provider.getList(ConfigKey.PORTS, null), Matchers.contains("200", "8080"));
        assertThat(provider.getList(ConfigKey.PORTS, Collections.singletonList("123")), Matchers.contains("200", "8080", "123"));

        // Test with another property that does not have CombinePolicy Merge
        props.put("docker.entrypoint.1", "ep1");
        props.put("docker.entrypoint.2", "ep2");
        assertThat(provider.getList(ConfigKey.ENTRYPOINT, null), Matchers.contains("ep1", "ep2"));
        assertThat(provider.getList(ConfigKey.ENTRYPOINT, Collections.singletonList("asd")), Matchers.contains("ep1", "ep2"));

        configure(PropertyMode.Fallback);
        assertThat(provider.getList(ConfigKey.ENTRYPOINT, null), Matchers.contains("ep1", "ep2"));
        assertThat(provider.getList(ConfigKey.ENTRYPOINT, Collections.singletonList("asd")), Matchers.contains("asd"));

        // Override combine policy
        props.put("docker.entrypoint._combine", "merge");

        assertThat(provider.getList(ConfigKey.ENTRYPOINT, Collections.singletonList("asd")), Matchers.contains("asd", "ep1", "ep2"));
    }



    @Test
    public void testGetMap() {
        configure(PropertyMode.Only);
        assertEquals(null, provider.getMap(ConfigKey.ENV_RUN, null));
        assertEquals(null, provider.getMap(ConfigKey.ENV_RUN, getTestMap("key", "value")));

        props.put("docker.envRun.myprop1", "pvalue1");
        props.put("docker.envRun.myprop2", "pvalue2");

        Map m = provider.getMap(ConfigKey.ENV_RUN, null);
        assertEquals(2, m.size());
        assertEquals("pvalue1", m.get("myprop1"));
        assertEquals("pvalue2", m.get("myprop2"));

        m = provider.getMap(ConfigKey.ENV_RUN, getTestMap("mycfg", "cvalue"));
        assertEquals(2, m.size());
        assertEquals("pvalue1", m.get("myprop1"));
        assertEquals("pvalue2", m.get("myprop2"));


        configure(PropertyMode.Override);

        m = provider.getMap(ConfigKey.ENV_RUN, null);
        assertEquals(2, m.size());
        assertEquals("pvalue1", m.get("myprop1"));
        assertEquals("pvalue2", m.get("myprop2"));

        m = provider.getMap(ConfigKey.ENV_RUN, getTestMap("ckey", "cvalue", "myprop1", "ignored"));
        assertEquals(3, m.size());
        assertEquals("pvalue1", m.get("myprop1"));
        assertEquals("pvalue2", m.get("myprop2"));
        assertEquals("cvalue", m.get("ckey"));


        configure(PropertyMode.Fallback);
        m = provider.getMap(ConfigKey.ENV_RUN, getTestMap("ckey", "cvalue", "myprop1", "overrides"));
        assertEquals(3, m.size());
        assertEquals("overrides", m.get("myprop1"));
        assertEquals("pvalue2", m.get("myprop2"));
        assertEquals("cvalue", m.get("ckey"));

        // Test with another property that does not have CombinePolicy Merge
        props.put("docker.buildOptions.boprop1", "popt1");
        props.put("docker.buildOptions.boprop2", "popt2");
        configure(PropertyMode.Override);
        m = provider.getMap(ConfigKey.BUILD_OPTIONS, null);
        assertEquals(2, m.size());
        assertEquals("popt1", m.get("boprop1"));
        assertEquals("popt2", m.get("boprop2"));

        m = provider.getMap(ConfigKey.BUILD_OPTIONS, getTestMap("ckey", "ignored", "myprop1", "ignored"));
        assertEquals(2, m.size());
        assertEquals("popt1", m.get("boprop1"));
        assertEquals("popt2", m.get("boprop2"));

        configure(PropertyMode.Fallback);
        m = provider.getMap(ConfigKey.BUILD_OPTIONS, getTestMap("ckey", "notignored1", "myprop1", "notignored2"));
        assertEquals(2, m.size());
        assertEquals("notignored1", m.get("ckey"));
        assertEquals("notignored2", m.get("myprop1"));

        // Override combine policy
        props.put("docker.buildOptions._combine", "merge");

        m = provider.getMap(ConfigKey.BUILD_OPTIONS, getTestMap("ckey", "notignored1", "boprop2", "notignored2"));
        assertEquals(3, m.size());
        assertEquals("popt1", m.get("boprop1"));
        assertEquals("notignored2", m.get("boprop2"));
        assertEquals("notignored1", m.get("ckey"));
    }



    private Map getTestMap(String ... vals) {
        Map ret = new HashMap();
        for (int i = 0; i < vals.length; i+=2) {
            ret.put(vals[i],vals[i+1]);
        }
        return ret;
    }

}