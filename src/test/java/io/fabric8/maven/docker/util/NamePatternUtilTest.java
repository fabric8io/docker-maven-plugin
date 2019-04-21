package io.fabric8.maven.docker.util;

import org.junit.Assert;
import org.junit.Test;

public class NamePatternUtilTest {
    @Test
    public void convertNonPatternRepoTagPatterns() {
        Assert.assertEquals("^$", NamePatternUtil.convertImageNamePattern(""));
        Assert.assertEquals("^a$", NamePatternUtil.convertImageNamePattern("a"));
        Assert.assertEquals("^hello$", NamePatternUtil.convertImageNamePattern("hello"));
        Assert.assertEquals("^hello/world$", NamePatternUtil.convertImageNamePattern("hello/world"));
        Assert.assertEquals("^hello/world:latest$", NamePatternUtil.convertImageNamePattern("hello/world:latest"));
        Assert.assertEquals("^\\Qregistry.com\\E/hello/world:latest$", NamePatternUtil.convertImageNamePattern("registry.com/hello/world:latest"));
        Assert.assertEquals("^\\Qregistry.com\\E:8080/hello/world:latest$", NamePatternUtil.convertImageNamePattern("registry.com:8080/hello/world:latest"));

        Assert.assertEquals("^hello/world:\\Q1.0-SNAPSHOT\\E$", NamePatternUtil.convertImageNamePattern("hello/world:1.0-SNAPSHOT"));
        Assert.assertEquals("^\\Qh\\E\\\\E\\Qllo\\E/\\Qw\\Qrld\\E:\\Q1.0-SNAPSHOT\\E$", NamePatternUtil.convertImageNamePattern("h\\Ello/w\\Qrld:1.0-SNAPSHOT"));
        Assert.assertEquals("^\\Qhello! [World] \\E:\\Q not really a tag, right\\E$", NamePatternUtil.convertImageNamePattern("hello! [World] : not really a tag, right"));
    }

    @Test
    public void convertPatternRepoTagPatterns() {
        Assert.assertEquals("^[^/:]$", NamePatternUtil.convertImageNamePattern("?"));
        Assert.assertEquals("^[^/:][^/:]$", NamePatternUtil.convertImageNamePattern("??"));
        Assert.assertEquals("^hello[^/:][^/:]$", NamePatternUtil.convertImageNamePattern("hello??"));
        Assert.assertEquals("^hello[^/:][^/:]\\Qare you there\\E$", NamePatternUtil.convertImageNamePattern("hello??are you there"));
        Assert.assertEquals("^[^/:][^/:]whaaat$", NamePatternUtil.convertImageNamePattern("??whaaat"));

        Assert.assertEquals("^([^/:]|:(?=.*:))*$", NamePatternUtil.convertImageNamePattern("*"));
        Assert.assertEquals("^my-company/([^/:]|:(?=.*:))*$", NamePatternUtil.convertImageNamePattern("my-company/*"));
        Assert.assertEquals("^my-co([^/:]|:(?=.*:))*/([^/:]|:(?=.*:))*$", NamePatternUtil.convertImageNamePattern("my-co*/*"));

        Assert.assertEquals("^([^:]|:(?=.*:))*(?<![^/])my-image:([^/:]|:(?=.*:))*$", NamePatternUtil.convertImageNamePattern("**/my-image:*"));
    }

    @Test
    public void convertPrefixedPatternRepoTagPatterns() {
        Assert.assertEquals("^[^/:]$", NamePatternUtil.convertImageNamePattern("%ant[?]"));
        Assert.assertEquals("^[^/:][^/:]$", NamePatternUtil.convertImageNamePattern("%ant[??]"));
        Assert.assertEquals("^hello[^/:][^/:]$", NamePatternUtil.convertImageNamePattern("%ant[hello??]"));
        Assert.assertEquals("^hello[^/:][^/:]\\Qare you there\\E$", NamePatternUtil.convertImageNamePattern("%ant[hello??are you there]"));
        Assert.assertEquals("^[^/:][^/:]whaaat$", NamePatternUtil.convertImageNamePattern("%ant[??whaaat]"));

        Assert.assertEquals("^([^/:]|:(?=.*:))*$", NamePatternUtil.convertImageNamePattern("%ant[*]"));
        Assert.assertEquals("^my-company/([^/:]|:(?=.*:))*$", NamePatternUtil.convertImageNamePattern("%ant[my-company/*]"));
        Assert.assertEquals("^my-co([^/:]|:(?=.*:))*/([^/:]|:(?=.*:))*$", NamePatternUtil.convertImageNamePattern("%ant[my-co*/*]"));

        Assert.assertEquals("^([^:]|:(?=.*:))*(?<![^/])my-image:([^/:]|:(?=.*:))*$", NamePatternUtil.convertImageNamePattern("%ant[**/my-image:*]"));
    }

    @Test
    public void convertRegexRepoTagPatterns() {
        Assert.assertEquals("^[^/:]$", NamePatternUtil.convertImageNamePattern("%regex[^[^/:]$]"));
        Assert.assertEquals("^[^/:][^/:]$", NamePatternUtil.convertImageNamePattern("%regex[^[^/:][^/:]$]"));
        Assert.assertEquals("^\\Qhello\\E[^/:][^/:]$", NamePatternUtil.convertImageNamePattern("%regex[^\\Qhello\\E[^/:][^/:]$]"));
        Assert.assertEquals("^\\Qhello\\E[^/:][^/:]\\Qare you there\\E$", NamePatternUtil.convertImageNamePattern("%regex[^\\Qhello\\E[^/:][^/:]\\Qare you there\\E$]"));

        Assert.assertEquals("easy literals", NamePatternUtil.convertImageNamePattern("%regex[easy literals]"));
        Assert.assertEquals("no .* anchors", NamePatternUtil.convertImageNamePattern("%regex[no .* anchors]"));
        Assert.assertEquals("less \\? fun for v1\\.0", NamePatternUtil.convertImageNamePattern("%regex[less \\? fun for v1\\.0]"));
    }
}
