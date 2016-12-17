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

import java.io.IOException;
import java.io.StringReader;
import java.lang.reflect.InvocationTargetException;

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
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @author roland
 * @since 01/07/15
 */
@RunWith(JMockit.class)
public class MojoExecutionServiceTest {

    @Tested
    @Mocked
    MojoExecutionService executionService;

    @Injectable
    protected MavenProject project;

    @Injectable
    MavenSession session;

    @Injectable
    BuildPluginManager pluginManager;

    @Mocked
    PluginDescriptor pluginDescriptor;

    private final String PLUGIN_NAME = "io.fabric8:fabric8-maven-plugin";
    private final String GOAL_NAME = "delete-pods";


    @Test
    public void straight() throws Exception {
        expectNewPlugin();
        expectDescriptor();
        overrideGetPluginDescriptor();
        executionService.callPluginGoal(PLUGIN_NAME + ":" + GOAL_NAME);

        new Verifications() {{}};
    }

    @Test
    public void straightWithExecutionId() throws Exception {
        expectNewPlugin();
        expectDescriptor();
        overrideGetPluginDescriptor();
        executionService.callPluginGoal(PLUGIN_NAME + ":" + GOAL_NAME + "#1");
    }

    @Test(expected = MojoExecutionException.class)
    public void noDescriptor() throws Exception {
        expectNewPlugin();
        expectNoDescriptor();
        overrideGetPluginDescriptor();
        executionService.callPluginGoal(PLUGIN_NAME + ":" + GOAL_NAME);

        new Verifications() {{}};
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

    // ============================================================================================

    private Expectations expectNewPlugin() {
        return new Expectations() {{
            project.getPlugin(PLUGIN_NAME);
            result = new Plugin();
        }};
    }

    private Expectations expectDescriptor() throws IOException, XmlPullParserException, PluginConfigurationException, MojoFailureException, MojoExecutionException, PluginManagerException {
        return new Expectations() {{
            pluginDescriptor.getMojo(GOAL_NAME);
            result = createPluginDescriptor();

            pluginManager.executeMojo(session, (MojoExecution) any);
        }};
    }

    private MojoDescriptor createPluginDescriptor() throws XmlPullParserException, IOException {
        MojoDescriptor descriptor = new MojoDescriptor();
        PlexusConfiguration config = new XmlPlexusConfiguration(Xpp3DomBuilder.build(new StringReader("<config name='test'><test>1</test></config>")));
        descriptor.setMojoConfiguration(config);
        return descriptor;
    }

    private Expectations expectNoDescriptor() {
        return new Expectations() {{
            pluginDescriptor.getMojo(GOAL_NAME);
            result = null;
        }};
    }

    private Expectations overrideGetPluginDescriptor() throws NoSuchMethodException, InvocationTargetException, InvalidPluginDescriptorException, IllegalAccessException, PluginResolutionException, PluginNotFoundException, PluginDescriptorParsingException, MojoFailureException {
        return new Expectations() {{
            executionService.getPluginDescriptor((MavenProject) any,(Plugin) any);
        }};
    }
}
