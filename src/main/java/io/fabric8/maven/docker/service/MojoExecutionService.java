package io.fabric8.maven.docker.service;

/*
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

import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.AbstractMojoExecutionException;
import org.apache.maven.plugin.BuildPluginManager;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.descriptor.MojoDescriptor;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.configuration.PlexusConfiguration;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.xml.Xpp3Dom;

/**
 * A service for executing goals on configured plugins.
 * <p>
 * Inspired by and partly reused from
 * https://github.com/TimMoore/mojo-executor but adapted to newer Maven versions.
 *
 * @author roland
 * @since 01/07/15
 */
public class MojoExecutionService {

    private final MavenProject project;

    private final MavenSession session;

    private final BuildPluginManager pluginManager;

    MojoExecutionService(MavenProject project, MavenSession session, BuildPluginManager pluginManager) {
        this.project = project;
        this.session = session;
        this.pluginManager = pluginManager;
    }

    // Call another goal after restart has finished
    public void callPluginGoal(String fullGoal) throws MojoFailureException, MojoExecutionException {
        String[] parts = splitGoalSpec(fullGoal);
        Plugin plugin = project.getPlugin(parts[0]);

        if (plugin == null) {
            throw new MojoFailureException("No goal " + fullGoal + " found in pom.xml");
        }

        try {
            String goal = parts[1];
            String executionId = null;
            if (goal.indexOf('#') > -1) {
                int pos = goal.indexOf('#');
                executionId = goal.substring(pos + 1);
                goal = goal.substring(0, pos);
            }

            PluginDescriptor pluginDescriptor = getPluginDescriptor(project, plugin);
            MojoDescriptor mojoDescriptor = pluginDescriptor.getMojo(goal);
            if (mojoDescriptor == null) {
                throw new MojoExecutionException("Could not find goal '" + goal + "' in " + plugin);
            }
            MojoExecution exec = getMojoExecution(executionId, mojoDescriptor);
            pluginManager.executeMojo(session, exec);
        } catch (AbstractMojoExecutionException e) {
            throw e;
        } catch (Exception e) {
            throw new MojoExecutionException("Unable to execute mojo", e);
        }
    }

    private MojoExecution getMojoExecution(String executionId, MojoDescriptor mojoDescriptor) {
        if (executionId != null) {
            return new MojoExecution(mojoDescriptor, executionId);
        } else {
            return new MojoExecution(mojoDescriptor, toXpp3Dom(mojoDescriptor.getMojoConfiguration()));
        }
    }

    PluginDescriptor getPluginDescriptor(MavenProject project, Plugin plugin)
        throws MojoFailureException {
        try {
            return pluginManager.loadPlugin(plugin, project.getRemotePluginRepositories(), session.getRepositorySession());
        } catch (Exception e) {
            throw new MojoFailureException("Could not load " + plugin, e);
        }
    }

    private String[] splitGoalSpec(String fullGoal) throws MojoFailureException {
        String[] parts = StringUtils.split(fullGoal, ":");
        if (parts.length != 3) {
            throw new MojoFailureException("Cannot parse " + fullGoal + " as a maven plugin goal. " +
                "It must be fully qualified as in <groupId>:<artifactId>:<goal>");
        }
        return new String[] { parts[0] + ":" + parts[1], parts[2] };
    }

    private Xpp3Dom toXpp3Dom(PlexusConfiguration config) {
        Xpp3Dom result = new Xpp3Dom(config.getName());
        result.setValue(config.getValue(null));
        for (String name : config.getAttributeNames()) {
            result.setAttribute(name, config.getAttribute(name));
        }
        for (PlexusConfiguration child : config.getChildren()) {
            result.addChild(toXpp3Dom(child));
        }
        return result;
    }

}
