package org.jolokia.docker.maven.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ImageNameTest {

    @Test
    public void simple() {

        String[] data = {
                "jolokia/jolokia_demo",                     null, "jolokia/jolokia_demo", null,
                "jolokia/jolokia_demo:0.9.6",               null, "jolokia/jolokia_demo", "0.9.6",
                "test.org/jolokia/jolokia_demo:0.9.6",  "test.org", "jolokia/jolokia_demo", "0.9.6",
                "test.org/jolokia/jolokia_demo",        "test.org", "jolokia/jolokia_demo", null,
                "test.org:8000/jolokia/jolokia_demo:8.0",        "test.org:8000", "jolokia/jolokia_demo", "8.0",
                "jolokia_demo",                             null, "jolokia_demo", null,
                "jolokia_demo:0.9.6",                       null, "jolokia_demo", "0.9.6",
                "consol/tomcat-8.0:8.0.9",                  null,"consol/tomcat-8.0","8.0.9"
        };

        for (int i = 0; i < data.length; i += 4) {
            ImageName name = new ImageName(data[i]);
            assertEquals("" + i,data[i+1],name.getRegistry());
            assertEquals("" + i,data[i+2],name.getRepository());
            assertEquals("" + i,data[i+3],name.getTag());
        }

    }

}