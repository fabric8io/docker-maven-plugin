package io.fabric8.maven.docker.access.util;/*
 * 
 * Copyright 2014 Roland Huss
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
import java.net.URLEncoder;
import java.nio.charset.Charset;

import org.apache.http.client.methods.*;
import org.apache.http.entity.StringEntity;

/**
 * @author roland
 * @since 30/11/14
 */
public class RequestUtil {

    public static final String HEADER_ACCEPT = "Accept";
    public static final String HEADER_ACCEPT_ALL = "*/*";

    // -----------------------
    // Request related methods
    public static HttpUriRequest newGet(String url) {
        return addDefaultHeaders(new HttpGet(url));
    }

    public static HttpUriRequest newPost(String url, String body) {
        HttpPost post = new HttpPost(url);
        if (body != null) {
            post.setEntity(new StringEntity(body, Charset.defaultCharset()));
        }
        return addDefaultHeaders(post);
    }

    public static HttpUriRequest newDelete(String url) {
        return addDefaultHeaders(new HttpDelete(url));
    }

    public static HttpUriRequest addDefaultHeaders(HttpUriRequest req) {
        req.addHeader(HEADER_ACCEPT, HEADER_ACCEPT_ALL);
        req.addHeader("Content-Type", "application/json");
        return req;
    }

    @SuppressWarnings("deprecation")
    public static String encode(String param) {
        try {
            return URLEncoder.encode(param, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            // wont happen
            return URLEncoder.encode(param);
        }
    }

}
