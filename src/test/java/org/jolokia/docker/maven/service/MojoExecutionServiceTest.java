package org.jolokia.docker.maven.service;/*
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

import java.io.IOException;
import java.io.StringReader;

import mockit.*;
import mockit.integration.junit4.JMockit;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.*;
import org.apache.maven.plugin.descriptor.MojoDescriptor;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.configuration.PlexusConfiguration;
import org.codehaus.plexus.configuration.xml.XmlPlexusConfiguration;
import org.codehaus.plexus.util.xml.Xpp3DomBuilder;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.eclipse.aether.RepositorySystemSession;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @author roland
 * @since 01/07/15
 */
@RunWith(JMockit.class)
public class MojoExecutionServiceTest {

    @Tested
    MojoExecutionService executionService;

    @Mocked @Injectable
    protected MavenProject project;

    @Mocked @Injectable
    MavenSession session;

    @Mocked @Injectable
    BuildPluginManager pluginManager;

    @Mocked
    RepositorySystemSession repository;

    @Mocked
    PluginDescriptor pluginDescriptor;

    @Test
    public void straight() throws MojoFailureException, MojoExecutionException, InvalidPluginDescriptorException, PluginResolutionException, PluginDescriptorParsingException, PluginNotFoundException, PluginConfigurationException, PluginManagerException, IOException, XmlPullParserException {
        call("io.fabric8:fabric8-maven-plugin", "delete-pods", null, true);
    }

    @Test
    public void straightWithExecutionId() throws MojoFailureException, MojoExecutionException, InvalidPluginDescriptorException, PluginResolutionException, PluginDescriptorParsingException, PluginNotFoundException, PluginConfigurationException, PluginManagerException, IOException, XmlPullParserException {
        call("io.fabric8:fabric8-maven-plugin", "delete-pods", "1", true);
    }

    @Test(expected = MojoExecutionException.class)
    public void noDescriptor() throws MojoFailureException, MojoExecutionException, InvalidPluginDescriptorException, PluginResolutionException, PluginDescriptorParsingException, PluginNotFoundException, PluginConfigurationException, PluginManagerException, IOException, XmlPullParserException {
        call("io.fabric8:fabric8-maven-plugin", "delete-pods", null, false);
    }

    private void call(final String plugin, final String goal, final String executionId, final boolean withDescriptor) throws MojoFailureException, MojoExecutionException, InvalidPluginDescriptorException, PluginResolutionException, PluginDescriptorParsingException, PluginNotFoundException, PluginConfigurationException, PluginManagerException, IOException, XmlPullParserException {
        new Expectations() {{
            project.getPlugin(plugin);
            Plugin plugin = new Plugin();
            result = new Plugin();

            session.getRepositorySession();
            result = repository;

            project.getRemotePluginRepositories();
            result = null;

            pluginManager.loadPlugin(plugin,null,repository);
            result = pluginDescriptor;

            pluginDescriptor.getMojo(goal);
            if (withDescriptor) {
                MojoDescriptor descriptor = new MojoDescriptor();
                PlexusConfiguration config = new XmlPlexusConfiguration(Xpp3DomBuilder.build(new StringReader("<config name='test'><test>1</test></config>")));
                descriptor.setMojoConfiguration(config);
                result = descriptor;

                pluginManager.executeMojo(session, (MojoExecution) any);
            } else {
                result = null;
            }
        }};

        executionService.callPluginGoal(plugin + ":" + goal + (executionId != null ? "#" + executionId : "") );

        new Verifications() {{
        }};
    }

    @Test(expected = MojoFailureException.class)
    public void noPlugin() throws MojoFailureException, MojoExecutionException {
        new Expectations() {{
            project.getPlugin(anyString);
            result = null;
        }};

        executionService.callPluginGoal("bla:blub:bla");
    }

    @Test(expected = MojoFailureException.class)
    public void wrongFormat() throws MojoFailureException, MojoExecutionException {
        executionService.callPluginGoal("blubber");
    }
}
