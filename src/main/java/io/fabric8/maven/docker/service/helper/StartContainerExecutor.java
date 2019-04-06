package io.fabric8.maven.docker.service.helper;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.Map;
import java.util.Properties;

import org.codehaus.plexus.util.StringUtils;

import io.fabric8.maven.docker.access.DockerAccessException;
import io.fabric8.maven.docker.access.ExecException;
import io.fabric8.maven.docker.access.PortMapping;
import io.fabric8.maven.docker.config.ConfigHelper;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.config.LogConfiguration;
import io.fabric8.maven.docker.config.RunImageConfiguration;
import io.fabric8.maven.docker.config.WaitConfiguration;
import io.fabric8.maven.docker.log.LogDispatcher;
import io.fabric8.maven.docker.log.LogOutputSpecFactory;
import io.fabric8.maven.docker.model.Container;
import io.fabric8.maven.docker.service.ServiceHub;
import io.fabric8.maven.docker.util.GavLabel;
import io.fabric8.maven.docker.util.Logger;

public class StartContainerExecutor {
    private String exposeContainerProps;
    private Logger log;
    private LogOutputSpecFactory logOutputSpecFactory;
    private ServiceHub hub;
    private boolean follow;
    private String showLogs;
    private String containerNamePattern;
    private Date buildDate;
    private Properties projectProperties;
    private File basedir;
    private ImageConfiguration imageConfig;
    private GavLabel gavLabel;
    private PortMapping portMapping;
    private LogDispatcher dispatcher;

    private StartContainerExecutor(){}

    public String startContainers() throws IOException, ExecException {
        final Properties projProperties = projectProperties;

        final String containerId = hub.getRunService().createAndStartContainer(imageConfig, portMapping, gavLabel, projProperties, basedir, containerNamePattern, buildDate);

        showLogsIfRequested(containerId);
        exposeContainerProps(containerId);
        waitAndPostExec(containerId, projProperties);

        return containerId;
    }

    private void exposeContainerProps(String containerId)
        throws DockerAccessException {
        String propKey = getExposedPropertyKeyPart();

        if (StringUtils.isNotEmpty(exposeContainerProps) && StringUtils.isNotEmpty(propKey)) {
            Container container = hub.getQueryService().getMandatoryContainer(containerId);

            String prefix = addDot(exposeContainerProps) + addDot(propKey);
            projectProperties.put(prefix + "id", containerId);
            String ip = container.getIPAddress();
            if (StringUtils.isNotEmpty(ip)) {
                projectProperties.put(prefix + "ip", ip);
            }

            Map<String, String> nets = container.getCustomNetworkIpAddresses();
            if (nets != null) {
                for (Map.Entry<String, String> entry : nets.entrySet()) {
                    projectProperties.put(prefix + addDot("net") + addDot(entry.getKey()) + "ip", entry.getValue());
                }
            }
        }
    }

    String getExposedPropertyKeyPart() {
        String propKey = imageConfig.getRunConfiguration() != null ? imageConfig.getRunConfiguration().getExposedPropertyKey() : "";
        if (StringUtils.isEmpty(propKey)) {
            propKey = imageConfig.getAlias();
        }
        return propKey;
    }

    private String addDot(String part) {
        return part.endsWith(".") ? part : part + ".";
    }

    private void showLogsIfRequested(String containerId) {
        if (showLogs()) {
            dispatcher.trackContainerLog(containerId,
                                         logOutputSpecFactory.createSpec(containerId, imageConfig));
        }
    }

    private void waitAndPostExec(String containerId, Properties projProperties) throws IOException, ExecException {
        // Wait if requested
        hub.getWaitService().wait(imageConfig, projProperties, containerId);
        WaitConfiguration waitConfig = imageConfig.getRunConfiguration().getWaitConfiguration();
        if (waitConfig != null && waitConfig.getExec() != null && waitConfig.getExec().getPostStart() != null) {
            try {
                hub.getRunService().execInContainer(containerId, waitConfig.getExec().getPostStart(), imageConfig);
            } catch (ExecException exp) {
                if (waitConfig.getExec().isBreakOnError()) {
                    throw exp;
                } else {
                    log.warn("Cannot run postStart: %s", exp.getMessage());
                }
            }
        }
    }

    boolean showLogs() {
        if (showLogs != null) {
            if (showLogs.equalsIgnoreCase("true")) {
                return true;
            } else if (showLogs.equalsIgnoreCase("false")) {
                return false;
            } else {
                return ConfigHelper.matchesConfiguredImages(showLogs, imageConfig);
            }
        }

        RunImageConfiguration runConfig = imageConfig.getRunConfiguration();
        if (runConfig != null) {
            LogConfiguration logConfig = runConfig.getLogConfiguration();
            if (logConfig != null) {
                return logConfig.isActivated();
            } else {
                // Default is to show logs if "follow" is true
                return follow;
            }
        }
        return false;
    }

    public static class Builder {
        private final StartContainerExecutor helper;

        public Builder(){
            helper = new StartContainerExecutor();
        }

        public Builder log(Logger log) {
            helper.log = log;
            return this;
        }

        public Builder logOutputSpecFactory(LogOutputSpecFactory factory) {
            helper.logOutputSpecFactory = factory;
            return this;
        }

        public Builder exposeContainerProps(String exposeContainerProps) {
            helper.exposeContainerProps = exposeContainerProps;
            return this;
        }

        public Builder serviceHub(ServiceHub hub) {
            helper.hub = hub;
            return this;
        }

        public Builder projectProperties(Properties props) {
            helper.projectProperties = props;
            return this;
        }

        public Builder basedir(File dir) {
            helper.basedir = dir;
            return this;
        }

        public Builder follow(boolean follow) {
            helper.follow = follow;
            return this;
        }

        public Builder showLogs(String showLogs) {
            helper.showLogs = showLogs;
            return this;
        }

        public Builder containerNamePattern(String pattern) {
            helper.containerNamePattern = pattern;
            return this;
        }

        public Builder buildTimestamp(Date date) {
            helper.buildDate = date;
            return this;
        }



        public Builder dispatcher(LogDispatcher dispatcher) {
            helper.dispatcher = dispatcher;
            return this;
        }

        public Builder portMapping(PortMapping portMapping) {
            helper.portMapping = portMapping;
            return this;
        }

        public Builder gavLabel(GavLabel gavLabel) {
            helper.gavLabel = gavLabel;
            return this;
        }

        public Builder imageConfig(ImageConfiguration imageConfig) {
            helper.imageConfig = imageConfig;
            return this;
        }

        public StartContainerExecutor build() {
            return helper;
        }
    }
}