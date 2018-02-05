package io.fabric8.maven.docker.util;

import java.util.*;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import junitparams.naming.TestCaseName;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

/**
 * @author roland
 * @since 14.10.14
 */
@RunWith(JUnitParamsRunner.class)
public class EnvUtilTest {


    @Test
    public void splitPath() {
        Iterator<String[]> it = EnvUtil.splitOnLastColon(Arrays.asList("db", "postgres:9:db", "postgres:db", "atlast:")).iterator();
        String[][] expected = new String[][] {
                { "db", "db"},
                { "postgres:9","db"},
                { "postgres", "db"},
                { "atlast", ""}
        };
        for (int i = 0; i < expected.length; i++) {
            String[] got = it.next();
            assertEquals(2,got.length);
            assertEquals(expected[i][0],got[0]);
            assertEquals(expected[i][1],got[1]);
        }
        assertFalse(it.hasNext());
    }

    @Test
    public void removeEmptyEntries() {
        assertEquals(ImmutableList.of("ein"),  EnvUtil.removeEmptyEntries(Arrays.asList(null, "", " ein", " ")));
        assertEquals(ImmutableList.of(), EnvUtil.removeEmptyEntries(null));
    }

    @Test
    public void splitAtCommas() {
        Iterable<String> it = EnvUtil.splitAtCommasAndTrim(Arrays.asList("db,postgres:9:db", "postgres:db"));
        Iterable<String> expected = ImmutableList.of ("db", "postgres:9:db","postgres:db");
        assertTrue(Iterables.elementsEqual(it, expected));
    }

    public void assertEmptyList(Iterable<String> actual) {
        assertTrue(Iterables.elementsEqual(Collections.emptyList(), actual));
    }
    @Test
    public void splitAtCommasEmpty() {
        assertEmptyList(EnvUtil.splitAtCommasAndTrim(Collections.<String>emptyList()));
    }

    @Test
    public void splitAtCommasSingleEmpty() {
        assertEmptyList(EnvUtil.splitAtCommasAndTrim(Arrays.asList("")));
    }

    @Test
    public void splitAtCommasNullList() {
        assertEmptyList(EnvUtil.splitAtCommasAndTrim(null));
    }

    // null occurs when <links><link></link></links>
    @Test
    public void splitAtCommasNullInList() {
        assertEmptyList(EnvUtil.splitAtCommasAndTrim(Collections.<String>singletonList(null)));
    }

    @Test
    @TestCaseName("{method}: input \"{0}\" splits to {1}")
    @Parameters
    public void splitOnSpace(String input, String[] expected) {
        String[] result = EnvUtil.splitOnSpaceWithEscape(input);
        assertEquals(expected.length, result.length);
        for (int j = 0; j < expected.length; j++) {
            assertEquals(expected[j],result[j]);
        }
    }

    private Object parametersForSplitOnSpace() {
        return $(
            $("bla blub", new String[] { "bla", "blub"}),
            $("bla\\ blub", new String[] {"bla blub"}),
            $("bla blub\\ blubber", new String[] { "bla", "blub blubber"})
                );
    }

    @Test
    public void extractMapFromProperties() {
        Properties props = getTestProperties(
                "bla.hello","world",
                "bla.max","morlock",
                "blub.not","aMap",
                "bla.empty", "");

        Map<String,String> result = EnvUtil.extractFromPropertiesAsMap("bla", props, true);
        assertEquals(3,result.size());
        assertEquals("world",result.get("hello"));
        assertEquals("morlock",result.get("max"));
        assertEquals("",result.get("empty"));

        result = EnvUtil.extractFromPropertiesAsMap("bla", props, false);
        assertEquals(2,result.size());
        assertEquals("world",result.get("hello"));
        assertEquals("morlock",result.get("max"));
    }

    @Test
    public void extractFromMap() {
        Map<String, String> src = getTestMap(
                "bla.hello","world",
                "bla.max","morlock",
                "blub.not","aMap",
                "bla.empty", "");

        Map<String, String> result = new HashMap<>();
        EnvUtil.extractFromMap("bla", src, true, result);
        assertEquals(3,result.size());
        assertEquals("world",result.get("hello"));
        assertEquals("morlock",result.get("max"));
        assertEquals("",result.get("empty"));

        // test sending to Properties
        Properties resultP = new Properties();
        EnvUtil.extractFromMap("bla", src, false, resultP);
        assertEquals(2,resultP.size());
        assertEquals("world",resultP.get("hello"));
        assertEquals("morlock",resultP.get("max"));
    }

    @Test
    public void extractMavenAndSystemProperties() {
        Properties src = getTestProperties(
                "bla.hello","world",
                "bla.max","morlock",
                "blub.not","aMap",
                "bla.empty", "");

        System.setProperty("bla.sysprop", "someSysProp");
        System.setProperty("bla.emptyprop", "");

        Map<String, String> result = new HashMap<>();
        EnvUtil.extractMavenAndSystemProperties("bla", src, true, result);
        assertEquals(5,result.size());
        assertEquals("world",result.get("hello"));
        assertEquals("morlock",result.get("max"));
        assertEquals("someSysProp",result.get("sysprop"));
        assertEquals("",result.get("emptyprop"));
        assertEquals("",result.get("empty"));

        // test properties alternative, which is also Map
        Properties resultP = new Properties();
        EnvUtil.extractMavenAndSystemProperties("bla", src, false, resultP);
        assertEquals(3,resultP.size());
        assertEquals("world",resultP.get("hello"));
        assertEquals("morlock",resultP.get("max"));
        assertEquals("someSysProp",result.get("sysprop"));
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
    @TestCaseName("{method}: expression {0} => variable {1}")
    @Parameters
    public void mavenPropertyExtract(String expression, String varName) {
        assertEquals(varName,EnvUtil.extractMavenPropertyName(expression));
    }

    private Object parametersForMavenPropertyExtract() {
        return $(
            $("${var1}", "var1"),
            $("${  var2}", "var2"),
            $(" ${ var3}  ", "var3"),
            $("nonvar", null),
            $("${nonvar", null)
                );
    }

    @Test
    @TestCaseName("{method}: max({0},{1}) = {2}, {0} >= {1} ? {3}")
    @Parameters
    public void versionChecks(String versionA, String versionB, String largerVersion, boolean isGreaterOrEquals) {
        assertEquals(largerVersion,EnvUtil.extractLargerVersion(versionA,versionB));
        assertEquals(isGreaterOrEquals, EnvUtil.greaterOrEqualsVersion(versionA,versionB));
    }

    private Object parametersForVersionChecks() {
        return $(
            $(null, null, null, false),
            $("1.10", null, "1.10", true),
            $(null, "1.10", "1.10", false),
            $("1.22", "1.10", "1.22", true),
            $("1.10", "1.25", "1.25", false),
            $("1.23", "1.23", "1.23", true),
            $("1.23.1", "1.23", "1.23.1", true),
            $("1.25", "1.25.1", "1.25.1", false),
            $("1.23.1", "2.0", "2.0", false)
                );
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
    
    @Test
    public void isValidWindowsFileName() {
    	
    	assertFalse(EnvUtil.isValidWindowsFileName("/Dockerfile"));
    	assertTrue(EnvUtil.isValidWindowsFileName("Dockerfile"));
    	assertFalse(EnvUtil.isValidWindowsFileName("Dockerfile/"));
    }    

    private Properties getTestProperties(String ... vals) {
        Properties ret = new Properties();
        for (int i = 0; i < vals.length; i+=2) {
            ret.setProperty(vals[i],vals[i+1]);
        }
        return ret;
    }

    private Map<String, String> getTestMap(String ... vals) {
        Map<String, String> ret = new HashMap();
        for (int i = 0; i < vals.length; i+=2) {
            ret.put(vals[i],vals[i+1]);
        }
        return ret;
    }

    private Object $(Object ... o) { return o; }
}
