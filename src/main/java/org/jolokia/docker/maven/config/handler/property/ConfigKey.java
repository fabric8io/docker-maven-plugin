package org.jolokia.docker.maven.config.handler.property;/*
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
 * Enum holding possible configuration keys
 *
 * @author roland
 * @since 07/12/14
 */
public enum ConfigKey {

    ALIAS,
    ASSEMBLY_BASEDIR,
    ASSEMBLY_DESCRIPTOR("assembly.descriptor"),
    ASSEMBLY_DESCRIPTOR_REF("assembly.descriptorRef"),
    ASSEMBLY_DRY_RUN("assembly.dryRun"),
    ASSEMBLY_EXPORT_BASEDIR("assembly.exportBase"),
    ASSEMBLY_IGNORE_PERMISSIONS("assembly.ignorePermissions"),
    ASSEMBLY_USER("assembly.user"),
    BIND,
    CAP_ADD,
    CAP_DROP,
    COMMAND,
    DOCKER_FILE_DIR,
    DOMAINNAME,
    DNS,
    DNS_SEARCH,
    ENTRYPOINT,
    ENV,
    EXTRA_HOSTS,
    FROM,
    HOSTNAME,
    LINKS,
    MEMORY,
    MEMORY_SWAP,
    NAME,
    PORT_PROPERTY_FILE,
    PORTS,
    PRIVILEGED,
    REGISTRY,
    RESTART_POLICY_NAME("restartPolicy.name"),
    RESTART_POLICY_RETRY("restartPolicy.retry"),
    USER,
    VOLUMES,
    VOLUMES_FROM,
    WAIT_LOG("wait.log"),
    WAIT_TIME("wait.time"),
    WAIT_URL("wait.url"),
    WAIT_SHUTDOWN("wait.shutdown"),
    WORKING_DIR;

    ConfigKey() {
        this.key = toVarName(name());
    }

    ConfigKey(String key) {
        this.key = key;
    }

    private String key;

    public static String DEFAULT_PREFIX = "docker";

    // Convert to camle case
    private String toVarName(String s) {
        String[] parts = s.split("_");
        String var = parts[0].toLowerCase();
        for (int i = 1; i < parts.length; i++) {
            var = var + parts[i].substring(0, 1).toUpperCase() +
                  parts[i].substring(1).toLowerCase();
        }
        return var;
    }

    public String asPropertyKey(String prefix) {
        return prefix + "." + key;
    }

    public String asPropertyKey() {
        return DEFAULT_PREFIX + "." + key;
    }
}