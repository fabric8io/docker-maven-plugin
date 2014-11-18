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

import org.jolokia.docker.maven.config.ImageConfiguration;

/**
 * @author roland
 * @since 18/11/14
 */
public class Fabric8ConfigHandler implements ReferenceConfigHandler {
    @Override
    public String getType() {
        return "fabric8";
    }

    @Override
    public List<ImageConfiguration> resolve(ImageConfiguration unresolvedConfig) {
        return Collections.singletonList(unresolvedConfig);
    }
}
