package io.fabric8.maven.docker.service.helper;

import io.fabric8.maven.docker.access.DockerAccessException;
import io.fabric8.maven.docker.access.ExecException;
import io.fabric8.maven.docker.access.PortMapping;
import io.fabric8.maven.docker.config.*;
import io.fabric8.maven.docker.log.LogDispatcher;
import io.fabric8.maven.docker.service.RunService;
import io.fabric8.maven.docker.service.ServiceHub;
import io.fabric8.maven.docker.service.ServiceHubFactory;
import io.fabric8.maven.docker.util.Logger;
import io.fabric8.maven.docker.util.PomLabel;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;

import java.util.Date;
import java.util.Properties;

public class RunConfigurationExecutionHelper {
    private Logger log;
    private ServiceHubFactory serviceHubFactory;
    private ServiceHub hub;
    private boolean follow;
    private String showLogs;
    private String containerNamePattern;
    private Date buildDate;
    private MavenProject project;
    private ImageConfiguration imageConfig;
    private PomLabel pomLabel;
    private PortMapping portMapping;
    private LogDispatcher dispatcher;
    private RunService runService;

    private RunConfigurationExecutionHelper(){}

    public String executeRunConfiguration() throws DockerAccessException, MojoExecutionException, ExecException {
        final Properties projProperties = project.getProperties();

        final String containerId = runService.createAndStartContainer(imageConfig, portMapping, pomLabel, projProperties, project.getBasedir(), containerNamePattern, buildDate);

        if (showLogs(imageConfig)) {
            dispatcher.trackContainerLog(containerId,
                    serviceHubFactory.getLogOutputSpecFactory().createSpec(containerId, imageConfig));
        }

        // Wait if requested
        hub.getWaitService().wait(imageConfig, projProperties, containerId);
        WaitConfiguration waitConfig = imageConfig.getRunConfiguration().getWaitConfiguration();
        if (waitConfig != null && waitConfig.getExec() != null && waitConfig.getExec().getPostStart() != null) {
            try {
                runService.execInContainer(containerId, waitConfig.getExec().getPostStart(), imageConfig);
            } catch (ExecException exp) {
                if (waitConfig.getExec().isBreakOnError()) {
                    throw exp;
                } else {
                    log.warn("Cannot run postStart: %s", exp.getMessage());
                }
            }
        }

        return containerId;
    }

    protected boolean showLogs(ImageConfiguration imageConfig) {
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
        private final RunConfigurationExecutionHelper helper;

        public Builder(){
            helper = new RunConfigurationExecutionHelper();
        }

        public Builder log(Logger log) {
            helper.log = log;
            return this;
        }

        public Builder serviceHubFactory(ServiceHubFactory serviceHubFactory) {
            helper.serviceHubFactory = serviceHubFactory;
            return this;
        }

        public Builder serviceHub(ServiceHub hub) {
            helper.hub = hub;
            return this;
        }

        public Builder project(MavenProject project) {
            helper.project = project;
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

        public Builder buildDate(Date date) {
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

        public Builder pomLabel(PomLabel pomLabel) {
            helper.pomLabel = pomLabel;
            return this;
        }

        public Builder imageConfig(ImageConfiguration imageConfig) {
            helper.imageConfig = imageConfig;
            return this;
        }

        public Builder runService(RunService runService) {
            helper.runService = runService;
            return this;
        }

        public RunConfigurationExecutionHelper build() {
            return helper;
        }
    }
}