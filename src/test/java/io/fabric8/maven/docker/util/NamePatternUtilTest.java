package io.fabric8.maven.docker.util;

import org.junit.Assert;
import org.junit.Test;

public class NamePatternUtilTest {
    @Test
    public void convertNonPatternRepoTagPatterns() {
        Assert.assertEquals("^$", NamePatternUtil.convertNamePattern(""));
        Assert.assertEquals("^a$", NamePatternUtil.convertNamePattern("a"));
        Assert.assertEquals("^hello$", NamePatternUtil.convertNamePattern("hello"));
        Assert.assertEquals("^hello/world$", NamePatternUtil.convertNamePattern("hello/world"));
        Assert.assertEquals("^hello/world:latest$", NamePatternUtil.convertNamePattern("hello/world:latest"));
        Assert.assertEquals("^\\Qregistry.com\\E/hello/world:latest$", NamePatternUtil.convertNamePattern("registry.com/hello/world:latest"));
        Assert.assertEquals("^\\Qregistry.com\\E:8080/hello/world:latest$", NamePatternUtil.convertNamePattern("registry.com:8080/hello/world:latest"));

        Assert.assertEquals("^hello/world:\\Q1.0-SNAPSHOT\\E$", NamePatternUtil.convertNamePattern("hello/world:1.0-SNAPSHOT"));
        Assert.assertEquals("^\\Qh\\E\\\\E\\Qllo\\E/\\Qw\\Qrld\\E:\\Q1.0-SNAPSHOT\\E$", NamePatternUtil.convertNamePattern("h\\Ello/w\\Qrld:1.0-SNAPSHOT"));
        Assert.assertEquals("^\\Qhello! [World] \\E:\\Q not really a tag, right\\E$", NamePatternUtil.convertNamePattern("hello! [World] : not really a tag, right"));
    }

    @Test
    public void convertPatternRepoTagPatterns() {
        Assert.assertEquals("^[^/:]$", NamePatternUtil.convertNamePattern("?"));
        Assert.assertEquals("^[^/:][^/:]$", NamePatternUtil.convertNamePattern("??"));
        Assert.assertEquals("^hello[^/:][^/:]$", NamePatternUtil.convertNamePattern("hello??"));
        Assert.assertEquals("^hello[^/:][^/:]\\Qare you there\\E$", NamePatternUtil.convertNamePattern("hello??are you there"));
        Assert.assertEquals("^[^/:][^/:]whaaat$", NamePatternUtil.convertNamePattern("??whaaat"));

        Assert.assertEquals("^([^/:]|:(?=.*:))*$", NamePatternUtil.convertNamePattern("*"));
        Assert.assertEquals("^my-company/([^/:]|:(?=.*:))*$", NamePatternUtil.convertNamePattern("my-company/*"));
        Assert.assertEquals("^my-co([^/:]|:(?=.*:))*/([^/:]|:(?=.*:))*$", NamePatternUtil.convertNamePattern("my-co*/*"));

        Assert.assertEquals("^([^:]|:(?=.*:))*(?<![^/])my-image:([^/:]|:(?=.*:))*$", NamePatternUtil.convertNamePattern("**/my-image:*"));
        Assert.assertEquals("^([^:]|:(?=.*:))*my-image:([^/:]|:(?=.*:))*$", NamePatternUtil.convertNamePattern("**my-image:*"));
        Assert.assertEquals("^([^:]|:(?=.*:))*my-image:([^:]|:(?=.*:))*$", NamePatternUtil.convertNamePattern("**my-image:**"));
    }

    @Test
    public void convertPrefixedPatternRepoTagPatterns() {
        Assert.assertEquals("^[^/:]$", NamePatternUtil.convertNamePattern("%ant[?]"));
        Assert.assertEquals("^[^/:][^/:]$", NamePatternUtil.convertNamePattern("%ant[??]"));
        Assert.assertEquals("^hello[^/:][^/:]$", NamePatternUtil.convertNamePattern("%ant[hello??]"));
        Assert.assertEquals("^hello[^/:][^/:]\\Qare you there\\E$", NamePatternUtil.convertNamePattern("%ant[hello??are you there]"));
        Assert.assertEquals("^[^/:][^/:]whaaat$", NamePatternUtil.convertNamePattern("%ant[??whaaat]"));

        Assert.assertEquals("^([^/:]|:(?=.*:))*$", NamePatternUtil.convertNamePattern("%ant[*]"));
        Assert.assertEquals("^my-company/([^/:]|:(?=.*:))*$", NamePatternUtil.convertNamePattern("%ant[my-company/*]"));
        Assert.assertEquals("^my-co([^/:]|:(?=.*:))*/([^/:]|:(?=.*:))*$", NamePatternUtil.convertNamePattern("%ant[my-co*/*]"));

        Assert.assertEquals("^([^:]|:(?=.*:))*(?<![^/])my-image:([^/:]|:(?=.*:))*$", NamePatternUtil.convertNamePattern("%ant[**/my-image:*]"));

        // Broken prefixes are ignored
        Assert.assertEquals("^\\Q%ant[\\E[^/:]$", NamePatternUtil.convertNamePattern("%ant[?"));
    }

    @Test
    public void convertRegexRepoTagPatterns() {
        Assert.assertEquals("^[^/:]$", NamePatternUtil.convertNamePattern("%regex[^[^/:]$]"));
        Assert.assertEquals("^[^/:][^/:]$", NamePatternUtil.convertNamePattern("%regex[^[^/:][^/:]$]"));
        Assert.assertEquals("^\\Qhello\\E[^/:][^/:]$", NamePatternUtil.convertNamePattern("%regex[^\\Qhello\\E[^/:][^/:]$]"));
        Assert.assertEquals("^\\Qhello\\E[^/:][^/:]\\Qare you there\\E$", NamePatternUtil.convertNamePattern("%regex[^\\Qhello\\E[^/:][^/:]\\Qare you there\\E$]"));

        Assert.assertEquals("easy literals", NamePatternUtil.convertNamePattern("%regex[easy literals]"));
        Assert.assertEquals("no .* anchors", NamePatternUtil.convertNamePattern("%regex[no .* anchors]"));
        Assert.assertEquals("less \\? fun for v1\\.0", NamePatternUtil.convertNamePattern("%regex[less \\? fun for v1\\.0]"));

        // Broken prefixes don't cause failures
        Assert.assertEquals("^\\Q%regex[^[^\\E/:\\Q]$\\E$", NamePatternUtil.convertNamePattern("%regex[^[^/:]$"));
    }
    
    @Test
    public void convertNamePatternListWithOnePattern() {
        Assert.assertNull(NamePatternUtil.convertNamePatternList(""));
        Assert.assertEquals("^a$", NamePatternUtil.convertNamePatternList("a"));
        Assert.assertEquals("^hello$", NamePatternUtil.convertNamePatternList("hello"));
        Assert.assertEquals("^hello/world$", NamePatternUtil.convertNamePatternList("hello/world"));
        Assert.assertEquals("^hello/world:latest$", NamePatternUtil.convertNamePatternList("hello/world:latest"));
        Assert.assertEquals("^\\Qregistry.com\\E/hello/world:latest$", NamePatternUtil.convertNamePatternList("registry.com/hello/world:latest"));
        Assert.assertEquals("^\\Qregistry.com\\E:8080/hello/world:latest$", NamePatternUtil.convertNamePatternList("registry.com:8080/hello/world:latest"));

        Assert.assertEquals("^hello/world:\\Q1.0-SNAPSHOT\\E$", NamePatternUtil.convertNamePatternList("hello/world:1.0-SNAPSHOT"));
        Assert.assertEquals("^\\Qh\\E\\\\E\\Qllo\\E/\\Qw\\Qrld\\E:\\Q1.0-SNAPSHOT\\E$", NamePatternUtil.convertNamePatternList("h\\Ello/w\\Qrld:1.0-SNAPSHOT"));

        Assert.assertEquals("^[^/:]$", NamePatternUtil.convertNamePatternList("?"));
        Assert.assertEquals("^[^/:][^/:]$", NamePatternUtil.convertNamePatternList("??"));
        Assert.assertEquals("^hello[^/:][^/:]$", NamePatternUtil.convertNamePatternList("hello??"));
        Assert.assertEquals("^hello[^/:][^/:]\\Qare you there\\E$", NamePatternUtil.convertNamePatternList("hello??are you there"));
        Assert.assertEquals("^[^/:][^/:]whaaat$", NamePatternUtil.convertNamePatternList("??whaaat"));

        Assert.assertEquals("^([^/:]|:(?=.*:))*$", NamePatternUtil.convertNamePatternList("*"));
        Assert.assertEquals("^my-company/([^/:]|:(?=.*:))*$", NamePatternUtil.convertNamePatternList("my-company/*"));
        Assert.assertEquals("^my-co([^/:]|:(?=.*:))*/([^/:]|:(?=.*:))*$", NamePatternUtil.convertNamePatternList("my-co*/*"));

        Assert.assertEquals("^([^:]|:(?=.*:))*(?<![^/])my-image:([^/:]|:(?=.*:))*$", NamePatternUtil.convertNamePatternList("**/my-image:*"));
        Assert.assertEquals("^[^/:]$", NamePatternUtil.convertNamePatternList("%regex[^[^/:]$]"));
        Assert.assertEquals("^[^/:][^/:]$", NamePatternUtil.convertNamePatternList("%regex[^[^/:][^/:]$]"));
        Assert.assertEquals("^\\Qhello\\E[^/:][^/:]$", NamePatternUtil.convertNamePatternList("%regex[^\\Qhello\\E[^/:][^/:]$]"));
        Assert.assertEquals("^\\Qhello\\E[^/:][^/:]\\Qare you there\\E$", NamePatternUtil.convertNamePatternList("%regex[^\\Qhello\\E[^/:][^/:]\\Qare you there\\E$]"));

        Assert.assertEquals("easy literals", NamePatternUtil.convertNamePatternList("%regex[easy literals]"));
        Assert.assertEquals("no .* anchors", NamePatternUtil.convertNamePatternList("%regex[no .* anchors]"));
        Assert.assertEquals("less \\? fun for v1\\.0", NamePatternUtil.convertNamePatternList("%regex[less \\? fun for v1\\.0]"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void convertNamePatternListWithOneInvalidPattern() {
        NamePatternUtil.convertNamePatternList("%regex[\\E bogus \\Q]");
    }

    @Test
    public void convertNamePatternListWithMultiplePatterns() {
        Assert.assertNull(NamePatternUtil.convertNamePatternList(","));
        Assert.assertNull(NamePatternUtil.convertNamePatternList(",,"));
        Assert.assertNull(NamePatternUtil.convertNamePatternList(" , , ,, "));
        Assert.assertNull(NamePatternUtil.convertNamePatternList(" , ,%regex[], "));
        Assert.assertEquals("^$", NamePatternUtil.convertNamePatternList(" , ,%ant[], "));
        Assert.assertEquals("(^a$|^b$)", NamePatternUtil.convertNamePatternList("a,b"));
        Assert.assertEquals("(^hello$|^\\Qworld!\\E$)", NamePatternUtil.convertNamePatternList("hello, world!"));
        Assert.assertEquals("(^hello/world$|^foo/bar$|^baz/quux$)", NamePatternUtil.convertNamePatternList("hello/world,foo/bar , baz/quux "));

        Assert.assertEquals("(^a$|^b$)", NamePatternUtil.convertNamePatternList("%ant[a],b"));
        Assert.assertEquals("(^a$|^b$)", NamePatternUtil.convertNamePatternList("%ant[a],%ant[b]"));
        Assert.assertEquals("(^a$|^b$)", NamePatternUtil.convertNamePatternList("a,%ant[b]"));

        Assert.assertEquals("(a|^b$)", NamePatternUtil.convertNamePatternList("%regex[a],b"));
        Assert.assertEquals("(a|b)", NamePatternUtil.convertNamePatternList("%regex[a],%regex[b]"));
        Assert.assertEquals("(^a$|b)", NamePatternUtil.convertNamePatternList("a,%regex[b]"));

        Assert.assertEquals("(a|^b$)", NamePatternUtil.convertNamePatternList("%regex[a], %ant[b]"));
        Assert.assertEquals("(^a$|b)", NamePatternUtil.convertNamePatternList("%ant[a] , %regex[b]"));
        Assert.assertEquals("(^a$|b)", NamePatternUtil.convertNamePatternList(" a , %regex[b]"));

        Assert.assertEquals("(^hello$|^\\Qworld!\\E$)", NamePatternUtil.convertNamePatternList("hello, world!"));
        Assert.assertEquals("(^hello/world$|foo/bar|^baz/quux$)", NamePatternUtil.convertNamePatternList("hello/world, %regex[foo/bar] , baz/quux "));
        Assert.assertEquals("(^hello$|^\\Qworld!\\E$)", NamePatternUtil.convertNamePatternList(",hello, world!,"));
        Assert.assertEquals("(^hello/world$|foo/bar|^baz/quux$)", NamePatternUtil.convertNamePatternList(", , , hello/world , ,,,, %regex[foo/bar] , baz/quux ,"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void convertNamePatternListWithMultiplePatternsOneInvalid() {
        NamePatternUtil.convertNamePatternList("hello, %regex[\\Eworld!\\Q]");
    }

    @Test
    public void convertNamePatternListWithSpecificField() {
        Assert.assertNull(NamePatternUtil.convertNamePatternList("", "name", true));
        Assert.assertNull(NamePatternUtil.convertNamePatternList("", "name", false));
        Assert.assertNull(NamePatternUtil.convertNamePatternList("", null, true));
        Assert.assertNull(NamePatternUtil.convertNamePatternList("", null, false));

        Assert.assertEquals("(^a$|^b$)", NamePatternUtil.convertNamePatternList("a,b", "name", true));
        Assert.assertNull(NamePatternUtil.convertNamePatternList("a,b", "name", false));
        Assert.assertEquals("(^a$|^b$)", NamePatternUtil.convertNamePatternList("a,b", null, true));

        Assert.assertEquals("(^a$|^b$)", NamePatternUtil.convertNamePatternList("a, name= b", "name", true));
        Assert.assertEquals("^b$", NamePatternUtil.convertNamePatternList("a, name=b", "name", false));
        Assert.assertEquals("^a$", NamePatternUtil.convertNamePatternList("a , name = b ", null, true));

        Assert.assertEquals("^b$", NamePatternUtil.convertNamePatternList("image = a, name= b", "name", true));
        Assert.assertEquals("(^b$|c)", NamePatternUtil.convertNamePatternList("image = a, name= b, %regex[c]", "name", true));
        Assert.assertEquals("^b$", NamePatternUtil.convertNamePatternList("image = a, name=b", "name", false));
        Assert.assertNull(NamePatternUtil.convertNamePatternList("image = a , name = b ", null, true));

        Assert.assertEquals("(^a$|^b$)", NamePatternUtil.convertNamePatternList("a, name= %ant[b]", "name", true));
        Assert.assertEquals("^b$", NamePatternUtil.convertNamePatternList("%regex[a], name=b", "name", false));
        Assert.assertEquals("^a$", NamePatternUtil.convertNamePatternList("%ant[a] , name = b ", null, true));

        Assert.assertEquals("^b$", NamePatternUtil.convertNamePatternList("image = %regex[a], name= %ant[b]", "name", true));
        Assert.assertEquals("(b|c)", NamePatternUtil.convertNamePatternList("image = %regex[a], name= %regex[b], %regex[c]", "name", true));
        Assert.assertEquals(" b ", NamePatternUtil.convertNamePatternList("image = a, name=%regex[ b ]", "name", false));
        Assert.assertNull(NamePatternUtil.convertNamePatternList("image = %ant[a] , name = %regex[ b ]", null, true));
    }
}
