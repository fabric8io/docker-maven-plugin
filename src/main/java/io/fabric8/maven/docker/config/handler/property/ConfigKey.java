package io.fabric8.maven.docker.config.handler.property;/*
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
    ARGS,
    ASSEMBLY_BASEDIR("assembly.baseDir"),
    ASSEMBLY_DESCRIPTOR("assembly.descriptor"),
    ASSEMBLY_DESCRIPTOR_REF("assembly.descriptorRef"),
    ASSEMBLY_EXPORT_BASEDIR("assembly.exportBaseDir"),
    ASSEMBLY_IGNORE_PERMISSIONS("assembly.ignorePermissions"),
    ASSEMBLY_PERMISSIONS("assembly.permissions"),
    ASSEMBLY_DOCKER_FILE_DIR("assembly.dockerFileDir"),
    ASSEMBLY_USER("assembly.user"),
    ASSEMBLY_MODE("assembly.mode"),
    ASSEMBLY_TARLONGFILEMODE("assembly.tarLongFileMode"),
    BIND,
    BUILD_OPTIONS,
    CAP_ADD,
    CAP_DROP,
    CLEANUP,
    NOCACHE,
    OPTIMISE,
    CMD,
    DEPENDS_ON,
    DOMAINNAME,
    DNS,
    DNS_SEARCH,
    DOCKER_ARCHIVE,
    DOCKER_FILE,
    DOCKER_FILE_DIR,
    ENTRYPOINT,
    ENV,
    ENV_PROPERTY_FILE,
    EXPOSED_PROPERTY_KEY,
    EXTRA_HOSTS,
    FROM,
    FROM_EXT,
    HEALTHCHECK,
    HEALTHCHECK_MODE("healthcheck.mode"),
    HEALTHCHECK_INTERVAL("healthcheck.interval"),
    HEALTHCHECK_TIMEOUT("healthcheck.timeout"),
    HEALTHCHECK_RETRIES("healthcheck.retries"),
    HEALTHCHECK_CMD("healthcheck.cmd"),
    HOSTNAME,
    LABELS,
    LINKS,
    LOG_ENABLED("log.enabled"),
    LOG_PREFIX("log.prefix"),
    LOG_DATE("log.date"),
    LOG_COLOR("log.color"),
    LOG_DRIVER_NAME("log.driver.name"),
    LOG_DRIVER_OPTS("log.driver.opts"),
    MAINTAINER,
    MEMORY,
    MEMORY_SWAP,
    NAME,
    NAMING_STRATEGY,
    NET,
    NETWORK_MODE("network.mode"),
    NETWORK_NAME("network.name"),
    NETWORK_ALIAS("network.alias"),
    PORT_PROPERTY_FILE,
    PORTS,
    POST_START("wait.exec.postStart"),
    PRE_STOP("wait.exec.preStop"),
    PRIVILEGED,
    REGISTRY,
    RESTART_POLICY_NAME("restartPolicy.name"),
    RESTART_POLICY_RETRY("restartPolicy.retry"),
    RUN,
    SECURITY_OPTS,
    SHMSIZE,
    SKIP_BUILD("skip.build"),
    SKIP_RUN("skip.run"),
    TAGS,
    TMPFS,
    ULIMITS,
    USER,
    VOLUMES,
    VOLUMES_FROM,
    WAIT_LOG("wait.log"),
    WAIT_TIME("wait.time"),
    WAIT_URL("wait.url"),
    WAIT_HTTP_URL("wait.http.url"),
    WAIT_HTTP_METHOD("wait.http.method"),
    WAIT_HTTP_STATUS("wait.http.status"),
    WAIT_KILL("wait.kill"),
    WAIT_SHUTDOWN("wait.shutdown"),
    WAIT_TCP_MODE("wait.tcp.mode"),
    WAIT_TCP_HOST("wait.tcp.host"),
    WAIT_TCP_PORT("wait.tcp.port"),
    WATCH_INTERVAL("watch.interval"),
    WATCH_MODE("watch.mode"),
    WATCH_POSTGOAL("watch.postGoal"),
    WORKDIR,
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
