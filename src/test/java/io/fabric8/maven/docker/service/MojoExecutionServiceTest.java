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

import mockit.Expectations;
import mockit.Injectable;
import mockit.Mocked;
import mockit.Tested;
import mockit.Verifications;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.BuildPluginManager;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.descriptor.MojoDescriptor;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.configuration.PlexusConfiguration;
import org.codehaus.plexus.configuration.xml.XmlPlexusConfiguration;
import org.codehaus.plexus.util.xml.Xpp3DomBuilder;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.junit.Test;

/**
 * @author roland
 * @since 01/07/15
 */

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
        standardSetup();
        executionService.callPluginGoal(PLUGIN_NAME + ":" + GOAL_NAME);

        new Verifications() {{}};
    }

    private void standardSetup() throws Exception {
        new Expectations() {{
            project.getPlugin(PLUGIN_NAME);
            result = new Plugin();
            pluginDescriptor.getMojo(GOAL_NAME);
            result = createPluginDescriptor();

            pluginManager.executeMojo(session, (MojoExecution) any);
            executionService.getPluginDescriptor((MavenProject) any, (Plugin) any);
        }};
    }

    @Test
    public void straightWithExecutionId() throws Exception {
        standardSetup();
        executionService.callPluginGoal(PLUGIN_NAME + ":" + GOAL_NAME + "#1");
    }

    @Test(expected = MojoExecutionException.class)
    public void noDescriptor() throws Exception {
        new Expectations() {{
            project.getPlugin(PLUGIN_NAME);
            result = new Plugin();
            pluginDescriptor.getMojo(GOAL_NAME);
            result = null;
            executionService.getPluginDescriptor((MavenProject) any, (Plugin) any);
        }};
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

    private MojoDescriptor createPluginDescriptor() throws XmlPullParserException, IOException {
        MojoDescriptor descriptor = new MojoDescriptor();
        PlexusConfiguration config = new XmlPlexusConfiguration(Xpp3DomBuilder.build(new StringReader("<config name='test'><test>1</test></config>")));
        descriptor.setMojoConfiguration(config);
        return descriptor;
    }

}
