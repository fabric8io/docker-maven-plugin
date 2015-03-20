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

import java.util.*;

import org.jolokia.docker.maven.config.*;
import org.jolokia.docker.maven.config.RestartPolicy;
import org.jolokia.docker.maven.config.handler.ExternalConfigHandler;
import org.jolokia.docker.maven.util.EnvUtil;

import static org.jolokia.docker.maven.config.handler.property.ConfigKey.*;

import static org.jolokia.docker.maven.util.EnvUtil.*;

/**
 * @author roland
 * @since 18/11/14
 */
public class PropertyConfigHandler implements ExternalConfigHandler {

    @Override
    public String getType() {
        return "props";
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
                .assembly(extractAssembly(prefix, properties))
                .env(mapWithPrefix(prefix, ENV, properties))
                .ports(extractPortValues(prefix, properties))
                .from(withPrefix(prefix, FROM, properties))
                .registry(withPrefix(prefix, REGISTRY, properties))
                .volumes(listWithPrefix(prefix, VOLUMES, properties))
                .tags(listWithPrefix(prefix, TAGS, properties))
                .maintainer(withPrefix(prefix, MAINTAINER, properties))
                .build();
    }

    private RunImageConfiguration extractRunConfiguration(String prefix, Properties properties) {

        return new RunImageConfiguration.Builder()
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
                .memory(longWithPrefix(prefix, MEMORY, properties))
                .memorySwap(longWithPrefix(prefix, MEMORY_SWAP, properties))
                .portPropertyFile(withPrefix(prefix, PORT_PROPERTY_FILE, properties))
                .ports(listWithPrefix(prefix, PORTS, properties))
                .privileged(booleanWithPrefix(prefix, PRIVILEGED, properties))
                .restartPolicy(extractRestartPolicy(prefix, properties))
                .user(withPrefix(prefix, USER, properties))
                .workingDir(withPrefix(prefix, WORKING_DIR, properties))
                .wait(extractWaitConfig(prefix, properties))
                .volumes(extractVolumeConfig(prefix, properties))
                .build();
    }

    private AssemblyConfiguration extractAssembly(String prefix, Properties properties) {
        return new AssemblyConfiguration.Builder()
                .basedir(withPrefix(prefix, ASSEMBLY_BASEDIR, properties))        
                .descriptor(withPrefix(prefix, ASSEMBLY_DESCRIPTOR, properties))
                .descriptorRef(withPrefix(prefix, ASSEMBLY_DESCRIPTOR_REF, properties))
                .dockerFileDir(withPrefix(prefix, DOCKER_FILE_DIR, properties))
                .exportBasedir(booleanWithPrefix(prefix, ASSEMBLY_EXPORT_BASEDIR, properties))
                .ignorePermissions(booleanWithPrefix(prefix, ASSEMBLY_IGNORE_PERMISSIONS, properties))
                .user(withPrefix(prefix, ASSEMBLY_USER, properties))
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
        return new RestartPolicy.Builder()
                .name(withPrefix(prefix, RESTART_POLICY_NAME, properties))
                .retry(asInt(withPrefix(prefix, RESTART_POLICY_RETRY, properties)))
                .build();
    }

    private WaitConfiguration extractWaitConfig(String prefix, Properties properties) {
        return new WaitConfiguration.Builder()
                .time(asInt(withPrefix(prefix, WAIT_TIME,properties)))
                .url(withPrefix(prefix, WAIT_URL, properties))
                .log(withPrefix(prefix, WAIT_LOG, properties))
                .shutdown(asInt(withPrefix(prefix,WAIT_SHUTDOWN,properties)))
                .build();
    }

    private VolumeConfiguration extractVolumeConfig(String prefix, Properties properties) {
        return new VolumeConfiguration.Builder()
                .bind(listWithPrefix(prefix, BIND, properties))
                .from(listWithPrefix(prefix, VOLUMES_FROM, properties))
                .build();
    }
  
    private int asInt(String s) {
        return s != null ? Integer.parseInt(s) : 0;
    }

    private List<String> listWithPrefix(String prefix, ConfigKey key, Properties properties) {
        return extractFromPropertiesAsList(key.asPropertyKey(prefix), properties);
    }
   
    private Map<String, String> mapWithPrefix(String prefix, ConfigKey key, Properties properties) {
        return extractFromPropertiesAsMap(key.asPropertyKey(prefix), properties);
    }
        
    private String withPrefix(String prefix, ConfigKey key, Properties properties) {
        return properties.getProperty(key.asPropertyKey(prefix));
    }

    private Long longWithPrefix(String prefix, ConfigKey key, Properties properties) {
        String prop = withPrefix(prefix, key, properties);
        return prop == null ? null : Long.valueOf(prop);
    }

    private Boolean booleanWithPrefix(String prefix, ConfigKey key, Properties properties) {
        String prop = withPrefix(prefix,key,properties);
        return prop == null ? null : Boolean.valueOf(prop);
    }
    
    private String getPrefix(ImageConfiguration config) {
        Map<String, String> refConfig = config.getExternalConfig();
        String prefix = refConfig != null ? refConfig.get("prefix") : null;
        if (prefix == null) {
            prefix = "docker";
        }
        return prefix;
    }
}
