package io.fabric8.maven.docker.access;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import io.fabric8.maven.docker.config.LogConfiguration;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONParser;

import static org.junit.Assert.assertEquals;

public class ContainerHostConfigTest {

    @Test(expected = IllegalArgumentException.class)
    public void testExtraHostsDoesNotResolve() {
        ContainerHostConfig hc = new ContainerHostConfig();
        hc.extraHosts(Arrays.asList("database.pvt:a.b.pvt"));
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
    public void testBinds() throws Exception {
        ContainerHostConfig hc = new ContainerHostConfig();
        JSONObject result = (JSONObject) hc.binds(Arrays.asList("c:\\Users\\roland\\sample:/sample")).toJsonObject();
        JSONObject expected = new JSONObject();
        JSONArray binds = new JSONArray();
        binds.put("/c/Users/roland/sample:/sample");
        expected.put("Binds",binds);
        JSONAssert.assertEquals(expected,result,false);
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
