package io.fabric8.maven.docker.util;

import java.util.*;

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
        List<String> result = EnvUtil.extractFromPropertiesAsList("bla", props);
        assertEquals(3,result.size());
        assertArrayEquals(new String[]{"hello", "world", "last"}, new ArrayList(result).toArray());
    }

    @Test
    public void mavenPropertyExtract() {
        String[] data = {
                "${var1}", "var1",
                "${  var2}", "var2",
                " ${ var3}  ", "var3",
                "nonvar", null,
                "${nonvar", null
        };
        for (int i = 0; i < data.length; i +=2) {
            assertEquals(data[i+1],EnvUtil.extractMavenPropertyName(data[i]));
        }

    }

    @Test
    public void versionChecks() {
        Object[] data = {
            null, null, null, false,
            "1.10", null, "1.10", true,
            null, "1.10", "1.10", false,
            "1.22", "1.10", "1.22", true,
            "1.10", "1.25", "1.25", false,
            "1.23", "1.23", "1.23", true,
            "1.23.1", "1.23", "1.23.1", true,
            "1.25", "1.25.1", "1.25.1", false,
            "1.23.1", "2.0", "2.0", false
        };
        for (int i = 0; i < data.length; i+=4) {
            assertEquals(data[i+2],EnvUtil.extractLargerVersion((String) data[i],(String) data[i+1]));
            assertEquals(data[i+3],EnvUtil.greaterOrEqualsVersion((String) data[i],(String) data[i+1]));
        }
    }

    @Test
    public void fixupPath() throws Exception {
        String[] data = new String[] {
            "my/regular/path", "my/regular/path",
            "c:\\windows\\path", "/c/windows/path",
            "Z:\\yet another\\path", "/z/yet another/path"
        };

        for (int i = 0; i < data.length; i+=2) {
            assertEquals(data[i+1], EnvUtil.fixupPath(data[i]));
        }


    }

    private Properties getTestProperties(String ... vals) {
        Properties ret = new Properties();
        for (int i = 0; i < vals.length; i+=2) {
            ret.setProperty(vals[i],vals[i+1]);
        }
        return ret;
    }




}
