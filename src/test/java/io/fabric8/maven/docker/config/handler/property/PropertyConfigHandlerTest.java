package io.fabric8.maven.docker.config.handler.property;/*
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

import java.io.File;
import java.util.*;

import io.fabric8.maven.docker.config.*;
import io.fabric8.maven.docker.config.handler.AbstractConfigHandlerTest;
import io.fabric8.maven.docker.util.MojoParameters;
import mockit.Expectations;
import mockit.Mocked;
import mockit.integration.junit4.JMockit;
import org.apache.maven.project.MavenProject;
import org.junit.*;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

/**
 * @author roland
 * @since 05/12/14
 */
@RunWith(JMockit.class)
public class PropertyConfigHandlerTest extends AbstractConfigHandlerTest {

    private PropertyConfigHandler configHandler;
    private ImageConfiguration imageConfiguration;

    @Mocked
    private MavenProject project;

    @Before
    public void setUp() throws Exception {
        configHandler = new PropertyConfigHandler();
        imageConfiguration = buildAnUnresolvedImage();
    }

    @Test
    public void testSkipBuild() {
        assertFalse(resolveExternalImageConfig(getSkipTestData(ConfigKey.SKIP_BUILD, false)).getBuildConfiguration().skip());
        assertTrue(resolveExternalImageConfig(getSkipTestData(ConfigKey.SKIP_BUILD, true)).getBuildConfiguration().skip());

        assertFalse(resolveExternalImageConfig(new String[] {k(ConfigKey.NAME), "image"}).getBuildConfiguration().skip());
    }

    @Test
    public void testSkipRun() {
        assertFalse(resolveExternalImageConfig(getSkipTestData(ConfigKey.SKIP_RUN, false)).getRunConfiguration().skip());
        assertTrue(resolveExternalImageConfig(getSkipTestData(ConfigKey.SKIP_RUN, true)).getRunConfiguration().skip());

        assertFalse(resolveExternalImageConfig(new String[] {k(ConfigKey.NAME), "image"}).getRunConfiguration().skip());
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
    public void testRunCommands() {
        List<ImageConfiguration> configs = resolveImage(
            imageConfiguration,props(
                "docker.name","demo",
                "docker.run.1", "foo",
                "docker.run.2", "bar",
                "docker.run.3", "wibble")
        );

        assertEquals(1, configs.size());

        BuildImageConfiguration buildConfig = configs.get(0).getBuildConfiguration();
        String[] runCommands = new ArrayList<>(buildConfig.getRunCmds()).toArray(new String[buildConfig.getRunCmds().size()]);
        assertArrayEquals(new String[]{"foo", "bar", "wibble"}, runCommands);
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
        assertFalse(config.exportTargetDir());
        assertTrue(config.isIgnorePermissions());
    }

    @Test
    public void testNamingScheme() throws Exception  {
        String[] testData = new String[] {k(ConfigKey.NAME), "image", k(ConfigKey.NAMING_STRATEGY), RunImageConfiguration.NamingStrategy.alias.toString() };

        ImageConfiguration config = resolveExternalImageConfig(testData);
        assertEquals(RunImageConfiguration.NamingStrategy.alias, config.getRunConfiguration().getNamingStrategy());
    }

    @Test
    public void testNoCleanup() throws Exception {
        String[] testData = new String[] {k(ConfigKey.NAME), "image", k(ConfigKey.CLEANUP), "none" };

        ImageConfiguration config = resolveExternalImageConfig(testData);
        assertEquals(CleanupMode.NONE, config.getBuildConfiguration().cleanupMode());
    }

    @Test
    public void testDockerfile() throws Exception {
        String[] testData = new String[] { k(ConfigKey.NAME), "image", k(ConfigKey.DOCKER_FILE_DIR), "src/main/docker/" };
        ImageConfiguration config = resolveExternalImageConfig(testData);
        config.initAndValidate(ConfigHelper.NameFormatter.IDENTITY, null);
        assertTrue(config.getBuildConfiguration().isDockerFileMode());
        assertEquals(new File("src/main/docker/Dockerfile"), config.getBuildConfiguration().getDockerFile());
    }

    @Test
    public void testDockerArchive() {
        String[] testData = new String[] { k(ConfigKey.NAME), "image", k(ConfigKey.DOCKER_ARCHIVE), "dockerLoad.tar" };
        ImageConfiguration config = resolveExternalImageConfig(testData);
        config.initAndValidate(ConfigHelper.NameFormatter.IDENTITY, null);
        assertFalse(config.getBuildConfiguration().isDockerFileMode());
        assertEquals(new File("dockerLoad.tar"), config.getBuildConfiguration().getDockerArchive());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidDockerFileArchiveConfig() {
        String[] testData = new String[] { k(ConfigKey.NAME), "image", k(ConfigKey.DOCKER_FILE_DIR),  "src/main/docker/", k(ConfigKey.DOCKER_ARCHIVE), "dockerLoad.tar" };
        ImageConfiguration config = resolveExternalImageConfig(testData);
        config.initAndValidate(ConfigHelper.NameFormatter.IDENTITY, null);
    }

    @Test
    public void testNoCacheDisabled() throws Exception {
        String[] testData = new String[] {k(ConfigKey.NAME), "image", k(ConfigKey.NOCACHE), "false" };

        ImageConfiguration config = resolveExternalImageConfig(testData);
        assertEquals(false, config.getBuildConfiguration().nocache());
    }

    @Test
    public void testNoCacheEnabled() throws Exception {
        String[] testData = new String[] {k(ConfigKey.NAME), "image", k(ConfigKey.NOCACHE), "true" };

        ImageConfiguration config = resolveExternalImageConfig(testData);
        assertEquals(true, config.getBuildConfiguration().nocache());
    }

    @Test
    public void testNoOptimise() throws Exception {
        String[] testData = new String[] {k(ConfigKey.NAME), "image", k(ConfigKey.OPTIMISE), "false" };

        ImageConfiguration config = resolveExternalImageConfig(testData);
        assertEquals(false, config.getBuildConfiguration().optimise());
    }

    @Test
    public void testNoAssembly() throws Exception {
        Properties props = props(k(ConfigKey.NAME), "image");
        //List<ImageConfiguration> configs = configHandler.resolve(imageConfiguration, props);
        //assertEquals(1, configs.size());

        //AssemblyConfiguration config = configs.get(0).getBuildConfiguration().getAssemblyConfiguration();
        //assertNull(config);
    }

    @Test
    public void testResolve() {
        ImageConfiguration resolved = resolveExternalImageConfig(getTestData());

        validateBuildConfiguration(resolved.getBuildConfiguration());
        validateRunConfiguration(resolved.getRunConfiguration());
        //validateWaitConfiguraion(resolved.getRunConfiguration().getWaitConfiguration());
    }

    @Override
    protected String getEnvPropertyFile() {
        return "/tmp/envProps.txt";
    }

    @Override
    protected RunImageConfiguration.NamingStrategy getRunNamingStrategy() {
        return RunImageConfiguration.NamingStrategy.none;
    }

    @Override
    protected void validateEnv(Map<String, String> env) {
        assertTrue(env.containsKey("HOME"));
        assertEquals("/Users/roland", env.get("HOME"));
    }

    private ImageConfiguration buildAnUnresolvedImage() {
        return new ImageConfiguration.Builder()
                .externalConfig(new HashMap<String, String>())
                .build();
    }

    private List<ImageConfiguration> resolveImage(ImageConfiguration image, final Properties properties) {
        //MavenProject project = mock(MavenProject.class);
        //when(project.getProperties()).thenReturn(properties);
        new Expectations() {{
            project.getProperties(); result = properties;
        }};

        return configHandler.resolve(imageConfiguration, project, null);
    }

    private ImageConfiguration resolveExternalImageConfig(String[] testData) {
        Map<String, String> external = new HashMap<>();
        external.put("type", "props");

        ImageConfiguration config = new ImageConfiguration.Builder().name("image").alias("alias").externalConfig(external).build();
        PropertyConfigHandler handler = new PropertyConfigHandler();

        List<ImageConfiguration> resolvedImageConfigs = resolveImage(config, props(testData));
        assertEquals(1, resolvedImageConfigs.size());

        return resolvedImageConfigs.get(0);
    }

    private void validateBuildConfiguration(BuildImageConfiguration buildConfig) {
        assertEquals(CleanupMode.TRY_TO_REMOVE, buildConfig.cleanupMode());
        assertEquals("command.sh", buildConfig.getCmd().getShell());
        assertEquals("image", buildConfig.getFrom());
        assertEquals("image-ext", buildConfig.getFromExt().get("name"));
        assertEquals(a("8080"), buildConfig.getPorts());
        assertEquals("registry", buildConfig.getRegistry());
        assertEquals(a("/foo"), buildConfig.getVolumes());
        assertEquals("fabric8io@redhat.com",buildConfig.getMaintainer());
        assertEquals(false, buildConfig.nocache());

        validateEnv(buildConfig.getEnv());
        validateLabels(buildConfig.getLabels());
        validateArgs(buildConfig.getArgs());
        validateBuildOptions(buildConfig.getBuildOptions());
        /*
         * validate only the descriptor is required and defaults are all used, 'testAssembly' validates
         * all options can be set
         */
        AssemblyConfiguration assemblyConfig = buildConfig.getAssemblyConfiguration();

        assertEquals("/maven", assemblyConfig.getTargetDir());
        assertEquals("assembly.xml", assemblyConfig.getDescriptor());
        assertNull(assemblyConfig.getUser());
        assertNull(assemblyConfig.exportTargetDir());
        assertFalse(assemblyConfig.isIgnorePermissions());
    }

    private void validateArgs(Map<String, String> args) {
        assertEquals("http://proxy",args.get("PROXY"));
    }

    private void validateLabels(Map<String, String> labels) {
        assertEquals("Hello\"World",labels.get("com.acme.label"));
    }

    private void validateBuildOptions(Map<String,String> buildOptions) {
        assertEquals("2147483648", buildOptions.get("shmsize"));
    }

    protected void validateRunConfiguration(RunImageConfiguration runConfig) {
        assertEquals(a("/foo", "/tmp:/tmp"), runConfig.getVolumeConfiguration().getBind());
        assertEquals(a("CAP"), runConfig.getCapAdd());
        assertEquals(a("CAP"), runConfig.getCapDrop());
        assertEquals(a("seccomp=unconfined"), runConfig.getSecurityOpts());
        assertEquals("command.sh", runConfig.getCmd().getShell());
        assertEquals(a("8.8.8.8"), runConfig.getDns());
        assertEquals("host",runConfig.getNetworkingConfig().getStandardMode(null));
        assertEquals(a("example.com"), runConfig.getDnsSearch());
        assertEquals("domain.com", runConfig.getDomainname());
        assertEquals("entrypoint.sh", runConfig.getEntrypoint().getShell());
        assertEquals(a("localhost:127.0.0.1"), runConfig.getExtraHosts());
        assertEquals("subdomain", runConfig.getHostname());
        assertEquals(a("redis"), runConfig.getLinks());
        assertEquals((Long) 1L, runConfig.getMemory());
        assertEquals((Long) 1L, runConfig.getMemorySwap());
        Assert.assertEquals(RunImageConfiguration.NamingStrategy.none, runConfig.getNamingStrategy());
        assertEquals("/tmp/envProps.txt",runConfig.getEnvPropertyFile());
        assertEquals("/tmp/props.txt", runConfig.getPortPropertyFile());
        assertEquals(a("8081:8080"), runConfig.getPorts());
        assertEquals(true, runConfig.getPrivileged());
        assertEquals("tomcat", runConfig.getUser());
        assertEquals(a("from"), runConfig.getVolumeConfiguration().getFrom());
        assertEquals("foo", runConfig.getWorkingDir());
        assertNotNull( runConfig.getUlimits());
        assertEquals(4, runConfig.getUlimits().size());
        assertUlimitEquals(ulimit("memlock",10,10),runConfig.getUlimits().get(0));
        assertUlimitEquals(ulimit("memlock",null,-1),runConfig.getUlimits().get(1));
        assertUlimitEquals(ulimit("memlock",1024,null),runConfig.getUlimits().get(2));
        assertUlimitEquals(ulimit("memlock",2048,null),runConfig.getUlimits().get(3));
        assertEquals("/var/lib/mysql:10m", runConfig.getTmpfs().get(0));
        assertEquals(1, runConfig.getTmpfs().size());


        validateEnv(runConfig.getEnv());

        // not sure it's worth it to implement 'equals/hashcode' for these
        RestartPolicy policy = runConfig.getRestartPolicy();
        assertEquals("on-failure", policy.getName());
        assertEquals(1, policy.getRetry());

        WaitConfiguration wait = runConfig.getWaitConfiguration();
        assertEquals("http://foo.com", wait.getUrl());
        assertEquals("pattern", wait.getLog());
        assertEquals("post_start_command", wait.getExec().getPostStart());
        assertEquals("pre_stop_command", wait.getExec().getPreStop());
        assertEquals(5, wait.getTime());

        LogConfiguration config = runConfig.getLogConfiguration();
        assertEquals("green", config.getColor());
        assertTrue(config.isEnabled());
        assertEquals("SRV", config.getPrefix());
        assertEquals("iso8601", config.getDate());
        assertEquals("json",config.getDriver().getName());
        assertEquals(2, config.getDriver().getOpts().size());
        assertEquals("1024", config.getDriver().getOpts().get("max-size"));
        assertEquals("10", config.getDriver().getOpts().get("max-file"));
    }

    private UlimitConfig ulimit(String name, Integer hard, Integer soft) {
        return new UlimitConfig(name, hard, soft);
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
            k(ConfigKey.ASSEMBLY_BASEDIR), "/basedir",
            k(ConfigKey.ASSEMBLY_DESCRIPTOR_REF), "project",
            k(ConfigKey.ASSEMBLY_EXPORT_BASEDIR), "false",
            k(ConfigKey.ASSEMBLY_IGNORE_PERMISSIONS), "true",
            k(ConfigKey.ASSEMBLY_USER), "user",
            k(ConfigKey.NAME), "image",
            };
    }

    private String[] getTestData() {
        return new String[] {
            k(ConfigKey.ALIAS), "alias",
            k(ConfigKey.ASSEMBLY_DESCRIPTOR), "assembly.xml",
            k(ConfigKey.BIND) + ".1", "/foo",
            k(ConfigKey.BIND) + ".2", "/tmp:/tmp",
            k(ConfigKey.CAP_ADD) + ".1", "CAP",
            k(ConfigKey.CAP_DROP) + ".1", "CAP",
            k(ConfigKey.SECURITY_OPTS) + ".1", "seccomp=unconfined",
            k(ConfigKey.CMD), "command.sh",
            k(ConfigKey.DNS) + ".1", "8.8.8.8",
            k(ConfigKey.NET), "host",
            k(ConfigKey.DNS_SEARCH) + ".1", "example.com",
            k(ConfigKey.DOMAINNAME), "domain.com",
            k(ConfigKey.ENTRYPOINT), "entrypoint.sh",
            k(ConfigKey.ENV) + ".HOME", "/Users/roland",
            k(ConfigKey.ARGS) + ".PROXY", "http://proxy",
            k(ConfigKey.LABELS) + ".com.acme.label", "Hello\"World",
            k(ConfigKey.BUILD_OPTIONS) + ".shmsize", "2147483648",
            k(ConfigKey.ENV_PROPERTY_FILE), "/tmp/envProps.txt",
            k(ConfigKey.EXTRA_HOSTS) + ".1", "localhost:127.0.0.1",
            k(ConfigKey.FROM), "image",
            k(ConfigKey.FROM_EXT) + ".name", "image-ext",
            k(ConfigKey.FROM_EXT) + ".kind", "kind",
            k(ConfigKey.HOSTNAME), "subdomain",
            k(ConfigKey.LINKS) + ".1", "redis",
            k(ConfigKey.MAINTAINER), "fabric8io@redhat.com",
            k(ConfigKey.MEMORY), "1",
            k(ConfigKey.MEMORY_SWAP), "1",
            k(ConfigKey.NAME), "image",
            k(ConfigKey.PORT_PROPERTY_FILE), "/tmp/props.txt",
            k(ConfigKey.PORTS) + ".1", "8081:8080",
            k(ConfigKey.PRIVILEGED), "true",
            k(ConfigKey.REGISTRY), "registry",
            k(ConfigKey.RESTART_POLICY_NAME), "on-failure",
            k(ConfigKey.RESTART_POLICY_RETRY), "1",
            k(ConfigKey.USER), "tomcat",
            k(ConfigKey.ULIMITS)+".1", "memlock=10:10",
            k(ConfigKey.ULIMITS)+".2", "memlock=:-1",
            k(ConfigKey.ULIMITS)+".3", "memlock=1024:",
            k(ConfigKey.ULIMITS)+".4", "memlock=2048",
            k(ConfigKey.VOLUMES) + ".1", "/foo",
            k(ConfigKey.VOLUMES_FROM) + ".1", "from",
            k(ConfigKey.PRE_STOP), "pre_stop_command",
            k(ConfigKey.POST_START), "post_start_command",
            k(ConfigKey.WAIT_LOG), "pattern",
            k(ConfigKey.WAIT_TIME), "5",
            k(ConfigKey.WAIT_URL), "http://foo.com",
            k(ConfigKey.LOG_PREFIX), "SRV",
            k(ConfigKey.LOG_COLOR), "green",
            k(ConfigKey.LOG_ENABLED), "true",
            k(ConfigKey.LOG_DATE), "iso8601",
            k(ConfigKey.LOG_DRIVER_NAME), "json",
            k(ConfigKey.LOG_DRIVER_OPTS) + ".max-size", "1024",
            k(ConfigKey.LOG_DRIVER_OPTS) + ".max-file", "10",
            k(ConfigKey.WORKING_DIR), "foo",
            k(ConfigKey.TMPFS) + ".1", "/var/lib/mysql:10m"
        };
    }

    private String[] getSkipTestData(ConfigKey key, boolean value) {
        return new String[] {k(ConfigKey.NAME), "image", k(key), String.valueOf(value) };
    }

    private String k(ConfigKey from) {
        return from.asPropertyKey();
    }
    private void assertUlimitEquals(UlimitConfig expected, UlimitConfig actual){
    	assertEquals(expected.getName(), actual.getName());
        assertEquals(expected.getSoft(), actual.getSoft());
        assertEquals(expected.getHard(), actual.getHard());
    }
}
