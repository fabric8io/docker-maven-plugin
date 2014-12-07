package org.jolokia.docker.maven.config.handler;/*
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
import org.junit.Before;
import org.junit.Test;

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
        String[] ports = new ArrayList<String>(runConfig.getPorts()).toArray(new String[0]);
        assertArrayEquals(new String[] {
                "jolokia.port:8080",
                "9090",
                "0.0.0.0:80:80"
        },ports);
        BuildImageConfiguration buildConfig = configs.get(0).getBuildConfiguration();
        ports = new ArrayList<String>(buildConfig.getPorts()).toArray(new String[0]);
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

    private Properties props(String ... args) {
        Properties ret = new Properties();
        for (int i = 0; i < args.length; i += 2) {
            ret.setProperty(args[i], args[i + 1]);
        }
        return ret;
    }

        private static final String ALIAS = "alias";
    private static final String ASSEMBLY = "assembly.xml";
    private static final String ASSEMBLY_REF = "project";
    private static final String BIND1 = "/foo";
    private static final String BIND2 = "/tmp:/tmp";
    private static final String CAP = "CAP";
    private static final String COMMAND = "command.sh";
    private static final String DOMAINNAME = "domain.com";
    private static final String DNS_IP = "8.8.8.8";
    private static final String ENTRYPOINT = "entrypoint.sh";
    private static final String EXPORT = "/export";
    private static final String HOST = "localhost:127.0.0.1";
    private static final String HOSTNAME = "subdomain";
    private static final String IMAGE = "image";
    private static final String LINK = "redis";
    private static final String P8080 = "8080";
    private static final String PORT = "8081:" + P8080;
    private static final String PROP_FILE = "/tmp/props.txt";
    private static final String REGISTRY = "registry";
    private static final String RESTART_POLICY_NAME = "on-failure";
    private static final String SEARCH = "example.com";
    private static final String TYPE = "type";
    private static final String USER = "tomcat";
    private static final String VOLUME_FROM = "from";
    private static final String WAIT_LOG = "pattern";
    private static final String WAIT_URL = "http://foo.com";
    private static final String WORKING_DIR = "foo";

    private static final List<String> BIND = Arrays.asList(BIND1, BIND2);
    private static final List<String> CAP_ADD_DROP = Arrays.asList(CAP);
    private static final List<String> DNS = Arrays.asList(DNS_IP);
    private static final List<String> DNS_SEARCH = Arrays.asList(SEARCH);
    private static final List<String> EXTRA_HOSTS = Arrays.asList(HOST);
    private static final List<String> LINKS = Arrays.asList(LINK);
    private static final List<String> PORTS = Arrays.asList(PORT);
    private static final List<String> VOLUMES_FROM = Arrays.asList(VOLUME_FROM);

    private static final int RESTART_POLICY_RETRIES = 1;
    private static final int WAIT_TIME = 5;

    private static final Long MEMORY = 1L;
    private static final Long MEMORY_SWAP = 1L;

    private static final boolean PRIVILEGED = true;

    private PropertyConfigHandler handler = new PropertyConfigHandler();

    @Test
    public void testResolve() {
        Map<String, String> external = new HashMap<>();
        external.put(TYPE, PropertyConfigHandler.PROPS);

        ImageConfiguration config = new ImageConfiguration.Builder().name(IMAGE).alias(ALIAS).externalConfig(external).build();
        ImageConfiguration resolved = handler.resolve(config, createProperties(PropertyConfigHandler.DOCKER)).get(0);

        validateBuildConfiguration(resolved.getBuildConfiguration());
        validateRunConfiguration(resolved.getRunConfiguration());
    }

    private void validateBuildConfiguration(BuildImageConfiguration buildConfig) {
        assertEquals(ASSEMBLY, buildConfig.getAssemblyDescriptor());
        assertEquals(ASSEMBLY_REF, buildConfig.getAssemblyDescriptorRef());
        assertEquals(COMMAND, buildConfig.getCommand());
        assertEquals(EXPORT, buildConfig.getExportDir());
        assertEquals(IMAGE, buildConfig.getFrom());
        assertEquals(Arrays.asList(P8080), buildConfig.getPorts());
        assertEquals(REGISTRY, buildConfig.getRegistry());
        assertEquals(Arrays.asList(BIND1), buildConfig.getVolumes());

        validateEnv(buildConfig.getEnv());
    }

    private void validateEnv(Map<String, String> env) {
        assertTrue(env.containsKey(ALIAS));
        assertEquals(USER, env.get(ALIAS));
    }


    private void validateRunConfiguration(RunImageConfiguration runConfig) {
        assertEquals(BIND, runConfig.getBind());
        assertEquals(CAP_ADD_DROP, runConfig.getCapAdd());
        assertEquals(CAP_ADD_DROP, runConfig.getCapDrop());
        assertEquals(COMMAND, runConfig.getCommand());
        assertEquals(DNS, runConfig.getDns());
        assertEquals(DNS_SEARCH, runConfig.getDnsSearch());
        assertEquals(DOMAINNAME, runConfig.getDomainname());
        assertEquals(ENTRYPOINT, runConfig.getEntrypoint());
        assertEquals(EXTRA_HOSTS, runConfig.getExtraHosts());
        assertEquals(HOSTNAME, runConfig.getHostname());
        assertEquals(LINKS, runConfig.getLinks());
        assertEquals(MEMORY, runConfig.getMemory());
        assertEquals(MEMORY_SWAP, runConfig.getMemorySwap());
        assertEquals(PROP_FILE, runConfig.getPortPropertyFile());
        assertEquals(PORTS, runConfig.getPorts());
        assertEquals(PRIVILEGED, runConfig.getPrivileged());
        assertEquals(USER, runConfig.getUser());
        assertEquals(VOLUMES_FROM, runConfig.getVolumesFrom());
        assertEquals(WORKING_DIR, runConfig.getWorkingDir());

        validateEnv(runConfig.getEnv());

        // not sure it's worth it to implement 'equals/hashcode' for these
        RunImageConfiguration.RestartPolicy policy = runConfig.getRestartPolicy();
        assertEquals(RESTART_POLICY_NAME, policy.getName());
        assertEquals(RESTART_POLICY_RETRIES, policy.getRetry());

        WaitConfiguration wait = runConfig.getWaitConfiguration();
        assertEquals(WAIT_URL, wait.getUrl());
        assertEquals(WAIT_LOG, wait.getLog());
        assertEquals(WAIT_TIME, wait.getTime());
    }

    private Properties createProperties(String prefix) {
        Properties properties = new Properties();

        properties.put(createKey(prefix, PropertyConfigHandler.NAME), IMAGE);
        properties.put(createKey(prefix, PropertyConfigHandler.ALIAS), ALIAS);

        properties.put(createKey(prefix, PropertyConfigHandler.FROM), IMAGE);

        // these both can't be active at once, but for testing it's ok
        properties.put(createKey(prefix, PropertyConfigHandler.ASSEMBLY_DESCRIPTOR), ASSEMBLY);
        properties.put(createKey(prefix, PropertyConfigHandler.ASSEMBLY_DESCRIPTOR_REF), ASSEMBLY_REF);

        properties.put(createKey(prefix, createKey(PropertyConfigHandler.BIND, "1")), BIND1);
        properties.put(createKey(prefix, createKey(PropertyConfigHandler.BIND, "2")), BIND2);
        properties.put(createKey(prefix, createKey(PropertyConfigHandler.CAP_ADD, "1")), CAP);
        properties.put(createKey(prefix, createKey(PropertyConfigHandler.CAP_DROP, "1")), CAP);
        properties.put(createKey(prefix, PropertyConfigHandler.COMMAND), COMMAND);
        properties.put(createKey(prefix, createKey(PropertyConfigHandler.DNS, "1")), DNS_IP);
        properties.put(createKey(prefix, createKey(PropertyConfigHandler.DNS_SEARCH, "1")), SEARCH);
        properties.put(createKey(prefix, PropertyConfigHandler.DOMAINNAME), DOMAINNAME);
        properties.put(createKey(prefix, PropertyConfigHandler.ENTRYPOINT), ENTRYPOINT);

        // don't care what these are, just that we get them...
        properties.put(createKey(prefix, createKey(PropertyConfigHandler.ENV, ALIAS)), USER);

        properties.put(createKey(prefix, PropertyConfigHandler.EXPORT_DIR), EXPORT);
        properties.put(createKey(prefix, createKey(PropertyConfigHandler.EXTRA_HOSTS, "1")), HOST);
        properties.put(createKey(prefix, PropertyConfigHandler.HOSTNAME), HOSTNAME);
        properties.put(createKey(prefix, createKey(PropertyConfigHandler.LINKS, "1")), LINK);
        properties.put(createKey(prefix, PropertyConfigHandler.MEMORY), String.valueOf(MEMORY));
        properties.put(createKey(prefix, PropertyConfigHandler.MEMORY_SWAP), String.valueOf(MEMORY_SWAP));
        properties.put(createKey(prefix, PropertyConfigHandler.PORT_PROP_FILE), PROP_FILE);
        properties.put(createKey(prefix, createKey(PropertyConfigHandler.PORTS, "1")), PORT);
        properties.put(createKey(prefix, PropertyConfigHandler.PRIVILEGED), String.valueOf(PRIVILEGED));
        properties.put(createKey(prefix, PropertyConfigHandler.REGISTRY), REGISTRY);
        properties.put(createKey(prefix, PropertyConfigHandler.RESTART_POLICY_NAME), RESTART_POLICY_NAME);
        properties.put(createKey(prefix, PropertyConfigHandler.RESTART_POLICY_RETRY), String.valueOf(RESTART_POLICY_RETRIES));
        properties.put(createKey(prefix, PropertyConfigHandler.USER), USER);
        properties.put(createKey(prefix, createKey(PropertyConfigHandler.VOLUMES, "1")), BIND1);
        properties.put(createKey(prefix, createKey(PropertyConfigHandler.VOLUMES_FROM, "1")), VOLUME_FROM);
        properties.put(createKey(prefix, PropertyConfigHandler.WAIT_LOG), WAIT_LOG);
        properties.put(createKey(prefix, PropertyConfigHandler.WAIT_TIME), String.valueOf(WAIT_TIME));
        properties.put(createKey(prefix, PropertyConfigHandler.WAIT_URL), WAIT_URL);
        properties.put(createKey(prefix, PropertyConfigHandler.WORKING_DIR), WORKING_DIR);

        return properties;
    }

    private String createKey(String prefix, String name) {
        return prefix + "." + name;

    }
}
