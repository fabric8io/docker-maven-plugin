package org.jolokia.docker.maven.config;

import org.apache.maven.plugins.annotations.Parameter;

/**
 * @author roland
 * @since 12.10.14
 */
public class WaitConfiguration {

    @Parameter
    int time;

    @Parameter
    String http;

    public int getTime() {
        return time;
    }

    public String getHttp() {
        return http;
    }
}
