package io.fabric8.maven.docker.config.handler.compose;

import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.config.NetworkConfig;
import io.fabric8.maven.docker.config.RestartPolicy;
import io.fabric8.maven.docker.config.RunImageConfiguration;
import io.fabric8.maven.docker.config.RunVolumeConfiguration;
import io.fabric8.maven.docker.config.handler.ExternalConfigHandlerException;
import mockit.Expectations;
import mockit.Injectable;
import mockit.Mocked;
import org.apache.commons.io.FileUtils;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.filtering.MavenFilteringException;
import org.apache.maven.shared.filtering.MavenReaderFilter;
import org.apache.maven.shared.filtering.MavenReaderFilterRequest;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author roland
 * @since 28.08.17
 */
public class DockerComposeConfigHandlerTest {

    @Injectable
    ImageConfiguration unresolved;

    @Mocked
    MavenProject project;

    @Mocked
    MavenSession session;

    @Mocked
    MavenReaderFilter readerFilter;

    private DockerComposeConfigHandler handler;

    @Before
    public void setUp() throws Exception {
        handler = new DockerComposeConfigHandler();
        handler.readerFilter = readerFilter;
    }


    @Test
    public void simple() throws IOException, MavenFilteringException {
        setupComposeExpectations("docker-compose.yml");
        List<ImageConfiguration> configs = handler.resolve(unresolved, project, session);
        assertEquals(1, configs.size());
        validateRunConfiguration(configs.get(0).getRunConfiguration());
    }


	@Test
	public void networkAliases() throws IOException, MavenFilteringException {
        setupComposeExpectations("docker-compose-network-aliases.yml");
        List<ImageConfiguration> configs = handler.resolve(unresolved, project, session);
        
        // Service 1 has 1 network (network1) with 2 aliases (alias1, alias2)
        NetworkConfig netSvc = configs.get(0).getRunConfiguration().getNetworkingConfig();
        assertEquals("network1", netSvc.getName());
        assertEquals(2, netSvc.getAliases().size());
        assertEquals("alias1", netSvc.getAliases().get(0));
        assertEquals("alias2", netSvc.getAliases().get(1));
  
        // Service 2 has 1 network (network1) with no aliases
        netSvc = configs.get(1).getRunConfiguration().getNetworkingConfig();
        assertEquals("network1", netSvc.getName());
        assertEquals(0, netSvc.getAliases().size());

        // Service 3 has 1 network (network1) with 1 aliase (alias1)
        netSvc = configs.get(2).getRunConfiguration().getNetworkingConfig();
        assertEquals("network1", netSvc.getName());
        assertEquals(1, netSvc.getAliases().size());
        assertEquals("alias1", netSvc.getAliases().get(0));
}
	
    @Test
    public void positiveVersionTest() throws IOException, MavenFilteringException {
        for (String composeFile : new String[] { "version/compose-version-2.yml", "version/compose-version-2x.yml"} ) {
            setupComposeExpectations(composeFile);
            assertNotNull(handler.resolve(unresolved, project, session));
        }

    }
	
    @Test
    public void negativeVersionTest() throws IOException, MavenFilteringException {
        for (String composeFile : new String[] { "version/compose-wrong-version.yml", "version/compose-no-version.yml"} ) {
            try {
                setupComposeExpectations(composeFile);
                handler.resolve(unresolved, project, session);
                fail();
            } catch (ExternalConfigHandlerException exp) {
                assertTrue(exp.getMessage().contains(("2.x")));
            }
        }

    }

    private void setupComposeExpectations(final String file) throws IOException, MavenFilteringException {
        new Expectations() {{
            final File input = getAsFile("/compose/" + file);

            unresolved.getExternalConfig();
            result = new HashMap<String,String>() {{
                put("composeFile", input.getAbsolutePath());
                // provide a base directory that actually exists, so that relative paths referenced by the
                // docker-compose.yaml file can be resolved
                // (note: this is different than the directory returned by 'input.getParent()')
                URL baseResource = this.getClass().getResource("/");
                String baseDir = baseResource.getFile();
                assertNotNull("Classpath resource '/' does not have a File: '" + baseResource, baseDir);
                assertTrue("Classpath resource '/' does not resolve to a File: '" + new File(baseDir) + "' does not exist.", new File(baseDir).exists());
                put("basedir", baseDir);
            }};

            readerFilter.filter((MavenReaderFilterRequest) any);
            result = new FileReader(input);
        }};
    }

    private File getAsFile(String resource) throws IOException {
        File tempFile = File.createTempFile("compose",".yml");
        InputStream is = getClass().getResourceAsStream(resource);
        FileUtils.copyInputStreamToFile(is,tempFile);
        return tempFile;
    }


     void validateRunConfiguration(RunImageConfiguration runConfig) {

        validateVolumeConfig(runConfig.getVolumeConfiguration());

        assertEquals(a("CAP"), runConfig.getCapAdd());
        assertEquals(a("CAP"), runConfig.getCapDrop());
        assertEquals("command.sh", runConfig.getCmd().getShell());
        assertEquals(a("8.8.8.8"), runConfig.getDns());
        assertEquals(a("example.com"), runConfig.getDnsSearch());
        assertEquals("domain.com", runConfig.getDomainname());
        assertEquals("entrypoint.sh", runConfig.getEntrypoint().getShell());
        assertEquals(a("localhost:127.0.0.1"), runConfig.getExtraHosts());
        assertEquals("subdomain", runConfig.getHostname());
        assertEquals(a("redis","link1"), runConfig.getLinks());
        assertEquals((Long) 1L, runConfig.getMemory());
        assertEquals((Long) 1L, runConfig.getMemorySwap());
        assertEquals("0,1", runConfig.getCpuSet());
        assertEquals((Long)1000000000L, runConfig.getCpus());
        assertEquals((Long) 1L, runConfig.getCpuShares());
        assertEquals(null,runConfig.getEnvPropertyFile());

        assertEquals(null, runConfig.getPortPropertyFile());
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
    }

    /**
     * Validates the {@link RunVolumeConfiguration} by asserting that:
     * <ul>
     *     <li>absolute host paths remain absolute</li>
     *     <li>access controls are preserved</li>
     *     <li>relative host paths are resolved to absolute paths correctly</li>
     * </ul>
     * @param toValidate the {@code RunVolumeConfiguration} being validated
     */
    void validateVolumeConfig(RunVolumeConfiguration toValidate) {
        final int expectedBindCnt = 4;
        final List<String> binds = toValidate.getBind();
        assertEquals("Expected " + expectedBindCnt + " bind statements", expectedBindCnt, binds.size());

        assertEquals(a("/foo", "/tmp:/tmp:rw", "namedvolume:/volume:ro"), binds.subList(0, expectedBindCnt - 1));

        // The docker-compose.yml used for testing contains a volume binding string that uses relative paths in the
        // host portion.  Insure that the relative portion has been resolved properly.
        String relativeBindString = binds.get(expectedBindCnt - 1);
        assertHostBindingExists(relativeBindString);
    }

    /**
     * Parses the supplied binding string for the host portion, and insures the host portion actually exists on the
     * filesystem.  Note this method is designed to accommodate both Windows-style and *nix-style absolute paths.
     * <p>
     * The {@code docker-compose.yml} used for testing contains volume binding strings which are <em>relative</em>.
     * When the {@link RunVolumeConfiguration} is built, relative paths in the host portion of the binding string are
     * resolved to absolute paths.  This method expects a binding string that has already had its relative paths
     * <em>resolved</em> to absolute paths.  It parses the host portion of the binding string, and asserts that the path
     * exists on the system.
     * </p>
     *
     *
     * @param bindString a volume binding string that contains a host portion that is expected to exist on the local
     *                   system
     */
    private void assertHostBindingExists(String bindString) {
//        System.err.println(">>>> " + bindString);

        // Extract the host-portion of the volume binding string, accounting for windows platform paths and unix style
        // paths.  For example:
        // C:\Users\foo\Documents\workspaces\docker-maven-plugin\target\test-classes\compose\version:/tmp/version
        // and
        // /Users/foo/workspaces/docker-maven-plugin/target/test-classes/compose/version:/tmp/version

        File file = null;
        if (bindString.indexOf(":") > 1) {
            // a unix-style path
            file = new File(bindString.substring(0, bindString.indexOf(":")));
        } else {
            // a windows-style path with a drive letter
            file = new File(bindString.substring(0, bindString.indexOf(":", 2)));
        }
        assertTrue("The file '" + file + "' parsed from the volume binding string '" + bindString + "' does not exist!", file.exists());
    }

    protected void validateEnv(Map<String, String> env) {
        assertEquals(2, env.size());
        assertEquals("name", env.get("NAME"));
        assertEquals("true", env.get("BOOL"));
    }

    protected List<String> a(String ... args) {
        return Arrays.asList(args);
    }

}
