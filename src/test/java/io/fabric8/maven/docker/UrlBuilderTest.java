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
import java.util.HashMap;

import io.fabric8.maven.docker.access.UrlBuilder;
import io.fabric8.maven.docker.util.ImageName;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author roland
 * @since 13/07/16
 */
public class UrlBuilderTest {

    @Test
    public void buildImage() throws URISyntaxException {
        UrlBuilder builder = new UrlBuilder("","1.0");
        assertEquals(new URI("/1.0/build?dockerfile=df&nocache=0&rm=1&t=image1"),
            new URI(builder.buildImage("image1", "df", false, false, null)));
        assertEquals(new URI("/1.0/build?dockerfile=df&forcerm=1&nocache=1&t=image1"),
            new URI(builder.buildImage("image1", "df", true, true, null)));
        HashMap<String, String> m = new HashMap<>();
        m.put("k1", "v1");
        m.put("k2", "v2");
        assertEquals("/1.0/build?buildargs=%7B%22k1%22%3A%22v1%22%2C%22k2%22%3A%22v2%22%7D&dockerfile=df&nocache=0&rm=1&t=image1",
            builder.buildImage("image1", "df", false, false, m));
    }

    @Test
    public void copyArchive() throws URISyntaxException {
        UrlBuilder builder = new UrlBuilder("","1.0");
        assertEquals(new URI("/1.0/containers/cid/archive?path=tp"), new URI(builder.copyArchive("cid", "tp")));

    }

    @Test
    public void containerLogs() throws URISyntaxException {
        UrlBuilder builder = new UrlBuilder("","1.0");
        assertEquals(new URI("/1.0/containers/cid/logs?follow=0&stderr=1&stdout=1&timestamps=1"),
                     new URI(builder.containerLogs("cid", false)));

    }

    @Test
    public void deleteImage() throws URISyntaxException {
        UrlBuilder builder = new UrlBuilder("","1.0");
        assertEquals(new URI("/1.0/images/n1?force=0"), new URI(builder.deleteImage("n1", false)));

    }

    @Test
    public void listContainers() throws MalformedURLException, UnsupportedEncodingException, URISyntaxException {
        UrlBuilder builder = new UrlBuilder("","1.0");

        assertEquals(new URI("/1.0/containers/json"), new URI(builder.listContainers()));
        assertEquals(new URI("/1.0/containers/json?filters=" + URLEncoder.encode("{\"ancestor\":[\"nginx\"]}","UTF8")),
                     new URI(builder.listContainers("ancestor", "nginx")));

        try {
            builder.listContainers("ancestor");
            fail();
        } catch (IllegalArgumentException exp) {
            assertTrue(exp.getMessage().contains("pair"));
        }
    }

    @Test
    public void loadImage() throws URISyntaxException {
        UrlBuilder builder = new UrlBuilder("", "1.0");
        assertEquals(new URI("/1.0/images/load"),new URI(builder.loadImage()));
    }

    @Test
    public void pullImage() throws URISyntaxException {
        UrlBuilder builder = new UrlBuilder("", "1.0");
        assertEquals(new URI("/1.0/images/create?fromImage=reg%2Ft1&tag=latest"),
                     new URI(builder.pullImage(new ImageName("t1:latest"), "reg")));
        assertEquals(new URI("/1.0/images/create?fromImage=reg%2Ft1"),
                     new URI(builder.pullImage(new ImageName("t1"), "reg")));
    }

    @Test
    public void tagContainer() throws URISyntaxException {
        UrlBuilder builder = new UrlBuilder("", "1.0");
        assertEquals(new URI("/1.0/images/t1%3Alatest/tag?force=1&repo=new&tag=tag1"),
                     new URI(builder.tagContainer(new ImageName("t1:latest"), new ImageName("new:tag1"), true)));

    }
}
