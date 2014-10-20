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
        List<String[]> res = EnvUtil.splitLinks(Arrays.asList("db", "postgres:9:db", "postgres:db"));
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
            String[] result = EnvUtil.splitWOnSpaceWithEscape((String) data[i]);
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


}
