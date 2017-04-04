package io.fabric8.maven.docker.assembly;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;

import io.fabric8.maven.docker.config.AssemblyConfiguration;
import io.fabric8.maven.docker.util.EnvUtil;
import io.fabric8.maven.docker.util.MojoParameters;
import java.net.URI;
import java.net.URISyntaxException;
import org.apache.maven.project.MavenProject;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class DockerAssemblyConfigurationSourceTest {

    private AssemblyConfiguration assemblyConfig;

    @Before
    public void setup() {
        // set 'ignorePermissions' to something other then default
        this.assemblyConfig = new AssemblyConfiguration.Builder()
                .descriptor("assembly.xml")
                .descriptorRef("project")
                .permissions("keep")
                .build();
    }

    @Test
    public void permissionMode() {
        try {
            new AssemblyConfiguration.Builder().permissions("blub").build();
        } catch (IllegalArgumentException exp) {
            assertTrue(exp.getMessage().contains("blub"));
        }

        AssemblyConfiguration config = new AssemblyConfiguration.Builder().ignorePermissions(false).permissions("ignore").build();
        assertTrue(config.isIgnorePermissions());;
    }

    @Test
    public void testCreateSourceAbsolute() throws Exception {
        testCreateSource(".", "/src/docker", "/output/docker");
    }

    @Test
    public void testCreateSourceRelative() throws Exception {
        testCreateSource(".","src/docker", "output/docker");
    }

    @Test
    public void testCreateSourceAbsoluteFromAbsoluteProjectBaseDir() throws Exception {
        testCreateSource("/projects/project", "/src/docker", "/output/docker");
    }

    @Test
    public void testCreateSourceRelativeFromAbsoluteProjectBaseDir() throws Exception {
        testCreateSource("/project/project", "src/docker", "output/docker");
    }

    @Test
    public void testCreateSourceAbsoluteFromLongRelativeProjectBaseDir() throws Exception {
        testCreateSource("./projects/project", "/src/docker", "/output/docker");
    }

    @Test
    public void testCreateSourceRelativeFromLongRelativeProjectBaseDir() throws Exception {
        testCreateSource("./projects/project", "src/docker", "output/docker");
    }

    @Test
    public void testOutputDirHasImage() {
        String image = "image";
        MojoParameters params = buildParameters(".", "src/docker", "output/docker");
        DockerAssemblyConfigurationSource source = new DockerAssemblyConfigurationSource(params,
                new BuildDirs(image, params),assemblyConfig);

        assertTrue(containsDir(image, source.getOutputDirectory()));
        assertTrue(containsDir(image, source.getWorkingDirectory()));
        assertTrue(containsDir(image, source.getTemporaryRootDirectory()));
    }

    private MojoParameters buildParameters(String projectDir, String sourceDir, String outputDir) {
        MavenProject mavenProject = new MavenProject();
        mavenProject.setFile(new File(projectDir, "pom.xml"));
        return new MojoParameters(null, mavenProject, null, null, null, null, sourceDir.replace("/", File.separator), outputDir.replace("/", File.separator));
    }

    @Test
    public void testEmptyAssemblyConfig() {
        DockerAssemblyConfigurationSource source = new DockerAssemblyConfigurationSource(
               new MojoParameters(null, null, null, null, null, null, "/src/docker", "/output/docker"),
               null,null
        );
        assertEquals(0,source.getDescriptors().length);
    }

    private void testCreateSource(String projectDir, String sourceDir, String outputDir) throws URISyntaxException {
        MojoParameters params = buildParameters(projectDir, sourceDir, outputDir);

        DockerAssemblyConfigurationSource source =
                new DockerAssemblyConfigurationSource(params, new BuildDirs("image", params), assemblyConfig);

        String[] descriptors = source.getDescriptors();
        String[] descriptorRefs = source.getDescriptorReferences();

        assertEquals("count of descriptors", 1, descriptors.length);
        assertEquals("directory of assembly", EnvUtil.prepareAbsoluteSourceDirPath(params, "assembly.xml").getAbsolutePath(), descriptors[0]);

        assertEquals("count of descriptors references", 1, descriptorRefs.length);
        assertEquals("reference must be project", "project", descriptorRefs[0]);

        assertFalse("we must not ignore permissions when creating the archive", source.isIgnorePermissions());

        final File outputFile = new File(params.getOutputDirectory());
        if (outputFile.isAbsolute()) {
            // if it was an absolute path at the beginning in MojoParameters then it should stay an absolute path with same root after all
            String absolutePath = outputFile.getAbsolutePath();
            assertStartsWithDir(absolutePath, source.getOutputDirectory());
            assertStartsWithDir(absolutePath, source.getWorkingDirectory());
            assertStartsWithDir(absolutePath, source.getTemporaryRootDirectory());
        } else {
            // if not - then at some level each plugin directory must be equal to outputDir
            assertChildOfDir(projectDir + File.separator + params.getOutputDirectory(), source.getOutputDirectory());
            assertChildOfDir(projectDir + File.separator + params.getOutputDirectory(), source.getWorkingDirectory());
            assertChildOfDir(projectDir + File.separator + params.getOutputDirectory(), source.getTemporaryRootDirectory());
        }
    }

    private boolean containsDir(String outputDir, File path) {
        return path.toString().contains(outputDir + File.separator);
    }

    private void assertStartsWithDir(String outputDir, File path) {
        String expectedStartsWith = outputDir + File.separator;
        int length = expectedStartsWith.length();
        assertEquals(expectedStartsWith, path.toString().substring(0, length));
    }

    private void assertChildOfDir(String rootPath, File childCandidatePath) throws URISyntaxException {
        File rootDir = new File(new URI(rootPath).normalize().getPath()).getAbsoluteFile();
        File childCandidateDir = new File(new URI(childCandidatePath.getAbsolutePath()).normalize().getPath()).getAbsoluteFile();

        do {

            if (childCandidateDir == null && !childCandidateDir.equals(rootDir)) {
                fail(childCandidatePath + " is not a child of " + rootPath);
            }

            if (rootDir.equals(childCandidateDir)) {
                // success
                return;
            }

            childCandidateDir = childCandidateDir.getParentFile();
        } while (true);
    }
}
