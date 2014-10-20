package org.jolokia.docker.maven.config;

import org.apache.maven.plugins.annotations.Parameter;

/**
 * @author roland
 * @since 12.10.14
 */
public class WaitConfiguration {

    @Parameter
    private int time;

    @Parameter
    private String url;

    @Parameter
    private String log;

    public int getTime() {
        return time;
    }

    public String getUrl() {
        return url;
    }

    public String getLog() {
        return log;
    }
}
