package io.fabric8.maven.docker.log;/*
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

import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.config.LogConfiguration;
import io.fabric8.maven.docker.config.RunImageConfiguration;

/**
 * @author roland
 * @since 26/09/15
 */
public class LogOutputSpecFactory {
    private boolean useColor;
    private boolean logStdout;
    private String logDate;

    public LogOutputSpecFactory(boolean useColor, boolean logStdout, String logDate) {
        this.useColor = useColor;
        this.logStdout = logStdout;
        this.logDate = logDate;
    }

    // ================================================================================================

    public LogOutputSpec createSpec(String containerId, ImageConfiguration imageConfiguration) {
        LogOutputSpec.Builder builder = new LogOutputSpec.Builder();
        LogConfiguration logConfig = extractLogConfiguration(imageConfiguration);

        addLogFormat(builder, logConfig);
        addPrefix(builder, logConfig.getPrefix()
                , imageConfiguration.getAlias(), containerId);
        builder.file(logConfig.getFileLocation())
               .containerId(containerId)
               .useColor(useColor)
               .logStdout(logStdout)
               .color(logConfig.getColor());

        return builder.build();
    }

    private void addPrefix(LogOutputSpec.Builder builder, String logPrefix, String alias, String containerId) {
        String prefix = logPrefix;
        if (prefix == null) {
            prefix = alias;
        }
        if (prefix == null) {
            prefix = containerId.substring(0, 6);
        }
        builder.prefix(prefix);
    }

    private void addLogFormat(LogOutputSpec.Builder builder, LogConfiguration logConfig) {
        String logFormat = logConfig.getDate() != null ? logConfig.getDate() : logDate;
        if (logFormat != null && logFormat.equalsIgnoreCase("true")) {
            logFormat = "DEFAULT";
        }
        if (logFormat != null) {
            builder.timeFormatter(logFormat);
        }
    }

    private LogConfiguration extractLogConfiguration(ImageConfiguration imageConfiguration) {
        RunImageConfiguration runConfig = imageConfiguration.getRunConfiguration();
        LogConfiguration logConfig = null;
        if (runConfig != null) {
            logConfig = runConfig.getLogConfiguration();
        }
        if (logConfig == null) {
            logConfig = LogConfiguration.DEFAULT;
        }
        return logConfig;
    }
}
