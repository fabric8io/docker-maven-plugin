package io.fabric8.maven.docker.config.handler.property;

import java.util.*;

import com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ValueProviderTest {
    private ValueProvider provider;
    private Properties props;

    @BeforeEach
    void setUp() {
        props = new Properties();
    }

    private void configure(PropertyMode mode) {
        provider = new ValueProvider("docker", props, mode);
    }

    @Test
    void testGetString_Only() {
        configure(PropertyMode.Only);
        Assertions.assertNull(provider.getString(ConfigKey.NAME, (String) null));
        Assertions.assertNull(provider.getString(ConfigKey.NAME, "ignored"));

        props.put("docker.name", "myname");
        Assertions.assertEquals("myname", provider.getString(ConfigKey.NAME, (String) null));
        Assertions.assertEquals("myname", provider.getString(ConfigKey.NAME, "ignored"));
    }

    @Test
    void testGetString_Skip() {
        configure(PropertyMode.Skip);
        Assertions.assertNull(provider.getString(ConfigKey.NAME, (String) null));
        Assertions.assertEquals("fromconfig", provider.getString(ConfigKey.NAME, "fromconfig"));

        props.put("docker.name", "ignored");
        Assertions.assertNull(provider.getString(ConfigKey.NAME, (String) null));
        Assertions.assertEquals("fromconfig", provider.getString(ConfigKey.NAME, "fromconfig"));
    }

    @Test
    void testGetString_Fallback() {
        configure(PropertyMode.Fallback);
        Assertions.assertNull(provider.getString(ConfigKey.NAME, (String) null));
        Assertions.assertEquals("fromconfig", provider.getString(ConfigKey.NAME, "fromconfig"));

        props.put("docker.name", "fromprop");
        Assertions.assertEquals("fromprop", provider.getString(ConfigKey.NAME, (String) null));
        Assertions.assertEquals("fromconfig", provider.getString(ConfigKey.NAME, "fromconfig"));
    }

    @Test
    void testGetString_Override() {
        configure(PropertyMode.Override);
        Assertions.assertNull(provider.getString(ConfigKey.NAME, (String) null));
        Assertions.assertEquals("fromconfig", provider.getString(ConfigKey.NAME, "fromconfig"));

        props.put("docker.name", "fromprop");
        Assertions.assertEquals("fromprop", provider.getString(ConfigKey.NAME, (String) null));
        Assertions.assertEquals("fromprop", provider.getString(ConfigKey.NAME, "fromconfig"));
    }

    @Test
    void testGetInt() {
        configure(PropertyMode.Only);
        Assertions.assertNull(provider.getInteger(ConfigKey.SHMSIZE, null));
        Assertions.assertNull(provider.getInteger(ConfigKey.SHMSIZE, 100));

        props.put("docker.shmsize", "200");
        Assertions.assertEquals(200, (int) provider.getInteger(ConfigKey.SHMSIZE, null));
        Assertions.assertEquals(200, (int) provider.getInteger(ConfigKey.SHMSIZE, 100));
    }

    @Test
    void testGetList() {
        configure(PropertyMode.Only);
        Assertions.assertNull(provider.getList(ConfigKey.PORTS, null));
        Assertions.assertNull(provider.getList(ConfigKey.PORTS, Collections.singletonList("8080")));

        props.put("docker.ports.1", "200");
        List<String> expected = Collections.singletonList("200");

        Assertions.assertEquals(expected, provider.getList(ConfigKey.PORTS, null));
        Assertions.assertEquals(expected, provider.getList(ConfigKey.PORTS, Collections.singletonList("8080")));

        props.put("docker.ports.1", "200");
        props.put("docker.ports.2", "8080");
        expected = Arrays.asList("200", "8080");

        Assertions.assertEquals(expected, provider.getList(ConfigKey.PORTS, null));
        Assertions.assertEquals(expected, provider.getList(ConfigKey.PORTS, Collections.singletonList("123")));

        configure(PropertyMode.Fallback);

        Assertions.assertEquals(expected, provider.getList(ConfigKey.PORTS, null));
        Assertions.assertEquals(Arrays.asList("123", "200", "8080"), provider.getList(ConfigKey.PORTS, Collections.singletonList("123")));

        configure(PropertyMode.Override);
        Assertions.assertEquals(expected, provider.getList(ConfigKey.PORTS, null));
        Assertions.assertEquals(Arrays.asList("200", "8080", "123"), provider.getList(ConfigKey.PORTS, Collections.singletonList("123")));

        // Test with another property that does not have CombinePolicy Merge
        props.put("docker.entrypoint.1", "ep1");
        props.put("docker.entrypoint.2", "ep2");
        expected = Arrays.asList("ep1", "ep2");

        Assertions.assertEquals(expected, provider.getList(ConfigKey.ENTRYPOINT, null));
        Assertions.assertEquals(expected, provider.getList(ConfigKey.ENTRYPOINT, Collections.singletonList("asd")));

        configure(PropertyMode.Fallback);
        Assertions.assertEquals(expected, provider.getList(ConfigKey.ENTRYPOINT, null));
        Assertions.assertEquals(Collections.singletonList("asd"), provider.getList(ConfigKey.ENTRYPOINT, Collections.singletonList("asd")));

        // Override combine policy
        props.put("docker.entrypoint._combine", "merge");

        Assertions.assertEquals(Arrays.asList("asd", "ep1", "ep2"), provider.getList(ConfigKey.ENTRYPOINT, Collections.singletonList("asd")));
    }

    @Test
    void testGetMap() {
        configure(PropertyMode.Only);
        Assertions.assertNull(provider.getMap(ConfigKey.ENV_RUN, null));
        Assertions.assertNull(provider.getMap(ConfigKey.ENV_RUN, ImmutableMap.of("key", "value")));

        props.put("docker.envRun.myprop1", "pvalue1");
        props.put("docker.envRun.myprop2", "pvalue2");

        Map<String, String> m = provider.getMap(ConfigKey.ENV_RUN, null);
        Assertions.assertEquals(2, m.size());
        Assertions.assertEquals("pvalue1", m.get("myprop1"));
        Assertions.assertEquals("pvalue2", m.get("myprop2"));

        m = provider.getMap(ConfigKey.ENV_RUN, ImmutableMap.of("mycfg", "cvalue"));
        Assertions.assertEquals(2, m.size());
        Assertions.assertEquals("pvalue1", m.get("myprop1"));
        Assertions.assertEquals("pvalue2", m.get("myprop2"));

        configure(PropertyMode.Override);

        m = provider.getMap(ConfigKey.ENV_RUN, null);
        Assertions.assertEquals(2, m.size());
        Assertions.assertEquals("pvalue1", m.get("myprop1"));
        Assertions.assertEquals("pvalue2", m.get("myprop2"));

        m = provider.getMap(ConfigKey.ENV_RUN, ImmutableMap.of("ckey", "cvalue", "myprop1", "ignored"));
        Assertions.assertEquals(3, m.size());
        Assertions.assertEquals("pvalue1", m.get("myprop1"));
        Assertions.assertEquals("pvalue2", m.get("myprop2"));
        Assertions.assertEquals("cvalue", m.get("ckey"));

        configure(PropertyMode.Fallback);
        m = provider.getMap(ConfigKey.ENV_RUN, ImmutableMap.of("ckey", "cvalue", "myprop1", "overrides"));
        Assertions.assertEquals(3, m.size());
        Assertions.assertEquals("overrides", m.get("myprop1"));
        Assertions.assertEquals("pvalue2", m.get("myprop2"));
        Assertions.assertEquals("cvalue", m.get("ckey"));

        // Test with another property that does not have CombinePolicy Merge
        props.put("docker.buildOptions.boprop1", "popt1");
        props.put("docker.buildOptions.boprop2", "popt2");
        configure(PropertyMode.Override);
        m = provider.getMap(ConfigKey.BUILD_OPTIONS, null);
        Assertions.assertEquals(2, m.size());
        Assertions.assertEquals("popt1", m.get("boprop1"));
        Assertions.assertEquals("popt2", m.get("boprop2"));

        m = provider.getMap(ConfigKey.BUILD_OPTIONS, ImmutableMap.of("ckey", "ignored", "myprop1", "ignored"));
        Assertions.assertEquals(2, m.size());
        Assertions.assertEquals("popt1", m.get("boprop1"));
        Assertions.assertEquals("popt2", m.get("boprop2"));

        configure(PropertyMode.Fallback);
        m = provider.getMap(ConfigKey.BUILD_OPTIONS, ImmutableMap.of("ckey", "notignored1", "myprop1", "notignored2"));
        Assertions.assertEquals(2, m.size());
        Assertions.assertEquals("notignored1", m.get("ckey"));
        Assertions.assertEquals("notignored2", m.get("myprop1"));

        // Override combine policy
        props.put("docker.buildOptions._combine", "merge");

        m = provider.getMap(ConfigKey.BUILD_OPTIONS, ImmutableMap.of("ckey", "notignored1", "boprop2", "notignored2"));
        Assertions.assertEquals(3, m.size());
        Assertions.assertEquals("popt1", m.get("boprop1"));
        Assertions.assertEquals("notignored2", m.get("boprop2"));
        Assertions.assertEquals("notignored1", m.get("ckey"));
    }

    @Test
    void testSkipTag() {
        configure(PropertyMode.Override);
        Assertions.assertNull(provider.getBoolean(ConfigKey.SKIP_TAG, null));

        props.put("docker.skip.tag", "true");
        Assertions.assertTrue(provider.getBoolean(ConfigKey.SKIP_TAG, null));
        props.put("docker.skip.tag", "false");
        Assertions.assertFalse(provider.getBoolean(ConfigKey.SKIP_TAG, null));
    }
}