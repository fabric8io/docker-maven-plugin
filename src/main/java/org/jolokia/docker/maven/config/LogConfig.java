package org.jolokia.docker.maven.config;

import java.util.Map;

/*
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

/**
* @author rikcarve
* @since 10/02/16
*/
public class LogConfig {

    public static final LogConfig DEFAULT = new LogConfig();

    /**
     * @parameter
     */
    private String logDriver;

    /**
     * @parameter
     */
    private Map<String, String> logOpts;

    public LogConfig() {};

    public String getLogDriver() {
        return logDriver;
    }

    public Map<String, String> getLogOpts() {
        return logOpts;
    }

    // ================================================

    public static class Builder {

        private LogConfig policy = new LogConfig();

        public Builder logDriver(String logDriver) {
            policy.logDriver = logDriver;
            return this;
        }

        public Builder logOpts(Map<String, String> logOpts) {
            policy.logOpts = logOpts;
            return this;
        }

        public LogConfig build() {
            return policy;
        }
    }
}
