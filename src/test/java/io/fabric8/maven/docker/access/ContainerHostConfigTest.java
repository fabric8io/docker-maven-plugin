package io.fabric8.maven.docker.access;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.apache.commons.lang3.tuple.Pair;
import org.json.JSONException;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import io.fabric8.maven.docker.config.LogConfiguration;
import io.fabric8.maven.docker.config.UlimitConfig;
import io.fabric8.maven.docker.util.JsonFactory;

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
    public void testUlimits() throws JSONException {
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
            assertEquals(JsonFactory.newJsonObject((String) data[0]),
                         hc.toJsonObject());
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
            JsonObject result = hc.binds(Arrays.asList(data[i])).toJsonObject();
            JsonObject expected = new JsonObject();
            JsonArray binds = new JsonArray();
            binds.add(data[i+1]);
            expected.add("Binds",binds);
            assertEquals(expected, result);
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
            JsonObject result = hc.tmpfs(Arrays.asList(data[i])).toJsonObject();
            JsonObject expected = JsonFactory.newJsonObject(data[i + 1]);
            assertEquals(expected, result);
        }
    }
    
    @Test
    public void testReadonlyRootfs() throws Exception {
        Pair [] data = {
            Pair.of(Boolean.TRUE, "{ReadonlyRootfs: true}"),
            Pair.of(Boolean.FALSE, "{ReadonlyRootfs: false}")
        };
        for (int i = 0; i < data.length; i++) {
            Pair<Boolean, String> d = data[i];
            ContainerHostConfig hc = new ContainerHostConfig();
            JsonObject result = hc.readonlyRootfs(d.getLeft()).toJsonObject();
            JsonObject expected = JsonFactory.newJsonObject(d.getRight());
            assertEquals(expected, result);
        }
    }
    
    @Test
    public void testAutoRemove() throws Exception {
        Pair [] data = {
            Pair.of(Boolean.TRUE, "{AutoRemove: true}"),
            Pair.of(Boolean.FALSE, "{AutoRemove: false}")
        };
        for (int i = 0; i < data.length; i++) {
            Pair<Boolean, String> d = data[i];
            ContainerHostConfig hc = new ContainerHostConfig();
            JsonObject result = hc.autoRemove(d.getLeft()).toJsonObject();
            JsonObject expected = JsonFactory.newJsonObject(d.getRight());
            assertEquals(expected, result);
        }
    }

    @Test
    public void testLogConfig() {
        ContainerHostConfig hc = new ContainerHostConfig();
        Map<String,String> opts = new HashMap<>();
        opts.put("gelf-address","udp://10.0.0.1:12201");
        opts.put("labels","label1,label2");
        LogConfiguration logConfig = new LogConfiguration.Builder()
            .logDriverName("gelf")
            .logDriverOpts(opts)
            .build();
        hc.logConfig(logConfig);

    // TODO: Does order matter?
    assertEquals(
        "{\"LogConfig\":{\"Type\":\"gelf\",\"Config\":{\"gelf-address\":\"udp://10.0.0.1:12201\",\"labels\":\"label1,label2\"}}}",
        hc.toJson());
    }

}
