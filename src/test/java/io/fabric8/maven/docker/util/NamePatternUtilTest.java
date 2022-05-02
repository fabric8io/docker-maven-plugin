package io.fabric8.maven.docker.util;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class NamePatternUtilTest {
    @Test
    void convertNonPatternRepoTagPatterns() {
        Assertions.assertEquals("^$", NamePatternUtil.convertNamePattern(""));
        Assertions.assertEquals("^a$", NamePatternUtil.convertNamePattern("a"));
        Assertions.assertEquals("^hello$", NamePatternUtil.convertNamePattern("hello"));
        Assertions.assertEquals("^hello/world$", NamePatternUtil.convertNamePattern("hello/world"));
        Assertions.assertEquals("^hello/world:latest$", NamePatternUtil.convertNamePattern("hello/world:latest"));
        Assertions.assertEquals("^\\Qregistry.com\\E/hello/world:latest$", NamePatternUtil.convertNamePattern("registry.com/hello/world:latest"));
        Assertions.assertEquals("^\\Qregistry.com\\E:8080/hello/world:latest$", NamePatternUtil.convertNamePattern("registry.com:8080/hello/world:latest"));

        Assertions.assertEquals("^hello/world:\\Q1.0-SNAPSHOT\\E$", NamePatternUtil.convertNamePattern("hello/world:1.0-SNAPSHOT"));
        Assertions.assertEquals("^\\Qh\\E\\\\E\\Qllo\\E/\\Qw\\Qrld\\E:\\Q1.0-SNAPSHOT\\E$", NamePatternUtil.convertNamePattern("h\\Ello/w\\Qrld:1.0-SNAPSHOT"));
        Assertions.assertEquals("^\\Qhello! [World] \\E:\\Q not really a tag, right\\E$", NamePatternUtil.convertNamePattern("hello! [World] : not really a tag, right"));
    }

    @Test
    void convertPatternRepoTagPatterns() {
        Assertions.assertEquals("^[^/:]$", NamePatternUtil.convertNamePattern("?"));
        Assertions.assertEquals("^[^/:][^/:]$", NamePatternUtil.convertNamePattern("??"));
        Assertions.assertEquals("^hello[^/:][^/:]$", NamePatternUtil.convertNamePattern("hello??"));
        Assertions.assertEquals("^hello[^/:][^/:]\\Qare you there\\E$", NamePatternUtil.convertNamePattern("hello??are you there"));
        Assertions.assertEquals("^[^/:][^/:]whaaat$", NamePatternUtil.convertNamePattern("??whaaat"));

        Assertions.assertEquals("^([^/:]|:(?=.*:))*$", NamePatternUtil.convertNamePattern("*"));
        Assertions.assertEquals("^my-company/([^/:]|:(?=.*:))*$", NamePatternUtil.convertNamePattern("my-company/*"));
        Assertions.assertEquals("^my-co([^/:]|:(?=.*:))*/([^/:]|:(?=.*:))*$", NamePatternUtil.convertNamePattern("my-co*/*"));

        Assertions.assertEquals("^([^:]|:(?=.*:))*(?<![^/])my-image:([^/:]|:(?=.*:))*$", NamePatternUtil.convertNamePattern("**/my-image:*"));
        Assertions.assertEquals("^([^:]|:(?=.*:))*my-image:([^/:]|:(?=.*:))*$", NamePatternUtil.convertNamePattern("**my-image:*"));
        Assertions.assertEquals("^([^:]|:(?=.*:))*my-image:([^:]|:(?=.*:))*$", NamePatternUtil.convertNamePattern("**my-image:**"));
    }

    @Test
    void convertPrefixedPatternRepoTagPatterns() {
        Assertions.assertEquals("^[^/:]$", NamePatternUtil.convertNamePattern("%ant[?]"));
        Assertions.assertEquals("^[^/:][^/:]$", NamePatternUtil.convertNamePattern("%ant[??]"));
        Assertions.assertEquals("^hello[^/:][^/:]$", NamePatternUtil.convertNamePattern("%ant[hello??]"));
        Assertions.assertEquals("^hello[^/:][^/:]\\Qare you there\\E$", NamePatternUtil.convertNamePattern("%ant[hello??are you there]"));
        Assertions.assertEquals("^[^/:][^/:]whaaat$", NamePatternUtil.convertNamePattern("%ant[??whaaat]"));

        Assertions.assertEquals("^([^/:]|:(?=.*:))*$", NamePatternUtil.convertNamePattern("%ant[*]"));
        Assertions.assertEquals("^my-company/([^/:]|:(?=.*:))*$", NamePatternUtil.convertNamePattern("%ant[my-company/*]"));
        Assertions.assertEquals("^my-co([^/:]|:(?=.*:))*/([^/:]|:(?=.*:))*$", NamePatternUtil.convertNamePattern("%ant[my-co*/*]"));

        Assertions.assertEquals("^([^:]|:(?=.*:))*(?<![^/])my-image:([^/:]|:(?=.*:))*$", NamePatternUtil.convertNamePattern("%ant[**/my-image:*]"));

        // Broken prefixes are ignored
        Assertions.assertEquals("^\\Q%ant[\\E[^/:]$", NamePatternUtil.convertNamePattern("%ant[?"));
    }

    @Test
    void convertRegexRepoTagPatterns() {
        Assertions.assertEquals("^[^/:]$", NamePatternUtil.convertNamePattern("%regex[^[^/:]$]"));
        Assertions.assertEquals("^[^/:][^/:]$", NamePatternUtil.convertNamePattern("%regex[^[^/:][^/:]$]"));
        Assertions.assertEquals("^\\Qhello\\E[^/:][^/:]$", NamePatternUtil.convertNamePattern("%regex[^\\Qhello\\E[^/:][^/:]$]"));
        Assertions.assertEquals("^\\Qhello\\E[^/:][^/:]\\Qare you there\\E$", NamePatternUtil.convertNamePattern("%regex[^\\Qhello\\E[^/:][^/:]\\Qare you there\\E$]"));

        Assertions.assertEquals("easy literals", NamePatternUtil.convertNamePattern("%regex[easy literals]"));
        Assertions.assertEquals("no .* anchors", NamePatternUtil.convertNamePattern("%regex[no .* anchors]"));
        Assertions.assertEquals("less \\? fun for v1\\.0", NamePatternUtil.convertNamePattern("%regex[less \\? fun for v1\\.0]"));

        // Broken prefixes don't cause failures
        Assertions.assertEquals("^\\Q%regex[^[^\\E/:\\Q]$\\E$", NamePatternUtil.convertNamePattern("%regex[^[^/:]$"));
    }

    @Test
    void convertNamePatternListWithOnePattern() {
        Assertions.assertNull(NamePatternUtil.convertNamePatternList(""));
        Assertions.assertEquals("^a$", NamePatternUtil.convertNamePatternList("a"));
        Assertions.assertEquals("^hello$", NamePatternUtil.convertNamePatternList("hello"));
        Assertions.assertEquals("^hello/world$", NamePatternUtil.convertNamePatternList("hello/world"));
        Assertions.assertEquals("^hello/world:latest$", NamePatternUtil.convertNamePatternList("hello/world:latest"));
        Assertions.assertEquals("^\\Qregistry.com\\E/hello/world:latest$", NamePatternUtil.convertNamePatternList("registry.com/hello/world:latest"));
        Assertions.assertEquals("^\\Qregistry.com\\E:8080/hello/world:latest$", NamePatternUtil.convertNamePatternList("registry.com:8080/hello/world:latest"));

        Assertions.assertEquals("^hello/world:\\Q1.0-SNAPSHOT\\E$", NamePatternUtil.convertNamePatternList("hello/world:1.0-SNAPSHOT"));
        Assertions.assertEquals("^\\Qh\\E\\\\E\\Qllo\\E/\\Qw\\Qrld\\E:\\Q1.0-SNAPSHOT\\E$", NamePatternUtil.convertNamePatternList("h\\Ello/w\\Qrld:1.0-SNAPSHOT"));

        Assertions.assertEquals("^[^/:]$", NamePatternUtil.convertNamePatternList("?"));
        Assertions.assertEquals("^[^/:][^/:]$", NamePatternUtil.convertNamePatternList("??"));
        Assertions.assertEquals("^hello[^/:][^/:]$", NamePatternUtil.convertNamePatternList("hello??"));
        Assertions.assertEquals("^hello[^/:][^/:]\\Qare you there\\E$", NamePatternUtil.convertNamePatternList("hello??are you there"));
        Assertions.assertEquals("^[^/:][^/:]whaaat$", NamePatternUtil.convertNamePatternList("??whaaat"));

        Assertions.assertEquals("^([^/:]|:(?=.*:))*$", NamePatternUtil.convertNamePatternList("*"));
        Assertions.assertEquals("^my-company/([^/:]|:(?=.*:))*$", NamePatternUtil.convertNamePatternList("my-company/*"));
        Assertions.assertEquals("^my-co([^/:]|:(?=.*:))*/([^/:]|:(?=.*:))*$", NamePatternUtil.convertNamePatternList("my-co*/*"));

        Assertions.assertEquals("^([^:]|:(?=.*:))*(?<![^/])my-image:([^/:]|:(?=.*:))*$", NamePatternUtil.convertNamePatternList("**/my-image:*"));
        Assertions.assertEquals("^[^/:]$", NamePatternUtil.convertNamePatternList("%regex[^[^/:]$]"));
        Assertions.assertEquals("^[^/:][^/:]$", NamePatternUtil.convertNamePatternList("%regex[^[^/:][^/:]$]"));
        Assertions.assertEquals("^\\Qhello\\E[^/:][^/:]$", NamePatternUtil.convertNamePatternList("%regex[^\\Qhello\\E[^/:][^/:]$]"));
        Assertions.assertEquals("^\\Qhello\\E[^/:][^/:]\\Qare you there\\E$", NamePatternUtil.convertNamePatternList("%regex[^\\Qhello\\E[^/:][^/:]\\Qare you there\\E$]"));

        Assertions.assertEquals("easy literals", NamePatternUtil.convertNamePatternList("%regex[easy literals]"));
        Assertions.assertEquals("no .* anchors", NamePatternUtil.convertNamePatternList("%regex[no .* anchors]"));
        Assertions.assertEquals("less \\? fun for v1\\.0", NamePatternUtil.convertNamePatternList("%regex[less \\? fun for v1\\.0]"));
    }

    @Test
    void convertNamePatternListWithOneInvalidPattern() {
        Assertions.assertThrows(IllegalArgumentException.class, () ->
            NamePatternUtil.convertNamePatternList("%regex[\\E bogus \\Q]"));
    }

    @Test
    void convertNamePatternListWithMultiplePatterns() {
        Assertions.assertNull(NamePatternUtil.convertNamePatternList(","));
        Assertions.assertNull(NamePatternUtil.convertNamePatternList(",,"));
        Assertions.assertNull(NamePatternUtil.convertNamePatternList(" , , ,, "));
        Assertions.assertNull(NamePatternUtil.convertNamePatternList(" , ,%regex[], "));
        Assertions.assertEquals("^$", NamePatternUtil.convertNamePatternList(" , ,%ant[], "));
        Assertions.assertEquals("(^a$|^b$)", NamePatternUtil.convertNamePatternList("a,b"));
        Assertions.assertEquals("(^hello$|^\\Qworld!\\E$)", NamePatternUtil.convertNamePatternList("hello, world!"));
        Assertions.assertEquals("(^hello/world$|^foo/bar$|^baz/quux$)", NamePatternUtil.convertNamePatternList("hello/world,foo/bar , baz/quux "));

        Assertions.assertEquals("(^a$|^b$)", NamePatternUtil.convertNamePatternList("%ant[a],b"));
        Assertions.assertEquals("(^a$|^b$)", NamePatternUtil.convertNamePatternList("%ant[a],%ant[b]"));
        Assertions.assertEquals("(^a$|^b$)", NamePatternUtil.convertNamePatternList("a,%ant[b]"));

        Assertions.assertEquals("(a|^b$)", NamePatternUtil.convertNamePatternList("%regex[a],b"));
        Assertions.assertEquals("(a|b)", NamePatternUtil.convertNamePatternList("%regex[a],%regex[b]"));
        Assertions.assertEquals("(^a$|b)", NamePatternUtil.convertNamePatternList("a,%regex[b]"));

        Assertions.assertEquals("(a|^b$)", NamePatternUtil.convertNamePatternList("%regex[a], %ant[b]"));
        Assertions.assertEquals("(^a$|b)", NamePatternUtil.convertNamePatternList("%ant[a] , %regex[b]"));
        Assertions.assertEquals("(^a$|b)", NamePatternUtil.convertNamePatternList(" a , %regex[b]"));

        Assertions.assertEquals("(^hello$|^\\Qworld!\\E$)", NamePatternUtil.convertNamePatternList("hello, world!"));
        Assertions.assertEquals("(^hello/world$|foo/bar|^baz/quux$)", NamePatternUtil.convertNamePatternList("hello/world, %regex[foo/bar] , baz/quux "));
        Assertions.assertEquals("(^hello$|^\\Qworld!\\E$)", NamePatternUtil.convertNamePatternList(",hello, world!,"));
        Assertions.assertEquals("(^hello/world$|foo/bar|^baz/quux$)", NamePatternUtil.convertNamePatternList(", , , hello/world , ,,,, %regex[foo/bar] , baz/quux ,"));
    }

    @Test
    void convertNamePatternListWithMultiplePatternsOneInvalid() {
        Assertions.assertThrows(IllegalArgumentException.class, () ->
            NamePatternUtil.convertNamePatternList("hello, %regex[\\Eworld!\\Q]"));
    }

    @Test
    void convertNamePatternListWithSpecificField() {
        Assertions.assertNull(NamePatternUtil.convertNamePatternList("", "name", true));
        Assertions.assertNull(NamePatternUtil.convertNamePatternList("", "name", false));
        Assertions.assertNull(NamePatternUtil.convertNamePatternList("", null, true));
        Assertions.assertNull(NamePatternUtil.convertNamePatternList("", null, false));

        Assertions.assertEquals("(^a$|^b$)", NamePatternUtil.convertNamePatternList("a,b", "name", true));
        Assertions.assertNull(NamePatternUtil.convertNamePatternList("a,b", "name", false));
        Assertions.assertEquals("(^a$|^b$)", NamePatternUtil.convertNamePatternList("a,b", null, true));

        Assertions.assertEquals("(^a$|^b$)", NamePatternUtil.convertNamePatternList("a, name= b", "name", true));
        Assertions.assertEquals("^b$", NamePatternUtil.convertNamePatternList("a, name=b", "name", false));
        Assertions.assertEquals("^a$", NamePatternUtil.convertNamePatternList("a , name = b ", null, true));

        Assertions.assertEquals("^b$", NamePatternUtil.convertNamePatternList("image = a, name= b", "name", true));
        Assertions.assertEquals("(^b$|c)", NamePatternUtil.convertNamePatternList("image = a, name= b, %regex[c]", "name", true));
        Assertions.assertEquals("^b$", NamePatternUtil.convertNamePatternList("image = a, name=b", "name", false));
        Assertions.assertNull(NamePatternUtil.convertNamePatternList("image = a , name = b ", null, true));

        Assertions.assertEquals("(^a$|^b$)", NamePatternUtil.convertNamePatternList("a, name= %ant[b]", "name", true));
        Assertions.assertEquals("^b$", NamePatternUtil.convertNamePatternList("%regex[a], name=b", "name", false));
        Assertions.assertEquals("^a$", NamePatternUtil.convertNamePatternList("%ant[a] , name = b ", null, true));

        Assertions.assertEquals("^b$", NamePatternUtil.convertNamePatternList("image = %regex[a], name= %ant[b]", "name", true));
        Assertions.assertEquals("(b|c)", NamePatternUtil.convertNamePatternList("image = %regex[a], name= %regex[b], %regex[c]", "name", true));
        Assertions.assertEquals(" b ", NamePatternUtil.convertNamePatternList("image = a, name=%regex[ b ]", "name", false));
        Assertions.assertNull(NamePatternUtil.convertNamePatternList("image = %ant[a] , name = %regex[ b ]", null, true));
    }
}
