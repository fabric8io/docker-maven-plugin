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
                "bla."+EnvUtil.PROPERTY_COMBINE_POLICY_SUFFIX, "ignored-since-it-is-reserved",
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
                "bla."+EnvUtil.PROPERTY_COMBINE_POLICY_SUFFIX, "ignored-since-it-is-reserved",
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

    private Properties getTestProperties(String ... vals) {
        Properties ret = new Properties();
        for (int i = 0; i < vals.length; i+=2) {
            ret.setProperty(vals[i],vals[i+1]);
        }
        return ret;
    }

    private Object $(Object ... o) { return o; }

    @Test
    public void ensureRegistryHttpUrl() {
        String[] data = {
            "https://index.docker.io/v1/", "https://index.docker.io/v1/",
            "index.docker.io/v1/", "https://index.docker.io/v1/",
            "http://index.docker.io/v1/", "http://index.docker.io/v1/",
            "registry.fuse-ignite.openshift.com", "https://registry.fuse-ignite.openshift.com"
        };

        for (int i = 0; i < data.length; i +=2) {
            assertEquals(">> " + data[i], data[i+1], EnvUtil.ensureRegistryHttpUrl(data[i]));
        }
    }
}
