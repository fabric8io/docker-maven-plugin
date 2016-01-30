package org.jolokia.docker.maven.config.handler.property;
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

import static org.jolokia.docker.maven.config.handler.property.ConfigKey.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.maven.project.MavenProject;
import org.jolokia.docker.maven.config.*;
import org.jolokia.docker.maven.config.RunImageConfiguration.NamingStrategy;
import org.jolokia.docker.maven.config.external.ExternalImageConfiguration;
import org.jolokia.docker.maven.config.external.PropertiesConfiguration;
import org.jolokia.docker.maven.config.handler.AbstractConfigHandlerTest;
import org.junit.Before;
import org.junit.Test;

/**
 * @author roland
 * @since 05/12/14
 */
public class PropertyConfigHandlerTest extends AbstractConfigHandlerTest {

    private PropertyConfigHandler configHandler;
    private ImageConfiguration imageConfiguration;
    
    @Before
    public void setUp() throws Exception {
        configHandler = new PropertyConfigHandler();
        imageConfiguration = buildAnUnresolvedImage();
    }
    
    @Test
    public void testSkipBuild() {
        assertFalse(resolveExternalImageConfig(getSkipTestData(ConfigKey.SKIP_BUILD, false)).getBuildConfiguration().skip());
        assertTrue(resolveExternalImageConfig(getSkipTestData(ConfigKey.SKIP_BUILD, true)).getBuildConfiguration().skip());
        
        assertFalse(resolveExternalImageConfig(new String[] { k(NAME), "image"}).getBuildConfiguration().skip());
    }

    @Test
    public void testSkipRun() {
        assertFalse(resolveExternalImageConfig(getSkipTestData(ConfigKey.SKIP_RUN, false)).getRunConfiguration().skip());
        assertTrue(resolveExternalImageConfig(getSkipTestData(ConfigKey.SKIP_RUN, true)).getRunConfiguration().skip());
        
        assertFalse(resolveExternalImageConfig(new String[] { k(NAME), "image"}).getRunConfiguration().skip());
    }    
    
    @Test
    public void testType() throws Exception {
        assertNotNull(configHandler.getType());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testEmpty() throws Exception {
        resolveImage(imageConfiguration, props());
    }

    @Test
    public void testPorts() throws Exception {
        List<ImageConfiguration> configs = resolveImage(
                imageConfiguration,props(
                        "docker.name","demo",
                        "docker.ports.1", "jolokia.port:8080",
                        "docker.ports.2", "9090",
                        "docker.ports.3", "0.0.0.0:80:80"
                                        ));
        assertEquals(1,configs.size());
        RunImageConfiguration runConfig = configs.get(0).getRunConfiguration();
        List<String> portsAsList = runConfig.getPorts();
        String[] ports = new ArrayList<>(portsAsList).toArray(new String[portsAsList.size()]);
        assertArrayEquals(new String[] {
                "jolokia.port:8080",
                "9090",
                "0.0.0.0:80:80"
        },ports);
        BuildImageConfiguration buildConfig = configs.get(0).getBuildConfiguration();
        ports = new ArrayList<>(buildConfig.getPorts()).toArray(new String[buildConfig.getPorts().size()]);
        assertArrayEquals(new String[]{"8080", "9090", "80"}, ports);
    }

    @Test
    public void testEnvAndLabels() throws Exception {
        List<ImageConfiguration> configs = resolveImage(
                imageConfiguration,props(
                        "docker.name","demo",
                        "docker.env.HOME", "/tmp",
                        "docker.env.root.dir", "/bla",
                        "docker.labels.version", "1.0.0",
                        "docker.labels.blub.bla.foobar", "yep"
                                        ));

        assertEquals(1,configs.size());
        ImageConfiguration calcConfig = configs.get(0);
        for (Map env : new Map[] { calcConfig.getBuildConfiguration().getEnv(),
                                   calcConfig.getRunConfiguration().getEnv()}) {
            assertEquals(2,env.size());
            assertEquals("/tmp",env.get("HOME"));
            assertEquals("/bla",env.get("root.dir"));
        }
        for (Map labels : new Map[] { calcConfig.getBuildConfiguration().getLabels(),
                                      calcConfig.getRunConfiguration().getLabels()}) {
            assertEquals(2, labels.size());
            assertEquals("1.0.0", labels.get("version"));
            assertEquals("yep", labels.get("blub.bla.foobar"));
        }
    }

    @Test
    public void testAssembly() throws Exception {
        List<ImageConfiguration> configs = resolveImage(imageConfiguration, props(getTestAssemblyData()));
        assertEquals(1, configs.size());

        AssemblyConfiguration config = configs.get(0).getBuildConfiguration().getAssemblyConfiguration();
        assertEquals("user", config.getUser());
        assertEquals("project", config.getDescriptorRef());
        assertFalse(config.exportBasedir());
        assertTrue(config.isIgnorePermissions());
    }

    @Test
    public void testNamingScheme() throws Exception  {
        String[] testData = new String[] { k(NAME), "image", k(NAMING_STRATEGY), NamingStrategy.alias.toString() };
        
        ImageConfiguration config = resolveExternalImageConfig(testData);
        assertEquals(NamingStrategy.alias, config.getRunConfiguration().getNamingStrategy());
    }
    
    @Test
    public void testNoCleanup() throws Exception {
        String[] testData = new String[] { k(NAME), "image", k(CLEANUP), "false" };
        
        ImageConfiguration config = resolveExternalImageConfig(testData);
        assertEquals(false, config.getBuildConfiguration().cleanup());
    }
    
    @Test
    public void testNoOptimise() throws Exception {
        String[] testData = new String[] { k(NAME), "image", k(OPTIMISE), "false" };

        ImageConfiguration config = resolveExternalImageConfig(testData);
        assertEquals(false, config.getBuildConfiguration().optimise());
    }

    @Test
    public void testNoAssembly() throws Exception {
        Properties props = props(k(NAME), "image");
        List<ImageConfiguration> configs = resolveImage(imageConfiguration, props);
        assertEquals(1, configs.size());

        AssemblyConfiguration config = configs.get(0).getBuildConfiguration().getAssemblyConfiguration();
        assertNull(config);
    }
    
    @Test
    public void testResolve() {
        ImageConfiguration resolved = resolveExternalImageConfig(getTestData());

        validateBuildConfiguration(resolved.getBuildConfiguration());
        validateRunConfiguration(resolved.getRunConfiguration());
        validateWaitConfiguraion(resolved.getRunConfiguration().getWaitConfiguration());
    }
        
    @Override
    protected String getEnvPropertyFile() {
        return "/tmp/envProps.txt";
    }
    
    @Override
    protected NamingStrategy getRunNamingStrategy() {
        return NamingStrategy.none;
    }
    
    @Override
    protected void validateEnv(Map<String, String> env) {
        assertTrue(env.containsKey("HOME"));
        assertEquals("/Users/roland", env.get("HOME"));
    }    
    
    private ImageConfiguration buildAnUnresolvedImage() {
        PropertiesConfiguration propsConfig = new PropertiesConfiguration.Builder()
                .build();

        ExternalImageConfiguration externalConfig = new ExternalImageConfiguration.Builder()
                .properties(propsConfig)
                .build();

        return new ImageConfiguration.Builder()
                .externalConfig(externalConfig)
                .build();
    }
        
    private List<ImageConfiguration> resolveImage(ImageConfiguration image, Properties properties) {
        MavenProject project = mock(MavenProject.class);
        when(project.getProperties()).thenReturn(properties);
        
        return configHandler.resolve(imageConfiguration, project);
    }
    
    private ImageConfiguration resolveExternalImageConfig(String[] testData) {
        List<ImageConfiguration> resolvedImageConfigs = resolveImage(imageConfiguration, props(testData));
        assertEquals(1, resolvedImageConfigs.size());

        return resolvedImageConfigs.get(0);
    }

    private void validateBuildConfiguration(BuildImageConfiguration buildConfig) {
        assertEquals(false, buildConfig.cleanup());
        assertEquals("command.sh", buildConfig.getCmd().getShell());
        assertEquals("image", buildConfig.getFrom());
        assertEquals(a("8080"), buildConfig.getPorts());
        assertEquals("registry", buildConfig.getRegistry());
        assertEquals(a("/foo"), buildConfig.getVolumes());
        assertEquals("rhuss@redhat.com",buildConfig.getMaintainer());

        validateEnv(buildConfig.getEnv());
        validateLabels(buildConfig.getLabels());
        /*
         * validate only the descriptor is required and defaults are all used, 'testAssembly' validates 
         * all options can be set 
         */
        AssemblyConfiguration assemblyConfig = buildConfig.getAssemblyConfiguration();

        assertEquals("/maven", assemblyConfig.getBasedir());
        assertEquals("assembly.xml", assemblyConfig.getDescriptor());
        assertNull(assemblyConfig.getUser());
        assertNull(assemblyConfig.exportBasedir());
        assertFalse(assemblyConfig.isIgnorePermissions());
    }
    
    private void validateWaitConfiguraion(WaitConfiguration wait) {
        assertEquals("http://foo.com", wait.getUrl());
        assertEquals("pattern", wait.getLog());
        assertEquals("post_start_command", wait.getExec().getPostStart());
        assertEquals("pre_stop_command", wait.getExec().getPreStop());
        assertEquals(5, wait.getTime());
    }

    private void validateLabels(Map<String, String> labels) {
        assertEquals("Hello\"World",labels.get("com.acme.label"));
    }

    private Properties props(String ... args) {
        Properties ret = new Properties();
        for (int i = 0; i < args.length; i += 2) {
            ret.setProperty(args[i], args[i + 1]);
        }
        return ret;
    }

    private String[] getTestAssemblyData() {
        return new String[] { 
                k(ASSEMBLY_BASEDIR), "/basedir",
                k(ASSEMBLY_DESCRIPTOR_REF), "project",
                k(ASSEMBLY_EXPORT_BASEDIR), "false",
                k(ASSEMBLY_IGNORE_PERMISSIONS), "true",
                k(ASSEMBLY_USER), "user",
                k(NAME), "image",
        };
    }
    
    private String[] getTestData() {
        return new String[] {
                k(ALIAS),"alias",
                k(ASSEMBLY_DESCRIPTOR), "assembly.xml",
                k(BIND) + ".1", "/foo",
                k(BIND) + ".2", "/tmp:/tmp",
                k(CAP_ADD) + ".1", "CAP",
                k(CAP_DROP) + ".1", "CAP",
                k(CMD), "command.sh",
                k(DNS) + ".1", "8.8.8.8",
                k(DNS_SEARCH) + ".1", "example.com",
                k(DOMAINNAME), "domain.com",
                k(ENTRYPOINT), "entrypoint.sh",
                k(ENV) + ".HOME","/Users/roland",
                k(LABELS) + ".com.acme.label","Hello\"World",
                k(ENV_PROPERTY_FILE),"/tmp/envProps.txt",
                k(EXTRA_HOSTS) + ".1", "localhost:127.0.0.1",
                k(FROM), "image",
                k(HOSTNAME), "subdomain",
                k(LINKS) + ".1", "redis",
                k(MAINTAINER), "rhuss@redhat.com",
                k(MEMORY), "1",
                k(MEMORY_SWAP), "1",
                k(NAME), "image",
                k(PORT_PROPERTY_FILE), "/tmp/props.txt",
                k(PORTS) + ".1", "8081:8080",
                k(PRIVILEGED), "true",
                k(REGISTRY), "registry",
                k(RESTART_POLICY_NAME), "on-failure",
                k(RESTART_POLICY_RETRY), "1",
                k(USER), "tomcat",
                k(VOLUMES) + ".1","/foo",
                k(VOLUMES_FROM) + ".1", "from",
                k(PRE_STOP), "pre_stop_command",
                k(POST_START), "post_start_command",
                k(WAIT_LOG), "pattern",
                k(WAIT_TIME), "5",
                k(WAIT_URL), "http://foo.com",
                k(WORKING_DIR), "foo"
        };
    }
    
    private String[] getSkipTestData(ConfigKey key, boolean value) {
        return new String[] { k(NAME), "image", k(key), String.valueOf(value) };
    }

    private String k(ConfigKey from) {
        return from.asPropertyKey();
    }
}
