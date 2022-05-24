package io.fabric8.maven.docker.config.handler.compose;

import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.config.NetworkConfig;
import io.fabric8.maven.docker.config.RestartPolicy;
import io.fabric8.maven.docker.config.RunImageConfiguration;
import io.fabric8.maven.docker.config.RunVolumeConfiguration;
import io.fabric8.maven.docker.config.handler.ExternalConfigHandlerException;
import org.apache.commons.io.FileUtils;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.filtering.MavenFilteringException;
import org.apache.maven.shared.filtering.MavenReaderFilter;
import org.apache.maven.shared.filtering.MavenReaderFilterRequest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author roland
 * @since 28.08.17
 */
@ExtendWith(MockitoExtension.class)
class DockerComposeConfigHandlerTest {

    @Mock
    ImageConfiguration unresolved;

    @Mock
    MavenProject project;

    @Mock
    MavenSession session;

    @Mock
    MavenReaderFilter readerFilter;

    private DockerComposeConfigHandler handler;

    @BeforeEach
    void setUp(@TempDir Path tmpDir) {
        handler = new DockerComposeConfigHandler();
        handler.readerFilter = readerFilter;
        Mockito.lenient().doReturn(tmpDir.toFile()).when(project).getBasedir();
    }

    @Test
    void simple() throws IOException, MavenFilteringException {
        setupComposeExpectations("docker-compose.yml");
        List<ImageConfiguration> configs = handler.resolve(unresolved, project, session);
        Assertions.assertEquals(1, configs.size());
        validateRunConfiguration(configs.get(0).getRunConfiguration());
    }

    @Test
    void networkAliases() throws IOException, MavenFilteringException {
        setupComposeExpectations("docker-compose-network-aliases.yml");
        List<ImageConfiguration> configs = handler.resolve(unresolved, project, session);

        // Service 1 has 1 network (network1) with 2 aliases (alias1, alias2)
        NetworkConfig netSvc = configs.get(0).getRunConfiguration().getNetworkingConfig();
        Assertions.assertEquals("network1", netSvc.getName());
        Assertions.assertEquals(2, netSvc.getAliases().size());
        Assertions.assertEquals("alias1", netSvc.getAliases().get(0));
        Assertions.assertEquals("alias2", netSvc.getAliases().get(1));

        // Service 2 has 1 network (network1) with no aliases
        netSvc = configs.get(1).getRunConfiguration().getNetworkingConfig();
        Assertions.assertEquals("network1", netSvc.getName());
        Assertions.assertEquals(0, netSvc.getAliases().size());

        // Service 3 has 1 network (network1) with 1 alias (alias1)
        netSvc = configs.get(2).getRunConfiguration().getNetworkingConfig();
        Assertions.assertEquals("network1", netSvc.getName());
        Assertions.assertEquals(1, netSvc.getAliases().size());
        Assertions.assertEquals("alias1", netSvc.getAliases().get(0));
    }

    @Test
    void positiveVersionTest() throws IOException, MavenFilteringException {
        for (String composeFile : new String[] { "version/compose-version-2.yml", "version/compose-version-2x.yml" }) {
            setupComposeExpectations(composeFile);
            Assertions.assertNotNull(handler.resolve(unresolved, project, session));
        }

    }

    @Test
    void negativeVersionTest() throws IOException, MavenFilteringException {
        for (String composeFile : new String[] { "version/compose-wrong-version.yml", "version/compose-no-version.yml" }) {
            setupComposeExpectations(composeFile);
            ExternalConfigHandlerException exp = Assertions.assertThrows(ExternalConfigHandlerException.class,
                () -> handler.resolve(unresolved, project, session));
            Assertions.assertTrue(exp.getMessage().contains(("2.x")));
        }

    }

    private void setupComposeExpectations(final String file) throws IOException, MavenFilteringException {
        final File input = getAsFile("/compose/" + file);

        Mockito.doReturn(new HashMap<String, String>() {{
            put("composeFile", input.getAbsolutePath());
            // provide a base directory that actually exists, so that relative paths referenced by the
            // docker-compose.yaml file can be resolved
            // (note: this is different than the directory returned by 'input.getParent()')
            URL baseResource = this.getClass().getResource("/");
            String baseDir = baseResource.getFile();
            Assertions.assertNotNull( baseDir,"Classpath resource '/' does not have a File: '" + baseResource);
            Assertions.assertTrue(new File(baseDir).exists(), "Classpath resource '/' does not resolve to a File: '" + new File(baseDir) + "' does not exist.");
            put("basedir", baseDir);
        }}).when(unresolved).getExternalConfig();

        Mockito.doReturn(new FileReader(input)).when(readerFilter).filter(Mockito.any(MavenReaderFilterRequest.class));
     }

    private File getAsFile(String resource) throws IOException {
        File tempFile = File.createTempFile("compose", ".yml");
        InputStream is = getClass().getResourceAsStream(resource);
        FileUtils.copyInputStreamToFile(is, tempFile);
        return tempFile;
    }

    void validateRunConfiguration(RunImageConfiguration runConfig) {

        validateVolumeConfig(runConfig.getVolumeConfiguration());

        Assertions.assertEquals(a("CAP"), runConfig.getCapAdd());
        Assertions.assertEquals(a("CAP"), runConfig.getCapDrop());
        Assertions.assertEquals(Collections.singletonMap("key", "value"), runConfig.getSysctls());
        Assertions.assertEquals("command.sh", runConfig.getCmd().getShell());
        Assertions.assertEquals(a("8.8.8.8"), runConfig.getDns());
        Assertions.assertEquals(a("example.com"), runConfig.getDnsSearch());
        Assertions.assertEquals("domain.com", runConfig.getDomainname());
        Assertions.assertEquals("entrypoint.sh", runConfig.getEntrypoint().getShell());
        Assertions.assertEquals(a("localhost:127.0.0.1"), runConfig.getExtraHosts());
        Assertions.assertEquals("subdomain", runConfig.getHostname());
        Assertions.assertEquals(a("redis", "link1"), runConfig.getLinks());
        Assertions.assertEquals((Long) 1L, runConfig.getMemory());
        Assertions.assertEquals((Long) 1L, runConfig.getMemorySwap());
        Assertions.assertEquals("0,1", runConfig.getCpuSet());
        Assertions.assertEquals((Long) 1000000000L, runConfig.getCpus());
        Assertions.assertEquals("default", runConfig.getIsolation());
        Assertions.assertEquals((Long) 1L, runConfig.getCpuShares());
        Assertions.assertNull(runConfig.getEnvPropertyFile());

        Assertions.assertNull(runConfig.getPortPropertyFile());
        Assertions.assertEquals(a("8081:8080"), runConfig.getPorts());
        Assertions.assertEquals(true, runConfig.getPrivileged());
        Assertions.assertEquals("tomcat", runConfig.getUser());
        Assertions.assertEquals(a("from"), runConfig.getVolumeConfiguration().getFrom());
        Assertions.assertEquals("foo", runConfig.getWorkingDir());

        validateEnv(runConfig.getEnv());

        // not sure it's worth it to implement 'equals/hashcode' for these
        RestartPolicy policy = runConfig.getRestartPolicy();
        Assertions.assertEquals("on-failure", policy.getName());
        Assertions.assertEquals(1, policy.getRetry());
    }

    /**
     * Validates the {@link RunVolumeConfiguration} by asserting that:
     * <ul>
     *     <li>absolute host paths remain absolute</li>
     *     <li>access controls are preserved</li>
     *     <li>relative host paths are resolved to absolute paths correctly</li>
     * </ul>
     *
     * @param toValidate the {@code RunVolumeConfiguration} being validated
     */
    void validateVolumeConfig(RunVolumeConfiguration toValidate) {
        final int expectedBindCnt = 4;
        final List<String> binds = toValidate.getBind();
        Assertions.assertEquals(expectedBindCnt, binds.size(), "Expected " + expectedBindCnt + " bind statements");

        Assertions.assertEquals(a("/foo", "/tmp:/tmp:rw", "namedvolume:/volume:ro"), binds.subList(0, expectedBindCnt - 1));

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
     * @param bindString a volume binding string that contains a host portion that is expected to exist on the local
     * system
     */
    private void assertHostBindingExists(String bindString) {
        //        System.err.println(">>>> " + bindString);

        // Extract the host-portion of the volume binding string, accounting for windows platform paths and unix style
        // paths.  For example:
        // C:\Users\foo\Documents\workspaces\docker-maven-plugin\target\test-classes\compose\version:/tmp/version
        // and
        // /Users/foo/workspaces/docker-maven-plugin/target/test-classes/compose/version:/tmp/version

        File file;
        if (bindString.indexOf(":") > 1) {
            // a unix-style path
            file = new File(bindString.substring(0, bindString.indexOf(":")));
        } else {
            // a windows-style path with a drive letter
            file = new File(bindString.substring(0, bindString.indexOf(":", 2)));
        }
        Assertions.assertTrue(file.exists(), "The file '" + file + "' parsed from the volume binding string '" + bindString + "' does not exist!");
    }

    protected void validateEnv(Map<String, String> env) {
        Assertions.assertEquals(2, env.size());
        Assertions.assertEquals("name", env.get("NAME"));
        Assertions.assertEquals("true", env.get("BOOL"));
    }

    protected List<String> a(String... args) {
        return Arrays.asList(args);
    }

}
