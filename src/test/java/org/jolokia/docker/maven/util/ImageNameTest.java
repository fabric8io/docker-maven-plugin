package org.jolokia.docker.maven.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ImageNameTest {

    @Test
    public void simple() {

        Object[] data = {
                "jolokia/jolokia_demo",
                r().repository("jolokia/jolokia_demo")
                   .fullName("jolokia/jolokia_demo").fullNameWithTag("jolokia/jolokia_demo:latest"),

                "jolokia/jolokia_demo:0.9.6",
                r().repository("jolokia/jolokia_demo").tag("0.9.6")
                   .fullName("jolokia/jolokia_demo").fullNameWithTag("jolokia/jolokia_demo:0.9.6"),

                "test.org/jolokia/jolokia_demo:0.9.6",
                r().registry("test.org").repository("jolokia/jolokia_demo").tag("0.9.6")
                   .fullName("test.org/jolokia/jolokia_demo").fullNameWithTag("test.org/jolokia/jolokia_demo:0.9.6"),

                "test.org/jolokia/jolokia_demo",
                r().registry("test.org").repository("jolokia/jolokia_demo")
                   .fullName("test.org/jolokia/jolokia_demo").fullNameWithTag("test.org/jolokia/jolokia_demo:latest"),

                "test.org:8000/jolokia/jolokia_demo:8.0",
                r().registry("test.org:8000").repository("jolokia/jolokia_demo").tag("8.0")
                   .fullName("test.org:8000/jolokia/jolokia_demo").fullNameWithTag("test.org:8000/jolokia/jolokia_demo:8.0"),

                "jolokia_demo",
                r().repository("jolokia_demo")
                   .fullName("jolokia_demo").fullNameWithTag("jolokia_demo:latest"),

                "jolokia_demo:0.9.6",
                r().repository("jolokia_demo").tag("0.9.6")
                   .fullName("jolokia_demo").fullNameWithTag("jolokia_demo:0.9.6"),

                "consol/tomcat-8.0:8.0.9",
                r().repository("consol/tomcat-8.0").tag("8.0.9")
                   .fullName("consol/tomcat-8.0").fullNameWithTag("consol/tomcat-8.0:8.0.9")
        };

        for (int i = 0; i < data.length; i += 2) {
            ImageName name = new ImageName((String) data[i]);
            Res res = (Res) data[i+1];
            assertEquals("Registry " + i,res.registry,name.getRegistry());
            assertEquals("Repository " + i,res.repository,name.getRepository());
            assertEquals("Tag " + i,res.tag,name.getTag());
            assertEquals("RepoWithRegistry " + i,res.fullName, name.getFullName(null));
            assertEquals("FullName " + i,res.fullNameWithTag,name.getFullNameWithTag(null));
        }
    }

    @Test
    public void testRegistryNaming() throws Exception {
        assertEquals("docker.jolokia.org/jolokia/jolokia_demo:0.18",
                     new ImageName("jolokia/jolokia_demo:0.18").getFullNameWithTag("docker.jolokia.org"));
        assertEquals("docker.jolokia.org/jolokia/jolokia_demo:latest",
                     new ImageName("jolokia/jolokia_demo").getFullNameWithTag("docker.jolokia.org"));
        assertEquals("jolokia/jolokia_demo:latest",
                     new ImageName("jolokia/jolokia_demo").getFullNameWithTag(null));
        assertEquals("docker.jolokia.org/jolokia/jolokia_demo:latest",
                     new ImageName("docker.jolokia.org/jolokia/jolokia_demo").getFullNameWithTag("another.registry.org"));
        assertEquals("docker.jolokia.org/jolokia/jolokia_demo:latest",
                     new ImageName("docker.jolokia.org/jolokia/jolokia_demo").getFullNameWithTag(null));
    }


    @Test(expected = IllegalArgumentException.class)
    public void testIllegalFormat() throws Exception {
        new ImageName("");
    }

    // =======================================================================================
    private static Res r() {
        return new Res();
    }

    private static class Res {
        private String registry,repository,tag, fullName, fullNameWithTag;
        boolean hasRegistry = false;

        Res registry(String registry) {
            this.registry = registry;
            this.hasRegistry = registry != null;
            return this;
        }

        Res repository(String repository) {
            this.repository = repository;
            return this;
        }

        Res tag(String tag) {
            this.tag = tag;
            return this;
        }

        Res fullName(String fullName) {
            this.fullName = fullName;
            return this;
        }

        Res fullNameWithTag(String fullNameWithTag) {
            this.fullNameWithTag = fullNameWithTag;
            return this;
        }
    }
}