package io.fabric8.maven.docker.model;
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
 * Interface representing a container
 *
 * @author roland
 * @since 16/07/15
 */
public interface Container {

    long getCreated();

    String getId();

    String getImage();

    Map<String, String> getLabels();

    String getName();

    String getNetworkMode();

    Map<String, PortBinding> getPortBindings();

    boolean isRunning();

    /**
     * IP Adress of the container if provided
     *
     * @return the IP address of the container or <code>null</code> if not provided.
     */
    String getIPAddress();

    /**
     * Return IP Addresses of custom networks, mapped to the network name as the key.
     * @return The mapping of network names to IP addresses, or null it none provided.
     */
    Map<String, String> getCustomNetworkIpAddresses();

    /**
     * Exit code of the container if it already has excited
     *
     * @return exit code if the container has excited, <code>null</code> if it is still running. Also null,
     * if the implementation doesn't support an exit code.
     */
    Integer getExitCode();

    class PortBinding {
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
