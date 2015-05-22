package org.jolokia.docker.maven.util;

import java.io.*;
import java.util.*;

import org.apache.maven.plugin.MojoExecutionException;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author roland
 * @since 14.10.14
 */
public class EnvUtilTest {


    @Test
    public void splitPath() {
        List<String[]> res = EnvUtil.splitOnLastColon(Arrays.asList("db", "postgres:9:db", "postgres:db"));
        assertEquals(3,res.size());
        String[][] expected = new String[][] {
                { "db", "db"},
                { "postgres:9","db"},
                { "postgres", "db"}
        };
        for (int i = 0; i < res.size(); i++) {
            String[] got = res.get(i);
            assertEquals(2,got.length);
            assertEquals(expected[i][0],got[0]);
            assertEquals(expected[i][1],got[1]);
        }
    }

    @Test
    public void splitOnSpace() {
        Object[] data = new Object[] {
                "bla blub", new String[] { "bla", "blub"},
                "bla\\ blub", new String[] {"bla blub"},
                "bla blub\\ blubber", new String[] { "bla", "blub blubber"}
        };
        for (int i = 0; i < data.length; i += 2) {
            String[] result = EnvUtil.splitOnSpaceWithEscape((String) data[i]);
            String[] expected = (String[]) data[i+1];
            assertEquals(expected.length, result.length);
            for (int j = 0; j < expected.length; j++) {
                assertEquals(expected[j],result[j]);
            }
        }
    }

    @Test
    public void writePortProperties() throws IOException, MojoExecutionException {
        File propFile = File.createTempFile("dmpl-",".properties");
        propFile.deleteOnExit();

        Properties origProps = new Properties();
        origProps.setProperty("test1","bla");
        origProps.setProperty("test2","blub");
        EnvUtil.writePortProperties(origProps,propFile.getAbsolutePath());
        assertTrue(propFile.exists());

        Properties newProps = new Properties();
        newProps.load(new FileInputStream(propFile));

        assertEquals(2,newProps.size());
        assertEquals(newProps.get("test1"),"bla");
        assertEquals(newProps.get("test2"),"blub");
    }

    @Test
    public void extractMapFromProperties() {
        Properties props = getTestProperties(
                "bla.hello","world",
                "bla.max","morlock",
                "blub.not","aMap");
        Map<String,String> result = EnvUtil.extractFromPropertiesAsMap("bla", props);
        assertEquals(2,result.size());
        assertEquals("world",result.get("hello"));
        assertEquals("morlock",result.get("max"));
    }

    @Test
    public void extractListFromProperties() {
        Properties props = getTestProperties(
                "bla.2","world",
                "bla.1","hello",
                "bla.blub","last",
                "blub.1","unknown");
        List<String> result = EnvUtil.extractFromPropertiesAsList("bla",props);
        assertEquals(3,result.size());
        assertArrayEquals(new String[] {"hello","world","last"}, new ArrayList(result).toArray());
    }

    private Properties getTestProperties(String ... vals) {
        Properties ret = new Properties();
        for (int i = 0; i < vals.length; i+=2) {
            ret.setProperty(vals[i],vals[i+1]);
        }
        return ret;
    }

}
