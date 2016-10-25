package io.fabric8.maven.docker.config;
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
 * Mode specifying how a cleanup should be performed.
 *
 * @author roland
 * @since 01/03/16
 */
public enum CleanupMode {
    NONE(false, "none"),
    TRY_TO_REMOVE(true, "try"),
    REMOVE(true, "remove");

    private final boolean remove;
    private final String parameter;

    CleanupMode(boolean remove, String parameter) {
        this.remove = remove;
        this.parameter = parameter;
    }

    public static CleanupMode parse(String param) {
        if (param == null || param.equalsIgnoreCase("try")) {
            return TRY_TO_REMOVE;
        } else if (param.equalsIgnoreCase("false") || param.equalsIgnoreCase("none")) {
            return NONE;
        } else if (param.equalsIgnoreCase("true") || param.equalsIgnoreCase("remove")) {
            return REMOVE;
        } else {
            throw new IllegalArgumentException("Invalid clean up mode " + param + " (should be one of: none/try/remove)");
        }
    }

    public boolean isRemove() {
        return remove;
    }

    public String toParameter() {
        return parameter;
    }
}
