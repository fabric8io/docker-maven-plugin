package io.fabric8.maven.docker.service;/*
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
import org.apache.maven.plugin.BuildPluginManager;
import org.apache.maven.plugin.DefaultBuildPluginManager;
import org.apache.maven.plugin.InvalidPluginDescriptorException;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.PluginDescriptorParsingException;
import org.apache.maven.plugin.PluginNotFoundException;
import org.apache.maven.plugin.PluginResolutionException;
import org.apache.maven.plugin.descriptor.MojoDescriptor;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.configuration.PlexusConfiguration;
import org.codehaus.plexus.configuration.xml.XmlPlexusConfiguration;
import org.codehaus.plexus.util.xml.Xpp3DomBuilder;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystemSession;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.io.StringReader;

/**
 * @author roland
 * @since 01/07/15
 */

@ExtendWith(MockitoExtension.class)
class MojoExecutionServiceTest {

    @Spy
    @InjectMocks
    MojoExecutionService executionService;

    @Mock
    protected MavenProject project;

    @Mock
    MavenSession session;

    @Mock
    BuildPluginManager pluginManager;

    @Mock
    PluginDescriptor pluginDescriptor;

    private final String PLUGIN_NAME = "io.fabric8:fabric8-maven-plugin";
    private final String GOAL_NAME = "delete-pods";

    @Test
    void straight() throws Exception {
        pluginSetup(createPluginDescriptor());
        executionService.callPluginGoal(PLUGIN_NAME + ":" + GOAL_NAME);
        pluginVerifications();
    }

    private void pluginSetup() {
        Mockito.doReturn(new DefaultRepositorySystemSession()).when(session).getRepositorySession();
        Mockito.doReturn(new Plugin()).when(project).getPlugin(PLUGIN_NAME);
    }

    private void pluginSetup(MojoDescriptor descriptor) throws Exception {
        pluginSetup();
        Mockito.doReturn(pluginDescriptor).when(pluginManager).loadPlugin(Mockito.any(Plugin.class), Mockito.anyList(), Mockito.any(RepositorySystemSession.class));
        Mockito.doReturn(descriptor).when(pluginDescriptor).getMojo(GOAL_NAME);
    }

    private void pluginVerifications() throws Exception {
        Mockito.verify(pluginManager).executeMojo(Mockito.any(MavenSession.class), Mockito.any(MojoExecution.class));
        Mockito.verify(executionService).getPluginDescriptor(Mockito.any(MavenProject.class), Mockito.any(Plugin.class));
    }

    @Test
    void straightWithExecutionId() throws Exception {
        pluginSetup(createPluginDescriptor());
        executionService.callPluginGoal(PLUGIN_NAME + ":" + GOAL_NAME + "#1");
        pluginVerifications();
    }

    @Test
    void missingGoal() throws Exception {
        pluginSetup();
        Mockito.doThrow(new PluginResolutionException(new Plugin(), new Exception()))
            .when(pluginManager).loadPlugin(Mockito.any(Plugin.class), Mockito.anyList(), Mockito.any(RepositorySystemSession.class));
        Assertions.assertThrows(MojoFailureException.class, () -> executionService.callPluginGoal(PLUGIN_NAME + ":" + GOAL_NAME));
    }

    @Test
    void noDescriptor() throws Exception {
        pluginSetup(null);

        Assertions.assertThrows(MojoExecutionException.class, () -> executionService.callPluginGoal(PLUGIN_NAME + ":" + GOAL_NAME));

        Mockito.verify(executionService).getPluginDescriptor(Mockito.any(MavenProject.class), Mockito.any(Plugin.class));
    }

    @Test
    void noPlugin() {
        Mockito.doReturn(null).when(project).getPlugin(Mockito.anyString());
        Assertions.assertThrows(MojoFailureException.class, () -> executionService.callPluginGoal("bla:blub:bla"));
    }

    @Test
    void wrongFormat() {
        Assertions.assertThrows(MojoFailureException.class, () -> executionService.callPluginGoal("blubber"));
    }

    // ============================================================================================

    private MojoDescriptor createPluginDescriptor() throws XmlPullParserException, IOException {
        MojoDescriptor descriptor = new MojoDescriptor();
        PlexusConfiguration config = new XmlPlexusConfiguration(Xpp3DomBuilder.build(new StringReader("<config name='test'><test>1</test></config>")));
        descriptor.setMojoConfiguration(config);
        return descriptor;
    }
}
