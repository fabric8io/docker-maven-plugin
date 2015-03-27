package org.jolokia.docker.maven.config.handler.property;/*
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

import java.util.*;

import org.jolokia.docker.maven.config.*;
import org.jolokia.docker.maven.config.RunImageConfiguration.NamingStrategy;
import org.junit.Before;
import org.junit.Test;

import static org.jolokia.docker.maven.config.handler.property.ConfigKey.*;
import static org.junit.Assert.*;

/**
 * @author roland
 * @since 05/12/14
 */
public class PropertyConfigHandlerTest {


    private PropertyConfigHandler configHandler;
    private ImageConfiguration imageConfiguration;

    @Before
    public void setUp() throws Exception {
        configHandler = new PropertyConfigHandler();
        imageConfiguration = new ImageConfiguration.Builder().build();
    }

    @Test
    public void testType() throws Exception {
        assertNotNull(configHandler.getType());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testEmpty() throws Exception {
        configHandler.resolve(imageConfiguration,props());
    }

    @Test
    public void testPorts() throws Exception {
        List<ImageConfiguration> configs = configHandler.resolve(
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
        assertArrayEquals(new String[] { "8080","9090","80"},ports);
    }

    @Test
    public void testEnv() throws Exception {
        List<ImageConfiguration> configs = configHandler.resolve(
                imageConfiguration,props(
                        "docker.name","demo",
                        "docker.env.HOME", "/tmp",
                        "docker.env.root.dir", "/bla"
                                        ));

        assertEquals(1,configs.size());
        ImageConfiguration calcConfig = configs.get(0);
        for (Map<String,String> env : new Map[] { calcConfig.getBuildConfiguration().getEnv(),
                                                  calcConfig.getRunConfiguration().getEnv()}) {
            assertEquals(2,env.size());
            assertEquals("/tmp",env.get("HOME"));
            assertEquals("/bla",env.get("root.dir"));
        }
    }

    @Test
    public void testAssembly() throws Exception {
        List<ImageConfiguration> configs = configHandler.resolve(imageConfiguration, props(getTestAssemblyData()));
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
    public void testNoAssembly() throws Exception {
        Properties props = props(new String[] { k(NAME), "image" });
        List<ImageConfiguration> configs = configHandler.resolve(imageConfiguration, props);
        assertEquals(1, configs.size());

        AssemblyConfiguration config = configs.get(0).getBuildConfiguration().getAssemblyConfiguration();
        assertNull(config);
    }
    
    @Test
    public void testResolve() {
        ImageConfiguration resolved = resolveExternalImageConfig(getTestData());

        validateBuildConfiguration(resolved.getBuildConfiguration());
        validateRunConfiguration(resolved.getRunConfiguration());
    }
    
    private ImageConfiguration resolveExternalImageConfig(String[] testData) {
        Map<String, String> external = new HashMap<>();
        external.put("type", "props");

        ImageConfiguration config = new ImageConfiguration.Builder().name("image").alias("alias").externalConfig(external).build();
        PropertyConfigHandler handler = new PropertyConfigHandler();
        
        List<ImageConfiguration> resolvedImageConfigs = handler.resolve(config, props(testData));
        assertEquals(1,resolvedImageConfigs.size());
        ImageConfiguration resolved = resolvedImageConfigs.get(0);

        return resolved;
    }

    private void validateBuildConfiguration(BuildImageConfiguration buildConfig) {
        assertEquals("command.sh", buildConfig.getCommand());
        assertEquals("image", buildConfig.getFrom());
        assertEquals(a("8080"), buildConfig.getPorts());
        assertEquals("registry", buildConfig.getRegistry());
        assertEquals(a("/foo"), buildConfig.getVolumes());
        assertEquals("rhuss@redhat.com",buildConfig.getMaintainer());

        validateEnv(buildConfig.getEnv());
        
        /*
         * validate only the descriptor is required and defaults are all used, 'testAssembly' validates 
         * all options can be set 
         */
        AssemblyConfiguration assemblyConfig = buildConfig.getAssemblyConfiguration();

        assertEquals("/maven", assemblyConfig.getBasedir());
        assertEquals("assembly.xml", assemblyConfig.getDescriptor());
        assertNull(assemblyConfig.getUser());
        assertTrue(assemblyConfig.exportBasedir());
        assertFalse(assemblyConfig.isIgnorePermissions());        
    }

    private void validateEnv(Map<String, String> env) {
        assertTrue(env.containsKey("HOME"));
        assertEquals("/Users/roland", env.get("HOME"));
    }

    private void validateRunConfiguration(RunImageConfiguration runConfig) {
        assertEquals(a("/foo", "/tmp:/tmp"), runConfig.getVolumeConfiguration().getBind());
        assertEquals(a("CAP"), runConfig.getCapAdd());
        assertEquals(a("CAP"), runConfig.getCapDrop());
        assertEquals("command.sh", runConfig.getCommand());
        assertEquals(a("8.8.8.8"), runConfig.getDns());
        assertEquals(a("example.com"), runConfig.getDnsSearch());
        assertEquals("domain.com", runConfig.getDomainname());
        assertEquals("entrypoint.sh", runConfig.getEntrypoint());
        assertEquals(a("localhost:127.0.0.1"), runConfig.getExtraHosts());
        assertEquals("subdomain", runConfig.getHostname());
        assertEquals(a("redis"), runConfig.getLinks());
        assertEquals((Long) 1L, runConfig.getMemory());
        assertEquals((Long) 1L, runConfig.getMemorySwap());
        assertEquals(NamingStrategy.none, runConfig.getNamingStrategy());
        assertEquals("/tmp/envProps.txt",runConfig.getEnvPropertyFile());
        assertEquals("/tmp/props.txt", runConfig.getPortPropertyFile());
        assertEquals(a("8081:8080"), runConfig.getPorts());
        assertEquals(true, runConfig.getPrivileged());
        assertEquals("tomcat", runConfig.getUser());
        assertEquals(a("from"), runConfig.getVolumeConfiguration().getFrom());
        assertEquals("foo", runConfig.getWorkingDir());

        validateEnv(runConfig.getEnv());

        // not sure it's worth it to implement 'equals/hashcode' for these
        RestartPolicy policy = runConfig.getRestartPolicy();
        assertEquals("on-failure", policy.getName());
        assertEquals(1, policy.getRetry());

        WaitConfiguration wait = runConfig.getWaitConfiguration();
        assertEquals("http://foo.com", wait.getUrl());
        assertEquals("pattern", wait.getLog());
        assertEquals(5, wait.getTime());
    }

    private List<String> a(String ... args) {
        return Arrays.asList(args);
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
                k(COMMAND), "command.sh",
                k(DNS) + ".1", "8.8.8.8",
                k(DNS_SEARCH) + ".1", "example.com",
                k(DOMAINNAME), "domain.com",
                k(ENTRYPOINT), "entrypoint.sh",
                k(ENV) + ".HOME","/Users/roland",
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
                k(WAIT_LOG), "pattern",
                k(WAIT_TIME), "5",
                k(WAIT_URL), "http://foo.com",
                k(WORKING_DIR), "foo"
        };
    }

    private String k(ConfigKey from) {
        return from.asPropertyKey();
    }
}
