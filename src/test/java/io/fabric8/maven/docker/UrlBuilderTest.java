package io.fabric8.maven.docker;
/*
 * 
 * Copyright 2016 Roland Huss
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.UnsupportedEncodingException;
import java.net.*;

import io.fabric8.maven.docker.access.UrlBuilder;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author roland
 * @since 13/07/16
 */
public class UrlBuilderTest {

    @Test
    public void listContainers() throws MalformedURLException, UnsupportedEncodingException {
        UrlBuilder builder = new UrlBuilder("","1.0");

        assertEquals("/1.0/containers/json",builder.listContainers());
        assertEquals("/1.0/containers/json?filters=" + URLEncoder.encode("{\"ancestor\":[\"nginx\"]}","UTF8"),
                     builder.listContainers("ancestor", "nginx"));

        try {
            builder.listContainers("ancestor");
            fail();
        } catch (IllegalArgumentException exp) {
            assertTrue(exp.getMessage().contains("pair"));
        }
    }
}
