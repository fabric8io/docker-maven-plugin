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

import java.util.*;

import org.jolokia.docker.maven.config.*;

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
    public List<ImageConfiguration> resolve(ImageConfiguration config, Properties properties) {
        String prefix = getPrefix(config);
        WaitConfiguration wait = extractWaitConfig(prefix,properties);
        RunImageConfiguration run = extractRunConfiguration(prefix,properties,wait);
        BuildImageConfiguration build = extractBuildConfiguration(prefix,properties);

        String name = withPrefix(prefix, "name", properties);
        String alias = withPrefix(prefix, "alias", properties);

        return Collections.singletonList(
                new ImageConfiguration.Builder()
                        .name(name != null ? name : config.getName())
                        .alias(alias != null ? alias : config.getAlias())
                        .runConfig(run)
                        .buildConfig(build)
                        .build());
    }

    private BuildImageConfiguration extractBuildConfiguration(String prefix, Properties properties) {
        return new BuildImageConfiguration.Builder()
                .command(withPrefix(prefix, "command", properties))
                .assemblyDescriptor(withPrefix(prefix, "assemblyDescriptor", properties))
                .assemblyDescriptorRef(withPrefix(prefix, "assemblyDescriptorRef", properties))
                .env(extractFromPropertiesAsMap(prefix + ".env", properties))
                .ports(extractPortValues(prefix, properties))
                .from(withPrefix(prefix, "from", properties))
                .exportDir(withPrefix(prefix, "exportDir", properties))
                .registry(withPrefix(prefix, "registry", properties))
                .build();
    }

    private RunImageConfiguration extractRunConfiguration(String prefix, Properties properties, WaitConfiguration wait) {
        return new RunImageConfiguration.Builder()
                .command(withPrefix(prefix, "command", properties))
                .wait(wait)
                .env(extractFromPropertiesAsMap(prefix + ".env", properties))
                .ports(extractPorts(prefix + ".ports", properties))
                .links(extractFromPropertiesAsList(prefix + ".links", properties))
                .volumes(extractFromPropertiesAsList(prefix + ".volumesFrom", properties))
                .portPropertyFile(withPrefix(prefix, "portPropertyFile", properties))
                .build();
    }

    private List<String> extractPortValues(String prefix, Properties properties) {
        return new ArrayList<String>(extractFromPropertiesAsMap(prefix + ".ports", properties).values());
    }

    private List<String> extractPorts(String prefix, Properties properties) {
        Map<String,String> portMap = extractFromPropertiesAsMap(prefix,properties);
        List<String> ret = new ArrayList<>();
        for (Map.Entry<String,String> entry : portMap.entrySet()) {
            ret.add(entry.getKey() + ":" + entry.getValue());
        }
        return ret;
    }

    private WaitConfiguration extractWaitConfig(String prefix, Properties properties) {
        return new WaitConfiguration.Builder()
                .time(asInt(withPrefix(prefix,"wait.time",properties)))
                .url(withPrefix(prefix,"wait.url",properties))
                .log(withPrefix(prefix,"wait.log",properties))
                .build();
    }

    private int asInt(String s) {
        return s != null ? Integer.parseInt(s) : 0;
    }

    private String withPrefix(String prefix, String key, Properties properties) {
        return properties.getProperty(prefix + "." + key);
    }

    private String getPrefix(ImageConfiguration config) {
        Map<String, String> refConfig = config.getExternalConfig();
        String prefix = refConfig.get("prefix");
        if (prefix == null) {
            prefix = "docker";
        }
        return prefix;
    }
}
