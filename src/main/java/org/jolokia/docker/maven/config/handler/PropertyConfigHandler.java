package org.jolokia.docker.maven.config.handler;/*
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

import static org.jolokia.docker.maven.util.EnvUtil.extractFromPropertiesAsList;
import static org.jolokia.docker.maven.util.EnvUtil.extractFromPropertiesAsMap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.jolokia.docker.maven.config.BuildImageConfiguration;
import org.jolokia.docker.maven.config.ImageConfiguration;
import org.jolokia.docker.maven.config.RunImageConfiguration;
import org.jolokia.docker.maven.config.RunImageConfiguration.RestartPolicy;
import org.jolokia.docker.maven.config.WaitConfiguration;
import org.jolokia.docker.maven.util.EnvUtil;

/**
 * @author roland
 * @since 18/11/14
 */
public class PropertyConfigHandler implements ExternalConfigHandler {
    
    static final String ALIAS = "alias";
    static final String ASSEMBLY_DESCRIPTOR = "assemblyDescriptor";
    static final String ASSEMBLY_DESCRIPTOR_REF = "assemblyDescriptorRef";
    static final String BIND = "bind";
    static final String CAP_ADD = "capAdd";
    static final String CAP_DROP = "capDrop";
    static final String COMMAND = "command";
    static final String DOCKER = "docker";
    static final String DOMAINNAME = "domainname";
    static final String DNS = "dns";
    static final String DNS_SEARCH = "dnsSearch";
    static final String ENTRYPOINT = "entrypoint";
    static final String ENV = "env";
    static final String EXPORT_DIR = "exportDir";
    static final String EXTRA_HOSTS = "extraHosts";
    static final String FROM = "from";
    static final String HOSTNAME = "hostname";
    static final String LINKS = "links";
    static final String MEMORY = "memory";
    static final String MEMORY_SWAP = "memorySwap";    
    static final String NAME = "name";
    static final String PREFIX = "prefix";
    static final String PORT_PROP_FILE = "portPropertyFile";
    static final String PORTS = "ports";
    static final String PRIVILEGED = "privileged";
    static final String PROPS = "props";
    static final String REGISTRY = "registry";
    static final String RESTART_POLICY_NAME = "restartPolicy.name";
    static final String RESTART_POLICY_RETRY = "restartPolicy.retry";
    static final String USER = "user";
    static final String VOLUMES_FROM = "volumesFrom";
    static final String WAIT_LOG = "wait.log";
    static final String WAIT_TIME = "wait.time";
    static final String WAIT_URL = "wait.url";
    static final String WORKING_DIR = "workingDir";
    
    @Override
    public String getType() {
        return PROPS;
    }

    @Override
    public List<ImageConfiguration> resolve(ImageConfiguration config, Properties properties) throws IllegalArgumentException {
        String prefix = getPrefix(config);
        
        RunImageConfiguration run = extractRunConfiguration(prefix,properties);
        BuildImageConfiguration build = extractBuildConfiguration(prefix,properties);

        String name = withPrefix(prefix, NAME, properties);
        if (name == null) {
            throw new IllegalArgumentException(String.format("Mandatory property [%s] is not defined", NAME));
        }
        String alias = withPrefix(prefix, ALIAS, properties);

        return Collections.singletonList(
                new ImageConfiguration.Builder()
                        .name(name)
                        .alias(alias != null ? alias : config.getAlias())
                        .runConfig(run)
                        .buildConfig(build)
                        .build());
    }

    private BuildImageConfiguration extractBuildConfiguration(String prefix, Properties properties) {
        return new BuildImageConfiguration.Builder()
                .command(withPrefix(prefix, COMMAND, properties))
                .assemblyDescriptor(withPrefix(prefix, ASSEMBLY_DESCRIPTOR, properties))
                .assemblyDescriptorRef(withPrefix(prefix, ASSEMBLY_DESCRIPTOR_REF, properties))
                .env(mapWithPrefix(prefix, ENV, properties))
                .ports(extractPortValues(prefix, properties))
                .from(withPrefix(prefix, FROM, properties))
                .exportDir(withPrefix(prefix, EXPORT_DIR, properties))
                .registry(withPrefix(prefix, REGISTRY, properties))
                .build();
    }

    private RunImageConfiguration extractRunConfiguration(String prefix, Properties properties) {
        return new RunImageConfiguration.Builder()
                .bind(listWithPrefix(prefix, BIND, properties))
                .capAdd(listWithPrefix(prefix, CAP_ADD, properties))
                .capDrop(listWithPrefix(prefix, CAP_DROP, properties))
                .command(withPrefix(prefix, COMMAND, properties))
                .dns(listWithPrefix(prefix, DNS, properties))
                .dnsSearch(listWithPrefix(prefix, DNS_SEARCH, properties))
                .domainname(withPrefix(prefix, DOMAINNAME, properties))
                .entrypoint(withPrefix(prefix, ENTRYPOINT, properties))
                .env(mapWithPrefix(prefix, ENV, properties))
                .extraHosts(listWithPrefix(prefix, EXTRA_HOSTS, properties))
                .hostname(withPrefix(prefix, HOSTNAME, properties))
                .links(listWithPrefix(prefix, LINKS, properties))
                .memory(Long.valueOf(withPrefix(prefix, MEMORY, properties)))
                .memorySwap(Long.valueOf(withPrefix(prefix, MEMORY_SWAP, properties)))
                .portPropertyFile(withPrefix(prefix, PORT_PROP_FILE, properties))
                .ports(listWithPrefix(prefix, PORTS, properties))
                .privileged(Boolean.valueOf(withPrefix(prefix, PRIVILEGED, properties)))
                .restartPolicy(extractRestartPolicy(prefix, properties))
                .user(withPrefix(prefix, USER, properties))
                .volumes(listWithPrefix(prefix, VOLUMES_FROM, properties))
                .workingDir(withPrefix(prefix, WORKING_DIR, properties))
                .wait(extractWaitConfig(prefix,properties))
                .build();
    }

    // Extract only the values of the port mapping
    private List<String> extractPortValues(String prefix, Properties properties) {
        List<String> ret = new ArrayList<>();
        List<String> ports = listWithPrefix(prefix, PORTS, properties);
        if (ports == null) {
            return null;
        }
        List<String[]> parsedPorts = EnvUtil.splitOnLastColon(ports);
        for (String[] port : parsedPorts) {
            ret.add(port[1]);
        }
        return ret;
    }

    private RestartPolicy extractRestartPolicy(String prefix, Properties properties) {
        String name = withPrefix(prefix, RESTART_POLICY_NAME, properties);
        int retry = asInt(withPrefix(prefix, RESTART_POLICY_RETRY, properties));
        return new RestartPolicy(name, retry);
    }

    private WaitConfiguration extractWaitConfig(String prefix, Properties properties) {
        return new WaitConfiguration.Builder()
                .time(asInt(withPrefix(prefix, WAIT_TIME,properties)))
                .url(withPrefix(prefix,WAIT_URL,properties))
                .log(withPrefix(prefix,WAIT_LOG,properties))
                .build();
    }
    
    private int asInt(String s) {
        return s != null ? Integer.parseInt(s) : 0;
    }

    private String getKey(String prefix, String key) {
        return prefix + "." + key;
    }
    
    private List<String> listWithPrefix(String prefix, String key, Properties properties) {
        return extractFromPropertiesAsList(getKey(prefix, key), properties);
    }
   
    private Map<String, String> mapWithPrefix(String prefix, String key, Properties properties) {
        return extractFromPropertiesAsMap(getKey(prefix, key), properties);
    }
        
    private String withPrefix(String prefix, String key, Properties properties) {
        return properties.getProperty(getKey(prefix, key));
    }
    
    private String getPrefix(ImageConfiguration config) {
        Map<String, String> refConfig = config.getExternalConfig();
        String prefix = refConfig != null ? refConfig.get(PREFIX) : null;
        if (prefix == null) {
            prefix = DOCKER;
        }
        return prefix;
    }
}
