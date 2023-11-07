package io.fabric8.maven.docker.util;

import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Stream;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * @author roland
 * @since 14.10.14
 */
class EnvUtilTest {

    @Test
    void splitPath() {
        Iterator<String[]> it = EnvUtil.splitOnLastColon(Arrays.asList("db", "postgres:9:db", "postgres:db", "atlast:")).iterator();
        String[][] expected = new String[][] {
            { "db", "db" },
            { "postgres:9", "db" },
            { "postgres", "db" },
            { "atlast", "" }
        };
        for (String[] strings : expected) {
            String[] got = it.next();
            Assertions.assertEquals(2, got.length);
            Assertions.assertEquals(strings[0], got[0]);
            Assertions.assertEquals(strings[1], got[1]);
        }
        Assertions.assertFalse(it.hasNext());
    }

    @Test
    void removeEmptyEntries() {
        Assertions.assertEquals(ImmutableList.of("ein"), EnvUtil.removeEmptyEntries(Arrays.asList(null, "", " ein", " ")));
        Assertions.assertEquals(ImmutableList.of(), EnvUtil.removeEmptyEntries(null));
    }

    @Test
    void splitAtCommas() {
        Iterable<String> it = EnvUtil.splitAtCommasAndTrim(Arrays.asList("db,postgres:9:db", "postgres:db"));
        Iterable<String> expected = ImmutableList.of("db", "postgres:9:db", "postgres:db");
        Assertions.assertTrue(Iterables.elementsEqual(it, expected));
    }

    void assertEmptyList(Iterable<String> actual) {
        Assertions.assertTrue(Iterables.elementsEqual(Collections.emptyList(), actual));
    }

    @Test
    void splitAtCommasEmpty() {
        assertEmptyList(EnvUtil.splitAtCommasAndTrim(Collections.emptyList()));
    }

    @Test
    void splitAtCommasSingleEmpty() {
        assertEmptyList(EnvUtil.splitAtCommasAndTrim(Collections.singletonList("")));
    }

    @Test
    void splitAtCommasNullList() {
        assertEmptyList(EnvUtil.splitAtCommasAndTrim(null));
    }

    // null occurs when <links><link></link></links>
    @Test
    void splitAtCommasNullInList() {
        assertEmptyList(EnvUtil.splitAtCommasAndTrim(Collections.singletonList(null)));
    }

    @ParameterizedTest(name = "{displayName}: input \"{0}\" splits to {1}")
    @MethodSource("parametersForSplitOnSpace")
    void splitOnSpace(String input, String[] expected) {
        String[] result = EnvUtil.splitOnSpaceWithEscape(input);
        Assertions.assertEquals(expected.length, result.length);
        for (int j = 0; j < expected.length; j++) {
            Assertions.assertEquals(expected[j], result[j]);
        }
    }

    private static Stream<Arguments> parametersForSplitOnSpace() {
        return Stream.of(
            Arguments.of("bla blub", new String[] { "bla", "blub" }),
            Arguments.of("bla\\ blub", new String[] { "bla blub" }),
            Arguments.of("bla blub\\ blubber", new String[] { "bla", "blub blubber" })
        );
    }

    @Test
    void extractMapFromProperties() {
        Properties props = getTestProperties(
            "bla.hello", "world",
            "bla.max", "morlock",
            "bla." + EnvUtil.PROPERTY_COMBINE_POLICY_SUFFIX, "ignored-since-it-is-reserved",
            "blub.not", "aMap");
        Map<String, String> result = EnvUtil.extractFromPropertiesAsMap("bla", props);
        Assertions.assertEquals(2, result.size());
        Assertions.assertEquals("world", result.get("hello"));
        Assertions.assertEquals("morlock", result.get("max"));
    }

    @Test
    void extractListFromProperties() {
        Properties props = getTestProperties(
            "bla.2", "world",
            "bla.1", "hello",
            "bla.blub", "last",
            "bla." + EnvUtil.PROPERTY_COMBINE_POLICY_SUFFIX, "ignored-since-it-is-reserved",
            "blub.1", "unknown");
        List<String> result = EnvUtil.extractFromPropertiesAsList("bla", props);
        Assertions.assertEquals(Arrays.asList("hello", "world", "last"), result);
    }

    @Test
    void extractListOfPropertiesFromProperties() {
        final Properties testProperties = getTestProperties(
            "bla.0001.what", "bye",
            "bla.1.who", "java",
            "bla.2.what", "hello",
            "bla.2.who", "world",
            "bla.3", "no dot at the end",
            "bla.4.", "ends with dot",
            "bla.blub", "empty",
            "bla.blub.when.date", "late",
            "blub.1", "unknown",
            "bla." + EnvUtil.PROPERTY_COMBINE_POLICY_SUFFIX, "ignored-since-it-is-reserved");
        final Properties[] expectedListOfProperties = {
            getTestProperties(
                "who", "java",
                "what", "bye"),
            getTestProperties(
                "who", "world",
                "what", "hello"),
            getTestProperties(
                "", "no dot at the end"),
            getTestProperties(
                "", "ends with dot"),
            getTestProperties(
                "", "empty",
                "when.date", "late") };

        List<Properties> result = EnvUtil.extractFromPropertiesAsListOfProperties("bla", testProperties);

        Assertions.assertNotNull(result);
        Assertions.assertEquals(5, result.size());
        Assertions.assertArrayEquals(expectedListOfProperties, result.toArray());
    }

    @ParameterizedTest(name = "{displayName}: expression {0} => variable {1}")
    @MethodSource("parametersForMavenPropertyExtract")
    void mavenPropertyExtract(String expression, String varName) {
        Assertions.assertEquals(varName, EnvUtil.extractMavenPropertyName(expression));
    }

    private static Stream<Arguments> parametersForMavenPropertyExtract() {
        return Stream.of(
            Arguments.of("${var1}", "var1"),
            Arguments.of("${  var2}", "var2"),
            Arguments.of(" ${ var3}  ", "var3"),
            Arguments.of("nonvar", null),
            Arguments.of("${nonvar", null)
        );
    }

    @ParameterizedTest(name = "{displayName}: max({0},{1}) = {2}, {0} >= {1} ? {3}")
    @MethodSource("parametersForVersionChecks")
    void versionChecks(String versionA, String versionB, String largerVersion, boolean isGreaterOrEquals) {
        Assertions.assertEquals(largerVersion, EnvUtil.extractLargerVersion(versionA, versionB));
        Assertions.assertEquals(isGreaterOrEquals, EnvUtil.greaterOrEqualsVersion(versionA, versionB));
    }

    private static Stream<Arguments> parametersForVersionChecks() {
        return Stream.of(
            Arguments.of(null, null, null, false),
            Arguments.of("1.10", null, "1.10", true),
            Arguments.of(null, "1.10", "1.10", false),
            Arguments.of("1.22", "1.10", "1.22", true),
            Arguments.of("1.10", "1.25", "1.25", false),
            Arguments.of("1.23", "1.23", "1.23", true),
            Arguments.of("1.23.1", "1.23", "1.23.1", true),
            Arguments.of("1.25", "1.25.1", "1.25.1", false),
            Arguments.of("1.23.1", "2.0", "2.0", false)
        );
    }

    private Properties getTestProperties(String... vals) {
        Properties ret = new Properties();
        for (int i = 0; i < vals.length; i += 2) {
            ret.setProperty(vals[i], vals[i + 1]);
        }
        return ret;
    }

    @ParameterizedTest
    @CsvSource({
        "https://index.docker.io/v1/, https://index.docker.io/v1/",
        "index.docker.io/v1/, https://index.docker.io/v1/",
        "http://index.docker.io/v1/, http://index.docker.io/v1/",
        "registry.fuse-ignite.openshift.com, https://registry.fuse-ignite.openshift.com"

    })
    void ensureRegistryHttpUrl(String input, String expected) {
        Assertions.assertEquals( expected, EnvUtil.ensureRegistryHttpUrl(input), ">> " + input);
    }

    @Test
    void resolveHomeReferenceNoHome() {
        Assertions.assertEquals("~chas/relative", EnvUtil.resolveHomeReference("~chas/relative"));
    }

    @Test
    void resolveHomeReference() {
        Assertions.assertEquals(Paths.get(EnvUtil.getUserHome(), "relative").toString(), EnvUtil.resolveHomeReference("~/relative"));
    }
}
