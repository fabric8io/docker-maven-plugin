package io.fabric8.maven.docker.assembly;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import io.fabric8.maven.docker.config.Arguments;
import io.fabric8.maven.docker.config.AssemblyConfiguration;
import io.fabric8.maven.docker.config.AssemblyMode;
import io.fabric8.maven.docker.config.BuildImageConfiguration;
import io.fabric8.maven.docker.util.AnsiLogger;
import io.fabric8.maven.docker.util.DockerFileUtil;
import io.fabric8.maven.docker.util.Logger;
import io.fabric8.maven.docker.util.MojoParameters;
import mockit.Expectations;
import mockit.Injectable;
import mockit.Mock;
import mockit.Mocked;
import mockit.Tested;
import mockit.Verifications;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.MavenArtifactRepository;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.assembly.AssemblerConfigurationSource;
import org.apache.maven.plugins.assembly.InvalidAssemblerConfigurationException;
import org.apache.maven.plugins.assembly.archive.ArchiveCreationException;
import org.apache.maven.plugins.assembly.archive.AssemblyArchiver;
import org.apache.maven.plugins.assembly.format.AssemblyFormattingException;
import org.apache.maven.plugins.assembly.io.AssemblyReadException;
import org.apache.maven.plugins.assembly.io.AssemblyReader;
import org.apache.maven.plugins.assembly.model.Assembly;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.apache.maven.plugins.assembly.model.FileItem;
import org.apache.maven.project.MavenProject;
import org.apache.maven.settings.Settings;
import org.codehaus.plexus.archiver.ArchiveEntry;
import org.codehaus.plexus.archiver.ArchivedFileSet;
import org.codehaus.plexus.archiver.FileSet;
import org.codehaus.plexus.archiver.ResourceIterator;
import org.codehaus.plexus.archiver.manager.ArchiverManager;
import org.codehaus.plexus.archiver.manager.NoSuchArchiverException;
import org.codehaus.plexus.archiver.tar.TarArchiver;
import org.codehaus.plexus.components.io.resources.PlexusIoResource;
import org.codehaus.plexus.interpolation.fixed.FixedStringSearchInterpolator;
import org.codehaus.plexus.util.ReflectionUtils;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.*;

public class DockerAssemblyManagerTest {

    @Tested
    private DockerAssemblyManager assemblyManager;

    @Injectable
    private AssemblyArchiver assemblyArchiver;

    @Injectable
    private AssemblyReader assemblyReader;

    @Injectable
    private ArchiverManager archiverManager;

    @Injectable
    private TrackArchiverCollection trackArchivers;

    @Test
    public void testNoAssembly() {
        BuildImageConfiguration buildConfig = new BuildImageConfiguration();
        List<AssemblyConfiguration> assemblyConfig = buildConfig.getAssemblyConfigurations();

        String content =
            assemblyManager.createDockerFileBuilder(
                buildConfig, assemblyConfig).content();

        assertFalse(content.contains("COPY"));
        assertFalse(content.contains("VOLUME"));
    }

    @Test
    public void testShellIsSet() {
        BuildImageConfiguration buildConfig =
            new BuildImageConfiguration.Builder().shell(
                new Arguments.Builder().withShell("/bin/sh echo hello").build())
                                                 .build();

        DockerFileBuilder builder =
            assemblyManager.createDockerFileBuilder(buildConfig, buildConfig.getAssemblyConfigurations());
        String content = builder.content();

        assertTrue(content.contains("SHELL [\"/bin/sh\",\"echo\",\"hello\"]"));
    }

    @Test
    public void assemblyFiles(@Injectable final MojoParameters mojoParams,
                              @Injectable final MavenProject project,
                              @Injectable final Assembly assembly) throws AssemblyFormattingException, ArchiveCreationException, InvalidAssemblerConfigurationException, MojoExecutionException, AssemblyReadException, IllegalAccessException {

        ReflectionUtils.setVariableValueInObject(assemblyManager, "trackArchivers", trackArchivers);

        new Expectations() {{
            mojoParams.getOutputDirectory();
            result = "target/";

            mojoParams.getProject();
            project.getBasedir();
            result = ".";

            assemblyReader.readAssemblies((AssemblerConfigurationSource) any);
            result = Arrays.asList(assembly);

        }};

        BuildImageConfiguration buildConfig = createBuildConfig();

        assemblyManager.getAssemblyFiles("testImage", buildConfig.getAssemblyConfigurations().get(0), mojoParams, new AnsiLogger(new SystemStreamLog(),true,"build"));
    }

    @Test
    public void multipleAssemblyFiles(@Injectable final MojoParameters mojoParams,
                              @Injectable final MavenProject project,
                              @Injectable final Assembly assembly1,
                              @Injectable final Assembly assembly2) throws AssemblyFormattingException, ArchiveCreationException, InvalidAssemblerConfigurationException, MojoExecutionException, AssemblyReadException, IllegalAccessException {

        ReflectionUtils.setVariableValueInObject(assemblyManager, "trackArchivers", trackArchivers);

        new Expectations() {{
            mojoParams.getOutputDirectory();
            result = "target/"; times = 2;

            mojoParams.getProject();
            project.getBasedir();
            result = ".";

            assemblyReader.readAssemblies((AssemblerConfigurationSource) any);
            result = Collections.singletonList(assembly1);

            assemblyReader.readAssemblies((AssemblerConfigurationSource) any);
            result = Collections.singletonList(assembly2);

        }};

        BuildImageConfiguration buildConfig = createBuildConfigMultiAssembly();

        AssemblyFiles files = assemblyManager.getAssemblyFiles("testImage", buildConfig.getAssemblyConfigurations().get(0), mojoParams, new AnsiLogger(new SystemStreamLog(),true,"build"));
        assertNotNull(files);
        files = assemblyManager.getAssemblyFiles("testImage", buildConfig.getAssemblyConfigurations().get(1), mojoParams, new AnsiLogger(new SystemStreamLog(),true,"build"));
        assertNotNull(files);
    }

    @Test
    public void testCopyValidVerifyGivenDockerfile(@Injectable final Logger logger) throws IOException {
        BuildImageConfiguration buildConfig = createBuildConfig();

        assemblyManager.verifyGivenDockerfile(
            new File(getClass().getResource("/docker/Dockerfile_assembly_verify_copy_valid.test").getPath()),
            buildConfig,
            createInterpolator(buildConfig),
            logger);

        new Verifications() {{
            logger.warn(anyString, (Object []) any); times = 0;
        }};

    }

    @Test
    public void testCopyInvalidVerifyGivenDockerfile(@Injectable final Logger logger) throws IOException {
        BuildImageConfiguration buildConfig = createBuildConfig();

        assemblyManager.verifyGivenDockerfile(
            new File(getClass().getResource("/docker/Dockerfile_assembly_verify_copy_invalid.test").getPath()),
            buildConfig, createInterpolator(buildConfig),
            logger);

        new Verifications() {{
            logger.warn(anyString, (Object []) any); times = 1;
        }};

    }

    @Test
    public void testCopyChownValidVerifyGivenDockerfile(@Injectable final Logger logger) throws IOException {
        BuildImageConfiguration buildConfig = createBuildConfig();

        assemblyManager.verifyGivenDockerfile(
            new File(getClass().getResource("/docker/Dockerfile_assembly_verify_copy_chown_valid.test").getPath()),
            buildConfig,
            createInterpolator(buildConfig),
            logger);

        new Verifications() {{
            logger.warn(anyString, (Object []) any); times = 0;
        }};

    }

    @Test
    public void testMultipleCopyValidVerifyGivenDockerfile(@Injectable final Logger logger) throws IOException {
        BuildImageConfiguration buildConfig = createBuildConfigMultiAssembly();

        assemblyManager.verifyGivenDockerfile(
                new File(getClass().getResource("/docker/Dockerfile_assembly_verify_multi_copy_valid.test").getPath()),
                buildConfig,
                createInterpolator(buildConfig),
                logger);

        new Verifications() {{
            logger.warn(anyString, (Object []) any); times = 0;
        }};

    }

    @Test
    public void testMultipleCopyInvalidVerifyGivenDockerfile(@Injectable final Logger logger) throws IOException {
        BuildImageConfiguration buildConfig = createBuildConfigMultiAssembly();

        assemblyManager.verifyGivenDockerfile(
                new File(getClass().getResource("/docker/Dockerfile_assembly_verify_multi_copy_invalid.test").getPath()),
                buildConfig, createInterpolator(buildConfig),
                logger);

        new Verifications() {{
            logger.warn(anyString, (Object []) any); times = 2;
        }};

    }

    @Test
    public void testMultipleCopyChownValidVerifyGivenDockerfile(@Injectable final Logger logger) throws IOException {
        BuildImageConfiguration buildConfig = createBuildConfigMultiAssembly();

        assemblyManager.verifyGivenDockerfile(
                new File(getClass().getResource("/docker/Dockerfile_assembly_verify_multi_copy_chown_valid.test").getPath()),
                buildConfig,
                createInterpolator(buildConfig),
                logger);

        new Verifications() {{
            logger.warn(anyString, (Object []) any); times = 0;
        }};

    }

    @Test
    public void testArchiveCreationDockerfileNoAssembly(@Injectable final TarArchiver tarArchiver,
                                                        @Injectable final Logger logger) throws MojoExecutionException, NoSuchArchiverException {
        MojoParameters mojoParams = mockMojoParams(mockMavenProject());

        BuildImageConfiguration buildImageConfiguration = new BuildImageConfiguration.Builder()
                .dockerFile(DockerAssemblyManagerTest.class.getResource("/docker/Dockerfile.test").getPath())
                .build();
        buildImageConfiguration.initAndValidate(logger);

        File tarArchive = assemblyManager.createDockerTarArchive("test_image", mojoParams, buildImageConfiguration, logger, null);

        assertNotNull(tarArchive);

        new Verifications() {{
            archiverManager.getArchiver("tar");
            times = 1;

            List<FileSet> fileSets = new ArrayList<>();
            tarArchiver.addFileSet(withCapture(fileSets));

            assertEquals(2, fileSets.size());
            assertEquals("build", fileSets.get(0).getDirectory().getName());
            assertNull(fileSets.get(0).getIncludes());
            assertNull(fileSets.get(0).getExcludes());
            assertArrayEquals(new String[]{"target/**", "Dockerfile.test"}, fileSets.get(1).getExcludes());
            assertNull(fileSets.get(1).getIncludes());
        }};
    }

    @Test
    public void testArchiveCreationDockerfileWithDirAssembly(@Injectable final TarArchiver tarArchiver,
                                                        @Injectable final Logger logger) throws MojoExecutionException, NoSuchArchiverException {
        MojoParameters mojoParams = mockMojoParams(mockMavenProject());

        BuildImageConfiguration buildImageConfiguration = new BuildImageConfiguration.Builder()
                .dockerFile(DockerAssemblyManagerTest.class.getResource("/docker/Dockerfile.test").getPath())
                .assembly(new AssemblyConfiguration.Builder()
                        .mode(AssemblyMode.dir.name())
                        .build()
                )
                .build();
        buildImageConfiguration.initAndValidate(logger);

        File tarArchive = assemblyManager.createDockerTarArchive("test_image", mojoParams, buildImageConfiguration, logger, null);

        assertNotNull(tarArchive);

        new Verifications() {{
            archiverManager.getArchiver("tar");
            times = 1;

            List<FileSet> fileSets = new ArrayList<>();
            tarArchiver.addFileSet(withCapture(fileSets));

            assertEquals(2, fileSets.size());
            assertEquals("build", fileSets.get(0).getDirectory().getName());
            assertNull(fileSets.get(0).getIncludes());
            assertNull(fileSets.get(0).getExcludes());
            assertArrayEquals(new String[]{"target/**", "Dockerfile.test"}, fileSets.get(1).getExcludes());
            assertNull(fileSets.get(1).getIncludes());
        }};
    }

    @Test
    public void testArchiveCreationDockerfileWithArchiveAssembly(@Injectable final TarArchiver tarArchiver,
                                                             @Injectable final Logger logger) throws MojoExecutionException, NoSuchArchiverException {
        MojoParameters mojoParams = mockMojoParams(mockMavenProject());

        BuildImageConfiguration buildImageConfiguration = new BuildImageConfiguration.Builder()
                .dockerFile(DockerAssemblyManagerTest.class.getResource("/docker/Dockerfile.test").getPath())
                .assembly(new AssemblyConfiguration.Builder()
                        .mode(AssemblyMode.tar.name())
                        .assemblyDef(new Assembly())
                        .build()
                )
                .build();
        buildImageConfiguration.initAndValidate(logger);

        File tarArchive = assemblyManager.createDockerTarArchive("test_image", mojoParams, buildImageConfiguration, logger, null);

        assertNotNull(tarArchive);

        new Verifications() {{
            archiverManager.getArchiver("tar");
            times = 1;

            List<FileSet> fileSets = new ArrayList<>();
            tarArchiver.addFileSet(withCapture(fileSets));

            assertEquals(1, fileSets.size());
            assertArrayEquals(new String[]{"target/**", "Dockerfile.test"}, fileSets.get(0).getExcludes());
            assertNull(fileSets.get(0).getIncludes());

            tarArchiver.addFile(new File("target/test_image/build/Dockerfile.test").getAbsoluteFile(), "Dockerfile.test");

            List<ArchivedFileSet> archivedFileSets = new ArrayList<>();
            tarArchiver.addArchivedFileSet(withCapture(archivedFileSets));

            assertEquals(1, archivedFileSets.size());
            assertEquals(new File("target/test_image/build/maven.tar").getAbsoluteFile(), archivedFileSets.get(0).getArchive());
            assertEquals("maven/", archivedFileSets.get(0).getPrefix());
        }};
    }

    @Test
    public void testArchiveCreationDockerfileWithMultipleArchiveAssemblies(@Injectable final TarArchiver tarArchiver,
                                                                 @Injectable final Logger logger) throws MojoExecutionException, NoSuchArchiverException {
        MojoParameters mojoParams = mockMojoParams(mockMavenProject());

        BuildImageConfiguration buildImageConfiguration = new BuildImageConfiguration.Builder()
                .dockerFile(DockerAssemblyManagerTest.class.getResource("/docker/Dockerfile.test").getPath())
                .assemblies(Arrays.asList(
                        new AssemblyConfiguration.Builder()
                                .name("first")
                                .mode(AssemblyMode.tar.name())
                                .assemblyDef(new Assembly())
                                .build(),
                        new AssemblyConfiguration.Builder()
                                .name("second")
                                .mode(AssemblyMode.tar.name())
                                .assemblyDef(new Assembly())
                                .build()
                ))
                .build();
        buildImageConfiguration.initAndValidate(logger);

        File tarArchive = assemblyManager.createDockerTarArchive("test_image", mojoParams, buildImageConfiguration, logger, null);

        assertNotNull(tarArchive);

        new Verifications() {{
            archiverManager.getArchiver("tar");
            times = 1;

            List<FileSet> fileSets = new ArrayList<>();
            tarArchiver.addFileSet(withCapture(fileSets));

            assertEquals(1, fileSets.size());
            assertArrayEquals(new String[]{"target/**", "Dockerfile.test"}, fileSets.get(0).getExcludes());
            assertNull(fileSets.get(0).getIncludes());

            tarArchiver.addFile(new File("target/test_image/build/Dockerfile.test").getAbsoluteFile(), "Dockerfile.test");

            List<ArchivedFileSet> archivedFileSets = new ArrayList<>();
            tarArchiver.addArchivedFileSet(withCapture(archivedFileSets));

            assertEquals(2, archivedFileSets.size());
            assertEquals(new File("target/test_image/build/first.tar").getAbsoluteFile(), archivedFileSets.get(0).getArchive());
            assertEquals("first/", archivedFileSets.get(0).getPrefix());
            assertEquals(new File("target/test_image/build/second.tar").getAbsoluteFile(), archivedFileSets.get(1).getArchive());
            assertEquals("second/", archivedFileSets.get(1).getPrefix());
        }};
    }

    @Test
    public void testArchiveCreationNoDockerfileWithMultipleArchiveAssemblies(@Injectable final TarArchiver tarArchiver,
                                                                           @Injectable final Logger logger) throws MojoExecutionException, NoSuchArchiverException {
        MojoParameters mojoParams = mockMojoParams(mockMavenProject());

        BuildImageConfiguration buildImageConfiguration = new BuildImageConfiguration.Builder()
                .assemblies(Arrays.asList(
                        new AssemblyConfiguration.Builder()
                                .name("first")
                                .mode(AssemblyMode.tar.name())
                                .assemblyDef(new Assembly())
                                .build(),
                        new AssemblyConfiguration.Builder()
                                .name("second")
                                .mode(AssemblyMode.tar.name())
                                .assemblyDef(new Assembly())
                                .build()
                ))
                .build();
        buildImageConfiguration.initAndValidate(logger);

        File tarArchive = assemblyManager.createDockerTarArchive("test_image", mojoParams, buildImageConfiguration, logger, null);

        assertNotNull(tarArchive);

        new Verifications() {{
            archiverManager.getArchiver("tar");
            times = 1;

            tarArchiver.addFile(new File("target/test_image/build/Dockerfile").getAbsoluteFile(), "Dockerfile");

            List<ArchivedFileSet> archivedFileSets = new ArrayList<>();
            tarArchiver.addArchivedFileSet(withCapture(archivedFileSets));

            assertEquals(2, archivedFileSets.size());
            assertEquals(new File("target/test_image/build/first.tar").getAbsoluteFile(), archivedFileSets.get(0).getArchive());
            assertEquals("first/", archivedFileSets.get(0).getPrefix());
            assertEquals(new File("target/test_image/build/second.tar").getAbsoluteFile(), archivedFileSets.get(1).getArchive());
            assertEquals("second/", archivedFileSets.get(1).getPrefix());
        }};
    }

    @Test
    public void testArchiveCreationNoDockerfileWithExecutableAssemblies(@Mocked final PlexusIoResource resource,
                                                                        @Mocked final ArchiveEntry archiveEntry,
                                                                        @Mocked final TarArchiver tarArchiver,
                                                                        @Injectable final Logger logger) throws MojoExecutionException, NoSuchArchiverException, IOException {
        MojoParameters mojoParams = mockMojoParams(mockMavenProject());

        FileItem testFile = new FileItem();
        testFile.setDestName("test.txt");
        testFile.setSource("test.in");

        Assembly testAssembly = new Assembly();
        testAssembly.addFile(testFile);

        new Expectations() {{
            archiveEntry.getName();
            result = "test";
            archiveEntry.getMode();
            result = 0644;
            archiveEntry.getResource();
            result = resource;

            tarArchiver.getResources();
            result = new ResourceIterator() {
                boolean consumed = false;

                @Override
                public boolean hasNext() {
                    return !consumed;
                }

                @Override
                public ArchiveEntry next() {
                    if (consumed) {
                        return null;
                    }
                    consumed = true;
                    return archiveEntry;
                }
            };
        }};

        BuildImageConfiguration buildImageConfiguration = new BuildImageConfiguration.Builder()
                .assemblies(Collections.singletonList(
                        new AssemblyConfiguration.Builder()
                                .name("first")
                                .mode(AssemblyMode.tar.name())
                                .assemblyDef(testAssembly)
                                .permissions(AssemblyConfiguration.PermissionMode.exec.name())
                                .build()
                ))
                .build();
        buildImageConfiguration.initAndValidate(logger);

        File tarArchive = assemblyManager.createDockerTarArchive("test_image", mojoParams, buildImageConfiguration, logger, null);

        assertNotNull(tarArchive);

        new Verifications() {{
            archiverManager.getArchiver("tar");
            times = 1;

            tarArchiver.addFile(new File("target/test_image/build/Dockerfile").getAbsoluteFile(), "Dockerfile");

            List<ArchivedFileSet> archivedFileSets = new ArrayList<>();
            tarArchiver.addArchivedFileSet(withCapture(archivedFileSets));

            assertEquals(1, archivedFileSets.size());
            assertEquals(new File("target/test_image/build/first.tar").getAbsoluteFile(), archivedFileSets.get(0).getArchive());
            assertEquals("first/", archivedFileSets.get(0).getPrefix());

            tarArchiver.addResource((PlexusIoResource) any, "test", 0755);
        }};
    }

    private BuildImageConfiguration createBuildConfig() {
        return new BuildImageConfiguration.Builder()
                .assembly(new AssemblyConfiguration.Builder()
                        .descriptorRef("artifact")
                        .build())
                .build();
    }

    private BuildImageConfiguration createBuildConfigMultiAssembly() {
        return new BuildImageConfiguration.Builder()
                .from("busybox:latest")
                .assemblies(
                        Arrays.asList(
                                new AssemblyConfiguration.Builder()
                                        .descriptorRef("dependencies")
                                        .name("deps")
                                        .build(),
                                new AssemblyConfiguration.Builder()
                                        .descriptorRef("artifact")
                                        .build()
                        ))
                .build();
    }

    private FixedStringSearchInterpolator createInterpolator(BuildImageConfiguration buildConfig) {
        MavenProject project = mockMavenProject();

        return DockerFileUtil.createInterpolator(mockMojoParams(project), buildConfig.getFilter());
    }

    private MavenProject mockMavenProject() {
        MavenProject project = new MavenProject();
        project.setArtifactId("docker-maven-plugin");
        project.setFile(new File(".").getAbsoluteFile());
        return project;
    }

    private MojoParameters mockMojoParams(MavenProject project) {
        Settings settings = new Settings();
        ArtifactRepository localRepository = new MavenArtifactRepository() {
            @Mock
            public String getBasedir() {
                return "repository";
            }
        };
        @SuppressWarnings("deprecation")
        MavenSession session = new MavenSession(null, settings, localRepository, null, null, Collections.<String>emptyList(), ".", null, null, new Date());
        return new MojoParameters(session, project, null, null, null, settings, "src", "target", Collections.singletonList(project));
    }

}
