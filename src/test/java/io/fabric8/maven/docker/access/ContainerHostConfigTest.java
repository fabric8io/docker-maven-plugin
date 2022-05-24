package io.fabric8.maven.docker.access;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.fabric8.maven.docker.config.LogConfiguration;
import io.fabric8.maven.docker.config.UlimitConfig;
import io.fabric8.maven.docker.util.JsonFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class ContainerHostConfigTest {

    @Test
    void testExtraHostsDoesNotResolve() {
        ContainerHostConfig hc = new ContainerHostConfig();
        List<String> extraHosts = Collections.singletonList("database.pvt:ahostnamewhichreallyshouldnot.exist.zz");
        Assertions.assertThrows(IllegalArgumentException.class, () -> hc.extraHosts(extraHosts));
    }

    @Test
    void testExtraHostsInvalidFormat() {
        ContainerHostConfig hc = new ContainerHostConfig();
        List<String> extraHosts = Collections.singletonList("invalidFormat");
        Assertions.assertThrows(IllegalArgumentException.class, () -> hc.extraHosts(extraHosts));
    }

    @Test
    void testMapExtraHosts() {
        // assumes 'localhost' resolves, which it should
        ContainerHostConfig hc = new ContainerHostConfig();
        hc.extraHosts(Collections.singletonList("database.pvt:localhost"));

        Assertions.assertEquals("{\"ExtraHosts\":[\"database.pvt:127.0.0.1\"]}", hc.toJson());
    }

    @Test
    void testUlimits() {
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
            Assertions.assertEquals(JsonFactory.newJsonObject((String) data[0]),
                         hc.toJsonObject());
        }
    }

    @Test
    void testBinds() throws Exception {
        String[] data = {
            "c:\\Users\\roland\\sample:/sample", "/c/Users/roland/sample:/sample",
            "M:\\Users\\roland\\sample:/sample:ro", "/m/Users/roland/sample:/sample:ro"
        };
        for (int i = 0; i < data.length; i+=2) {
            ContainerHostConfig hc = new ContainerHostConfig();
            JsonObject result = hc.binds(Collections.singletonList(data[i])).toJsonObject();
            JsonObject expected = new JsonObject();
            JsonArray binds = new JsonArray();
            binds.add(data[i+1]);
            expected.add("Binds",binds);
            Assertions.assertEquals(expected, result);
        }
    }

    @Test
    void testTmpfs() {
        String[] data = {
            "/var/lib/mysql", "{Tmpfs: {'/var/lib/mysql': ''}}",
            "/var/lib/mysql:ro", "{Tmpfs: {'/var/lib/mysql': 'ro'}}"
        };
        for (int i = 0; i < data.length; i +=2) {
            ContainerHostConfig hc = new ContainerHostConfig();
            JsonObject result = hc.tmpfs(Collections.singletonList(data[i])).toJsonObject();
            JsonObject expected = JsonFactory.newJsonObject(data[i + 1]);
            Assertions.assertEquals(expected, result);
        }
    }

    @ParameterizedTest
    @CsvSource({
        "true, '{ReadonlyRootfs: true}'",
        "false, '{ReadonlyRootfs: false}'"
    })
    void testReadonlyRootfs(boolean parameter, String expected) {
            ContainerHostConfig hc = new ContainerHostConfig();
            JsonObject result = hc.readonlyRootfs(parameter).toJsonObject();
            Assertions.assertEquals(JsonFactory.newJsonObject(expected), result);
    }

    @ParameterizedTest
    @CsvSource({
        "true, '{AutoRemove: true}'",
        "false, '{AutoRemove: false}'"
    })
    void testAutoRemove(boolean parameter, String expected) {
            ContainerHostConfig hc = new ContainerHostConfig();
            JsonObject result = hc.autoRemove(parameter).toJsonObject();
        Assertions.assertEquals(JsonFactory.newJsonObject(expected), result);
    }

    @Test
    void testLogConfig() {
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
        Assertions.assertEquals(
        "{\"LogConfig\":{\"Type\":\"gelf\",\"Config\":{\"gelf-address\":\"udp://10.0.0.1:12201\",\"labels\":\"label1,label2\"}}}",
        hc.toJson());
    }

}
