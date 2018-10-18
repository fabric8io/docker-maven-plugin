package io.fabric8.maven.docker.build.maven.assembly;

import java.io.File;
import java.util.Arrays;

import io.fabric8.maven.docker.build.maven.MavenBuildContext;
import io.fabric8.maven.docker.build.maven.assembly.BuildDirs;
import io.fabric8.maven.docker.build.maven.assembly.DockerAssemblyConfigurationSource;
import io.fabric8.maven.docker.config.AssemblyConfiguration;
import io.fabric8.maven.docker.util.EnvUtil;
import org.apache.maven.project.MavenProject;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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

    @SuppressWarnings("deprecation")
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
    public void testCreateSourceAbsolute() {
        testCreateSource(buildBuildContetxt(".", "/src/docker".replace("/", File.separator), "/output/docker".replace("/", File.separator)));
    }

    @Test
    public void testCreateSourceRelative() {
        testCreateSource(buildBuildContetxt(".","src/docker".replace("/", File.separator), "output/docker".replace("/", File.separator)));
    }

    @Test
    public void testOutputDirHasImage() {
        String image = "image";
        MavenBuildContext context = buildBuildContetxt(".", "src/docker", "output/docker");
        DockerAssemblyConfigurationSource source = new DockerAssemblyConfigurationSource(context,
                                                                                         new BuildDirs(image, context), assemblyConfig);

        assertTrue(containsDir(image, source.getOutputDirectory()));
        assertTrue(containsDir(image, source.getWorkingDirectory()));
        assertTrue(containsDir(image, source.getTemporaryRootDirectory()));
    }

    private MavenBuildContext buildBuildContetxt(String projectDir, String sourceDir, String outputDir) {
        MavenProject mavenProject = new MavenProject();
        mavenProject.setFile(new File(projectDir));
        return new MavenBuildContext.Builder()
            .project(mavenProject)
            .sourceDirectory(sourceDir)
            .outputDirectory(outputDir)
            .build();
    }

    @Test
    public void testEmptyAssemblyConfig() {
        MavenBuildContext buildContext = new MavenBuildContext.Builder()
            .sourceDirectory("/src/docker")
            .outputDirectory("/output/docker")
            .build();
        DockerAssemblyConfigurationSource source = new DockerAssemblyConfigurationSource(buildContext,null,null);
        assertEquals(0,source.getDescriptors().length);
    }

    private void testCreateSource(MavenBuildContext params) {
        DockerAssemblyConfigurationSource source =
                new DockerAssemblyConfigurationSource(params, new BuildDirs("image", params), assemblyConfig);

        String[] descriptors = source.getDescriptors();
        String[] descriptorRefs = source.getDescriptorReferences();

        assertEquals("count of descriptors", 1, descriptors.length);
        Assert.assertEquals("directory of assembly", EnvUtil.prepareAbsoluteSourceDirPath(params, "assembly.xml").getAbsolutePath(), descriptors[0]);

        assertEquals("count of descriptors references", 1, descriptorRefs.length);
        assertEquals("reference must be project", "project", descriptorRefs[0]);

        assertFalse("we must not ignore permissions when creating the archive", source.isIgnorePermissions());

        String outputDir = params.getOutputDirectory();

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
        assertEquals(expectedStartsWith, path.toString().substring(0, length));
    }

    @Test
    public void testReactorProjects() {
    	MavenProject reactorProject1 = new MavenProject();
    	reactorProject1.setFile(new File("../reactor-1"));

        MavenProject reactorProject2 = new MavenProject();
        reactorProject2.setFile(new File("../reactor-2"));

        MavenBuildContext buildContext = new MavenBuildContext.Builder()
            .sourceDirectory("/src/docker")
            .outputDirectory("/output/docker")
            .reactorProjects(Arrays.asList(reactorProject1, reactorProject2))
            .build();
        DockerAssemblyConfigurationSource source = new DockerAssemblyConfigurationSource(buildContext,null,null);
        assertEquals(2,source.getReactorProjects().size());
    }
}
