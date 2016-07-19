package io.fabric8.maven.docker.util;
/*-
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

/**
 * Enum holding the possible values avalaible for auto-pulling.
 *
 * @author roland
 * @since 01/03/15
 */
public enum AutoPullMode {

    ON(true, "on", "true"),
    ONCE(true, "once"),
    OFF(false, "off", "false"),
    ALWAYS(true, "always");

    private Set<String> values = new HashSet<>();
    private boolean doPullIfNotPresent;

    AutoPullMode(boolean doPullIfNotPresent, String... vals) {
        this.doPullIfNotPresent = doPullIfNotPresent;
        Collections.addAll(values, vals);
    }

    public boolean doPullIfNotPresent() {
        return doPullIfNotPresent;
    }

    public boolean alwaysPull() {
        return (this == ONCE || this == ALWAYS);
    }

    static public AutoPullMode fromString(String val) {
        String valNorm = val.toLowerCase();
        for (AutoPullMode mode : values()) {
            if (mode.values.contains(valNorm)) {
                return mode;
            }
        }
        throw new IllegalArgumentException("Invalid auto-pull mode " + val + ". Please use 'on', 'off', 'once' or 'always'.");
    }
}
