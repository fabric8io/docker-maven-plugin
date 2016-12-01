package io.fabric8.maven.docker.access;

import java.util.*;

import io.fabric8.maven.docker.config.LogConfiguration;
import io.fabric8.maven.docker.config.UlimitConfig;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.experimental.theories.DataPoints;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONParser;

import static org.junit.Assert.assertEquals;

public class ContainerHostConfigTest {

    @Test(expected = IllegalArgumentException.class)
    public void testExtraHostsDoesNotResolve() {
        ContainerHostConfig hc = new ContainerHostConfig();
        hc.extraHosts(Arrays.asList("database.pvt:ahostnamewhichreallyshouldnot.exist.zz"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testExtraHostsInvalidFormat() {
        ContainerHostConfig hc = new ContainerHostConfig();
        hc.extraHosts(Arrays.asList("invalidFormat"));
    }

    @Test
    public void testMapExtraHosts() {
        // assumes 'localhost' resolves, which it should
        ContainerHostConfig hc = new ContainerHostConfig();
        hc.extraHosts(Arrays.asList("database.pvt:localhost"));

        assertEquals("{\"ExtraHosts\":[\"database.pvt:127.0.0.1\"]}", hc.toJson());
    }

    @Test
    public void testUlimits() {
        Object data[] = {
            "{Ulimits: [{Name:bla, Hard:2048, Soft: 1024}]}", "bla", 2048, 1024,
            "{Ulimits: [{Name:bla, Soft: 1024}]}", "bla", null, 1024,
            "{Ulimits: [{Name:bla, Hard: 2048}]}", "bla", 2048, null,
            "{Ulimits: [{Name:bla, Hard: 2048}]}", "bla=2048", null, null,
            "{Ulimits: [{Name:bla, Soft: 1024}]}", "bla=:1024", null, null,
            "{Ulimits: [{Name:bla, Hard: 2048, Soft: 1024}]}", "bla=2048:1024", null, null,
            "{Ulimits: [{Name:bla, Hard: 2048}]}", "bla=2048:", null, null
        };

        for (int i = 0; i < data.length; i += 4) {
            ContainerHostConfig hc = new ContainerHostConfig();
            hc.ulimits(Collections.singletonList(
                data[1].toString().contains("=") ?
                    new UlimitConfig((String) data[1]) :
                    new UlimitConfig((String) data[1], (Integer) data[2], (Integer) data[3])));
            JSONAssert.assertEquals((JSONObject) JSONParser.parseJSON((String) data[0]),
                                    (JSONObject) hc.toJsonObject(),
                                    false);
        }
    }

    @Test
    public void testBinds() throws Exception {
        String[] data = {
            "c:\\Users\\roland\\sample:/sample", "/c/Users/roland/sample:/sample",
            "M:\\Users\\roland\\sample:/sample:ro", "/m/Users/roland/sample:/sample:ro"
        };
        for (int i = 0; i < data.length; i+=2) {
            ContainerHostConfig hc = new ContainerHostConfig();
            JSONObject result = (JSONObject) hc.binds(Arrays.asList(data[i])).toJsonObject();
            JSONObject expected = new JSONObject();
            JSONArray binds = new JSONArray();
            binds.put(data[i+1]);
            expected.put("Binds",binds);
            JSONAssert.assertEquals(expected,result,false);
        }
    }

    @Test
    public void testTmpfs() throws Exception {
        String[] data = {
            "/var/lib/mysql", "{Tmpfs: {'/var/lib/mysql': ''}}",
            "/var/lib/mysql:ro", "{Tmpfs: {'/var/lib/mysql': 'ro'}}"
        };
        for (int i = 0; i < data.length; i +=2) {
            ContainerHostConfig hc = new ContainerHostConfig();
            JSONObject result = (JSONObject) hc.tmpfs(Arrays.asList(data[i])).toJsonObject();
            JSONObject expected = (JSONObject) JSONParser.parseJSON(data[i+1]);
            JSONAssert.assertEquals(expected, result, false);
        }
    }

    @Test
    public void testLogConfig() throws Exception {
        ContainerHostConfig hc = new ContainerHostConfig();
        Map<String,String> opts = new HashMap<>();
        opts.put("gelf-address","udp://10.0.0.1:12201");
        opts.put("labels","label1,label2");
        LogConfiguration logConfig = new LogConfiguration.Builder()
            .logDriverName("gelf")
            .logDriverOpts(opts)
            .build();
        hc.logConfig(logConfig);

        JSONAssert.assertEquals("{LogConfig:{Config:{gelf-address:\"udp://10.0.0.1:12201\",labels:\"label1,label2\"},Type:gelf}}", (JSONObject) hc.toJsonObject(), false);
    }

}
