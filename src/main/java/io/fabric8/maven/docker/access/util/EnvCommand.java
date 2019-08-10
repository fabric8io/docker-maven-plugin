package io.fabric8.maven.docker.access.util;
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

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.fabric8.maven.docker.util.Logger;

/**
 * Command for extracting the environment information emitted by e.g. 'docker-machine env' as
 * a map.
 *
 * @since 14/09/16
 */
abstract public class EnvCommand extends ExternalCommand {

    private final Map<String, String> env = new HashMap<>();

    private final String prefix;

    public EnvCommand(Logger log, String prefix) {
        super(log);
        this.prefix = prefix;
    }

    @Override
    protected void processLine(String line) {
        if (log.isDebugEnabled()) {
            log.verbose(Logger.LogVerboseCategory.BUILD,"%s", line);
        }
        if (line.startsWith(prefix)) {
            setEnvironmentVariable(line.substring(prefix.length()));
        }
    }

    private final Pattern ENV_VAR_PATTERN = Pattern.compile("^\\s*(?<key>[^=]+)=\"?(?<value>.*?)\"?\\s*$");

    // parse line like SET DOCKER_HOST=tcp://192.168.99.100:2376
    private void setEnvironmentVariable(String line) {
        Matcher matcher = ENV_VAR_PATTERN.matcher(line);
        if (matcher.matches()) {
            String key = matcher.group("key");
            String value = matcher.group("value");
            log.debug("Env: %s=%s",key,value);
            env.put(key, value);
        }
    }

    public Map<String, String> getEnvironment() throws IOException {
        execute();
        return env;
    }
}
