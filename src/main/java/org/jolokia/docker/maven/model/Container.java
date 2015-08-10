package org.jolokia.docker.maven.model;
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

import java.util.Map;

/**
 * @author roland
 * @since 16/07/15
 */
public interface Container {

    static final String CREATED = "Created";
    static final String ID = "Id";
    static final String IMAGE = "Image";
    static final String PORTS = "Ports";
    static final String SLASH = "/";

    long getCreated();

    String getId();

    String getImage();

    String getName();

    Map<String, PortBinding> getPortBindings();

    boolean isRunning();

    public static class PortBinding {
        private final String hostIp;
        private final Integer hostPort;

        public PortBinding(Integer hostPort, String hostIp) {
            this.hostPort = hostPort;
            this.hostIp = hostIp;
        }

        public String getHostIp() {
            return hostIp;
        }

        public Integer getHostPort() {
            return hostPort;
        }
    }
}
