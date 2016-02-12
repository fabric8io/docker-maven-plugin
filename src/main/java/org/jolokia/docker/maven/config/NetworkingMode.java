package org.jolokia.docker.maven.config;
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

/**
 * Helper class for handling network mode and custom networks
 *
 * @author roland
 * @since 12/02/16
 */
public class NetworkingMode {

    private enum Mode {
        NONE(true),
        BRIDGE(true),
        HOST(true),
        CONTAINER(true),
        CUSTOM(false);

        private final boolean isStandard;

        Mode(boolean isStandard) {
            this.isStandard = isStandard;
        }

        public boolean isStandard() {
            return isStandard;
        }

        public static Mode extractMode(String mode) {
            if (mode != null && mode.length() > 0) {
                try {
                    return Mode.valueOf(mode.toUpperCase());
                } catch (IllegalArgumentException exp) { /* could be a custom mode, too */ }
                if (mode.toUpperCase().startsWith(CONTAINER.name() + ":")) {
                    return CONTAINER;
                } else {
                    return CUSTOM;
                }
            }
            return null;
        }

         private String extractContainerAlias(String alias) {
            // Will always succeed if mode has been created with 'extractMode'
            return alias.substring(name().length() + 1);
        }
    }

    private final Mode networkMode;
    private final String name;

    public NetworkingMode(String mode) {
        if (mode != null) {
            this.networkMode = Mode.extractMode(mode);
            if (this.networkMode == Mode.CONTAINER) {
                this.name = networkMode.extractContainerAlias(mode);
            } else if (this.networkMode == Mode.CUSTOM) {
                this.name = mode;
            } else {
                this.name = null;
            }
        } else {
            this.networkMode = null;
            this.name = null;
        }
    }

    public boolean isStandardMode() {
        return networkMode != null && networkMode.isStandard();
    }

    public boolean isCustomNetwork() {
        return networkMode == Mode.CUSTOM;
    }

    public String getContainerAlias() {
        return networkMode == Mode.CONTAINER ? name : null;
    }

    public String getCustomNetwork() {
        return networkMode == Mode.CUSTOM ? name : null;
    }

    public String getStandardMode(String containerId) {
        if (networkMode == Mode.CUSTOM) {
            throw new IllegalArgumentException("Custom network for network '" + name +
                                               "' can not be used as standard mode");
        }
        return networkMode.name().toLowerCase() + (networkMode == Mode.CONTAINER ? ":" + containerId : "");
    }
}
