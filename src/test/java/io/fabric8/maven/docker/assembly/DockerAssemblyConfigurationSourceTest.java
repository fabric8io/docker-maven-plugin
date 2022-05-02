package io.fabric8.maven.docker.assembly;

import io.fabric8.maven.docker.config.AssemblyConfiguration;
import io.fabric8.maven.docker.util.EnvUtil;
import io.fabric8.maven.docker.util.MojoParameters;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Arrays;

class DockerAssemblyConfigurationSourceTest {

    private AssemblyConfiguration assemblyConfig;

    @BeforeEach
    void setup() {
        // set 'ignorePermissions' to something other then default
        this.assemblyConfig = new AssemblyConfiguration.Builder()
            .descriptor("assembly.xml")
            .descriptorRef("project")
            .permissions("keep")
            .build();
    }

    @SuppressWarnings("deprecation")
    @Test
    void permissionMode() {
        AssemblyConfiguration.Builder builder = new AssemblyConfiguration.Builder();
        IllegalArgumentException exp = Assertions.assertThrows(IllegalArgumentException.class, () -> builder.permissions("blub"));
        Assertions.assertTrue(exp.getMessage().contains("blub"));

        AssemblyConfiguration config = new AssemblyConfiguration.Builder().ignorePermissions(false).permissions("ignore").build();
        Assertions.assertTrue(config.isIgnorePermissions());
    }

    @Test
    void testCreateSourceAbsolute() {
        testCreateSource(buildParameters(".", "/src/docker".replace("/", File.separator), "/output/docker".replace("/", File.separator)));
    }

    @Test
    void testCreateSourceRelative() {
        testCreateSource(buildParameters(".", "src/docker".replace("/", File.separator), "output/docker".replace("/", File.separator)));
    }

    @Test
    void testOutputDirHasImage() {
        String image = "image";
        MojoParameters params = buildParameters(".", "src/docker", "output/docker");
        DockerAssemblyConfigurationSource source = new DockerAssemblyConfigurationSource(params,
            new BuildDirs(image, params), assemblyConfig);

        Assertions.assertTrue(containsDir(image, source.getOutputDirectory()));
        Assertions.assertTrue(containsDir(image, source.getWorkingDirectory()));
        Assertions.assertTrue(containsDir(image, source.getTemporaryRootDirectory()));
    }

    private MojoParameters buildParameters(String projectDir, String sourceDir, String outputDir) {
        MavenProject mavenProject = new MavenProject();
        mavenProject.setFile(new File(projectDir).getAbsoluteFile());
        return new MojoParameters(null, mavenProject, null, null, null, null, sourceDir, outputDir, null);
    }

    @Test
    void testEmptyAssemblyConfig() {
        DockerAssemblyConfigurationSource source = new DockerAssemblyConfigurationSource(
            new MojoParameters(null, null, null, null, null, null, "/src/docker", "/output/docker", null),
            null, null
        );
        Assertions.assertEquals(0, source.getDescriptors().length);
    }

    private void testCreateSource(MojoParameters params) {
        DockerAssemblyConfigurationSource source =
            new DockerAssemblyConfigurationSource(params, new BuildDirs("image", params), assemblyConfig);

        String[] descriptors = source.getDescriptors();
        String[] descriptorRefs = source.getDescriptorReferences();

        Assertions.assertEquals(1, descriptors.length);
        Assertions.assertEquals(EnvUtil.prepareAbsoluteSourceDirPath(params, "assembly.xml").getAbsolutePath(), descriptors[0]);

        Assertions.assertEquals(1, descriptorRefs.length);
        Assertions.assertEquals("project", descriptorRefs[0]);

        Assertions.assertFalse(source.isIgnorePermissions(), "we must not ignore permissions when creating the archive");

        String outputDir = new File(params.getOutputDirectory()).getAbsolutePath();

        assertStartsWithDir(outputDir, source.getOutputDirectory());
        assertStartsWithDir(outputDir, source.getWorkingDirectory());
        assertStartsWithDir(outputDir, source.getTemporaryRootDirectory());
    }

    private boolean containsDir(String outputDir, File path) {
        return path.toString().contains(outputDir + File.separator);
    }

    private void assertStartsWithDir(String outputDir, File path) {
        String expectedStartsWith = outputDir + File.separator;
        int length = expectedStartsWith.length();
        Assertions.assertEquals(expectedStartsWith, path.toString().substring(0, length));
    }

    @Test
    void testReactorProjects() {
        MavenProject reactorProject1 = new MavenProject();
        reactorProject1.setFile(new File("../reactor-1"));

        MavenProject reactorProject2 = new MavenProject();
        reactorProject2.setFile(new File("../reactor-2"));

        DockerAssemblyConfigurationSource source = new DockerAssemblyConfigurationSource(
            new MojoParameters(null, null, null, null, null, null, "/src/docker", "/output/docker", Arrays.asList(reactorProject1, reactorProject2)),
            null, null
        );
        Assertions.assertEquals(2, source.getReactorProjects().size());
    }
}
