package io.fabric8.maven.docker.assembly;

import io.fabric8.maven.docker.config.Arguments;
import io.fabric8.maven.docker.config.AssemblyConfiguration;
import io.fabric8.maven.docker.config.AssemblyMode;
import io.fabric8.maven.docker.config.BuildImageConfiguration;
import io.fabric8.maven.docker.util.AnsiLogger;
import io.fabric8.maven.docker.util.DockerFileUtil;
import io.fabric8.maven.docker.util.Logger;
import io.fabric8.maven.docker.util.MojoParameters;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.MavenArtifactRepository;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.apache.maven.plugins.assembly.AssemblerConfigurationSource;
import org.apache.maven.plugins.assembly.archive.AssemblyArchiver;
import org.apache.maven.plugins.assembly.io.AssemblyReader;
import org.apache.maven.plugins.assembly.model.Assembly;
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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

@ExtendWith(MockitoExtension.class)
class DockerAssemblyManagerTest {

    @InjectMocks
    private DockerAssemblyManager assemblyManager;

    @Mock
    private AssemblyArchiver assemblyArchiver;

    @Mock
    private AssemblyReader assemblyReader;

    @Mock
    private ArchiverManager archiverManager;

    private TrackArchiverCollection trackArchivers = new TrackArchiverCollection();

    @Mock
    private TarArchiver tarArchiver;

    @Mock
    private Logger logger;

    @Test
    void testNoAssembly() {
        BuildImageConfiguration buildConfig = new BuildImageConfiguration();
        List<AssemblyConfiguration> assemblyConfig = buildConfig.getAllAssemblyConfigurations();

        String content =
            assemblyManager.createDockerFileBuilder(
                buildConfig, assemblyConfig).content();

        Assertions.assertFalse(content.contains("COPY"));
        Assertions.assertFalse(content.contains("VOLUME"));
    }

    @Test
    void testShellIsSet() {
        BuildImageConfiguration buildConfig =
            new BuildImageConfiguration.Builder().shell(
                    new Arguments.Builder().withShell("/bin/sh echo hello").build())
                .build();

        DockerFileBuilder builder =
            assemblyManager.createDockerFileBuilder(buildConfig, buildConfig.getAllAssemblyConfigurations());
        String content = builder.content();

        Assertions.assertTrue(content.contains("SHELL [\"/bin/sh\",\"echo\",\"hello\"]"));
    }

    @Test
    void assemblyFiles(@TempDir Path tmpDir,
        @Mock final MavenProject project,
        @Mock final MojoParameters mojoParams,
        @Mock final Assembly assembly)
        throws Exception {

        setupAssemblies(tmpDir, project, mojoParams, assembly);

        BuildImageConfiguration buildConfig = createBuildConfig();

        assemblyManager.getAssemblyFiles("testImage", buildConfig.getAllAssemblyConfigurations().get(0), mojoParams, new AnsiLogger(new SystemStreamLog(), true, "build"));
        Mockito.verify(assemblyArchiver).createArchive(Mockito.eq(assembly), Mockito.eq("maven"), Mockito.eq("track"), Mockito.any(DockerAssemblyConfigurationSource.class),
            Mockito.any());
    }

    @Test
    void multipleAssemblyFiles(@TempDir Path tmpDir,
        @Mock final MavenProject project,
        @Mock final MojoParameters mojoParams,
        @Mock final Assembly assembly1,
        @Mock final Assembly assembly2)
        throws Exception {

        setupAssemblies(tmpDir,  project, mojoParams, assembly1, assembly2);

        BuildImageConfiguration buildConfig = createBuildConfigMultiAssembly();

        AssemblyFiles files = assemblyManager.getAssemblyFiles("testImage", buildConfig.getAllAssemblyConfigurations().get(0), mojoParams,
            new AnsiLogger(new SystemStreamLog(), true, "build"));
        Assertions.assertNotNull(files);
        files = assemblyManager.getAssemblyFiles("testImage", buildConfig.getAllAssemblyConfigurations().get(1), mojoParams, new AnsiLogger(new SystemStreamLog(), true, "build"));
        Assertions.assertNotNull(files);
    }

    private void setupAssemblies(Path tmpDir, MavenProject project,  MojoParameters mojoParams, Assembly assembly1,  Assembly... assemblies) throws Exception {
        ReflectionUtils.setVariableValueInObject(assemblyManager, "trackArchivers", trackArchivers);

        Mockito.doAnswer(i -> tmpDir.resolve( (String) i.getArguments()[1]).toFile())
            .when(assemblyArchiver)
            .createArchive(Mockito.any(Assembly.class), Mockito.anyString(), Mockito.anyString(),
                Mockito.any(AssemblerConfigurationSource.class), Mockito.any());

        Mockito.doReturn("target/").when(mojoParams).getOutputDirectory();
        Mockito.doReturn(project).when(mojoParams).getProject();
        Mockito.doReturn(new File(".")).when(project).getBasedir();

        Object[] arrayOfSingletonLists = Arrays.stream(assemblies).map(Collections::singletonList).toArray();
        Mockito.doReturn(Collections.singletonList(assembly1), arrayOfSingletonLists)
            .when(assemblyReader).readAssemblies(Mockito.any(AssemblerConfigurationSource.class));
    }

    @Test
    void testCopyValidVerifyGivenDockerfile(@Mock final Logger logger) throws IOException {
        BuildImageConfiguration buildConfig = createBuildConfig();

        assemblyManager.verifyGivenDockerfile(
            new File(getClass().getResource("/docker/Dockerfile_assembly_verify_copy_valid.test").getPath()),
            buildConfig,
            createInterpolator(buildConfig),
            logger);

        Mockito.verify(logger, Mockito.never()).warn(Mockito.anyString(), Mockito.any());
    }

    @Test
    void testCopyInvalidVerifyGivenDockerfile(@Mock final Logger logger) throws IOException {
        BuildImageConfiguration buildConfig = createBuildConfig();

        assemblyManager.verifyGivenDockerfile(
            new File(getClass().getResource("/docker/Dockerfile_assembly_verify_copy_invalid.test").getPath()),
            buildConfig, createInterpolator(buildConfig),
            logger);

        Mockito.verify(logger).warn(Mockito.anyString(), Mockito.any());
    }

    @Test
    void testCopyChownValidVerifyGivenDockerfile(@Mock final Logger logger) throws IOException {
        BuildImageConfiguration buildConfig = createBuildConfig();

        assemblyManager.verifyGivenDockerfile(
            new File(getClass().getResource("/docker/Dockerfile_assembly_verify_copy_chown_valid.test").getPath()),
            buildConfig,
            createInterpolator(buildConfig),
            logger);

        Mockito.verify(logger, Mockito.never()).warn(Mockito.anyString(), Mockito.any());
    }

    @Test
    void testMultipleCopyValidVerifyGivenDockerfile(@Mock final Logger logger) throws IOException {
        BuildImageConfiguration buildConfig = createBuildConfigMultiAssembly();

        assemblyManager.verifyGivenDockerfile(
            new File(getClass().getResource("/docker/Dockerfile_assembly_verify_multi_copy_valid.test").getPath()),
            buildConfig,
            createInterpolator(buildConfig),
            logger);

        Mockito.verify(logger, Mockito.never()).warn(Mockito.anyString(), Mockito.any());
    }

    @Test
    void testMultipleCopyInvalidVerifyGivenDockerfile(@Mock final Logger logger) throws IOException {
        BuildImageConfiguration buildConfig = createBuildConfigMultiAssembly();

        assemblyManager.verifyGivenDockerfile(
            new File(getClass().getResource("/docker/Dockerfile_assembly_verify_multi_copy_invalid.test").getPath()),
            buildConfig, createInterpolator(buildConfig),
            logger);

        Mockito.verify(logger, Mockito.times(2)).warn(Mockito.anyString(), Mockito.any());
    }

    @Test
    void testMultipleCopyChownValidVerifyGivenDockerfile(@Mock final Logger logger) throws IOException {
        BuildImageConfiguration buildConfig = createBuildConfigMultiAssembly();

        assemblyManager.verifyGivenDockerfile(
            new File(getClass().getResource("/docker/Dockerfile_assembly_verify_multi_copy_chown_valid.test").getPath()),
            buildConfig,
            createInterpolator(buildConfig),
            logger);

        Mockito.verify(logger, Mockito.never()).warn(Mockito.anyString(), Mockito.any());
    }

    @Test
    void testArchiveCreationDockerfileNoAssembly() throws MojoExecutionException, NoSuchArchiverException {
        MojoParameters mojoParams = mockMojoParams(mockMavenProject());

        BuildImageConfiguration buildImageConfiguration = new BuildImageConfiguration.Builder()
            .dockerFile(DockerAssemblyManagerTest.class.getResource("/docker/Dockerfile.test").getPath())
            .build();
        buildImageConfiguration.initAndValidate(logger);

        Mockito.doReturn(tarArchiver).when(archiverManager).getArchiver("tar");
        File tarArchive = assemblyManager.createDockerTarArchive("test_image", mojoParams, buildImageConfiguration, logger, null);
        Assertions.assertNotNull(tarArchive);

        verifyArchiveManager();
    }

    private void verifyArchiveManager() {
        List<FileSet> fileSets = getFileSetsToVerify(2);
        Assertions.assertEquals("build", fileSets.get(0).getDirectory().getName());
        Assertions.assertNull(fileSets.get(0).getIncludes());
        Assertions.assertNull(fileSets.get(0).getExcludes());
        Assertions.assertArrayEquals(new String[] { "target/**", "Dockerfile.test" }, fileSets.get(1).getExcludes());
        Assertions.assertNull(fileSets.get(1).getIncludes());
    }

    private List<FileSet> getFileSetsToVerify(int invocations) {
        ArgumentCaptor<FileSet> fileSetCapture = ArgumentCaptor.forClass(FileSet.class);
        Mockito.verify(tarArchiver, Mockito.times(invocations)).addFileSet(fileSetCapture.capture());

        return fileSetCapture.getAllValues();
    }

    private List<ArchivedFileSet> getArchivedFileSetsToVerify(String input, String destFileName, int count) {
        Mockito.verify(tarArchiver).addFile(new File(input).getAbsoluteFile(), destFileName);

        ArgumentCaptor<ArchivedFileSet> archivedFileSetCapture = ArgumentCaptor.forClass(ArchivedFileSet.class);
        Mockito.verify(tarArchiver, Mockito.times(count)).addArchivedFileSet(archivedFileSetCapture.capture());

        return archivedFileSetCapture.getAllValues();
    }

    @Test
    void testArchiveCreationDockerfileWithDirAssembly() throws MojoExecutionException, NoSuchArchiverException {
        MojoParameters mojoParams = mockMojoParams(mockMavenProject());

        BuildImageConfiguration buildImageConfiguration = new BuildImageConfiguration.Builder()
            .dockerFile(DockerAssemblyManagerTest.class.getResource("/docker/Dockerfile.test").getPath())
            .assembly(new AssemblyConfiguration.Builder()
                .mode(AssemblyMode.dir.name())
                .build()
            )
            .build();
        buildImageConfiguration.initAndValidate(logger);

        Mockito.doReturn(tarArchiver).when(archiverManager).getArchiver("tar");
        File tarArchive = assemblyManager.createDockerTarArchive("test_image", mojoParams, buildImageConfiguration, logger, null);
        Assertions.assertNotNull(tarArchive);

        verifyArchiveManager();
    }

    @Test
    void testArchiveCreationDockerfileWithArchiveAssembly() throws MojoExecutionException, NoSuchArchiverException {
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

        Mockito.doReturn(tarArchiver).when(archiverManager).getArchiver("tar");
        File tarArchive = assemblyManager.createDockerTarArchive("test_image", mojoParams, buildImageConfiguration, logger, null);
        Assertions.assertNotNull(tarArchive);

        List<FileSet> fileSets= getFileSetsToVerify(1);
        Assertions.assertArrayEquals(new String[] { "target/**", "Dockerfile.test" }, fileSets.get(0).getExcludes());
        Assertions.assertNull(fileSets.get(0).getIncludes());

        List<ArchivedFileSet> archivedFileSets = getArchivedFileSetsToVerify("target/test_image/build/Dockerfile.test", "Dockerfile.test", 1);
        Assertions.assertEquals(new File("target/test_image/build/maven.tar").getAbsoluteFile(), archivedFileSets.get(0).getArchive());
        Assertions.assertEquals("maven/", archivedFileSets.get(0).getPrefix());
    }

    @Test
    void testArchiveCreationDockerfileWithMultipleArchiveAssemblies() throws MojoExecutionException, NoSuchArchiverException {
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

        Mockito.doReturn(tarArchiver).when(archiverManager).getArchiver("tar");
        File tarArchive = assemblyManager.createDockerTarArchive("test_image", mojoParams, buildImageConfiguration, logger, null);
        Assertions.assertNotNull(tarArchive);

        List<FileSet> fileSets = getFileSetsToVerify(1);
        Assertions.assertArrayEquals(new String[] { "target/**", "Dockerfile.test" }, fileSets.get(0).getExcludes());
        Assertions.assertNull(fileSets.get(0).getIncludes());

        List<ArchivedFileSet> archivedFileSets = getArchivedFileSetsToVerify("target/test_image/build/Dockerfile.test", "Dockerfile.test", 2);
        Assertions.assertEquals(new File("target/test_image/build/first.tar").getAbsoluteFile(), archivedFileSets.get(0).getArchive());
        Assertions.assertEquals("first/", archivedFileSets.get(0).getPrefix());
        Assertions.assertEquals(new File("target/test_image/build/second.tar").getAbsoluteFile(), archivedFileSets.get(1).getArchive());
        Assertions.assertEquals("second/", archivedFileSets.get(1).getPrefix());
    }

    @Test
    void testArchiveCreationNoDockerfileWithMultipleArchiveAssemblies() throws MojoExecutionException, NoSuchArchiverException {
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

        Mockito.doReturn(tarArchiver).when(archiverManager).getArchiver("tar");
        File tarArchive = assemblyManager.createDockerTarArchive("test_image", mojoParams, buildImageConfiguration, logger, null);
        Assertions.assertNotNull(tarArchive);

        /*
            tarArchiver.addFile(new File("target/test_image/build/Dockerfile").getAbsoluteFile(), "Dockerfile");

            List<ArchivedFileSet> archivedFileSets = new ArrayList<>();
            tarArchiver.addArchivedFileSet(withCapture(archivedFileSets));
         */
        List<ArchivedFileSet> archivedFileSets = getArchivedFileSetsToVerify( "target/test_image/build/Dockerfile", "Dockerfile", 2);
        Assertions.assertEquals(new File("target/test_image/build/first.tar").getAbsoluteFile(), archivedFileSets.get(0).getArchive());
        Assertions.assertEquals("first/", archivedFileSets.get(0).getPrefix());
        Assertions.assertEquals(new File("target/test_image/build/second.tar").getAbsoluteFile(), archivedFileSets.get(1).getArchive());
        Assertions.assertEquals("second/", archivedFileSets.get(1).getPrefix());
    }

    @Test
    void testArchiveCreationNoDockerfileWithExecutableAssemblies(@TempDir Path tmpDir,
        @Mock final PlexusIoResource resource,
        @Mock final ArchiveEntry archiveEntry) throws MojoExecutionException, NoSuchArchiverException, IOException {
        MojoParameters mojoParams = mockMojoParams(mockMavenProject());

        FileItem testFile = new FileItem();
        testFile.setDestName("test.txt");
        testFile.setSource("test.in");

        Assembly testAssembly = new Assembly();
        testAssembly.addFile(testFile);

        Mockito.doReturn("test").when(archiveEntry).getName();
        Mockito.doReturn(0_644).when(archiveEntry).getMode();
        Mockito.doReturn(resource).when(archiveEntry).getResource();

        Mockito.doReturn(true).when(resource).isExisting();
        Mockito.doReturn(true).when(resource).isFile();
        Mockito.doReturn(new ByteArrayInputStream(new byte[0])).when(resource).getContents();

        Mockito.doReturn(new ResourceIterator() {
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
        }).when(tarArchiver).getResources();

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

        Mockito.doReturn(tarArchiver).when(archiverManager).getArchiver("tar");
        Mockito.doReturn(tmpDir.resolve("damt.tar").toFile()).when(tarArchiver).getDestFile();

        File tarArchive = assemblyManager.createDockerTarArchive("test_image", mojoParams, buildImageConfiguration, logger, null);
        Assertions.assertNotNull(tarArchive);

        List<ArchivedFileSet> archivedFileSets = getArchivedFileSetsToVerify("target/test_image/build/Dockerfile", "Dockerfile", 1);
        Assertions.assertEquals(new File("target/test_image/build/first.tar").getAbsoluteFile(), archivedFileSets.get(0).getArchive());
        Assertions.assertEquals("first/", archivedFileSets.get(0).getPrefix());

        // We cannot intercept the tarArchiver that AllFilesExecCustomizer creates.
        // TODO: Should AllFilesExecCustomizer lookup tarArchive wth archiverManager.getArchiver("tar");
        // Mockito.verify(tarArchiver)
        //     .addResource(Mockito.any(PlexusIoResource.class), Mockito.eq("test"), Mockito.eq(0_755));
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
            public String getBasedir() {
                return "repository";
            }
        };
        @SuppressWarnings("deprecation")
        MavenSession session = new MavenSession(null, settings, localRepository, null, null, Collections.emptyList(), ".", null, null, new Date());
        return new MojoParameters(session, project, null, null, null, settings, "src", "target", Collections.singletonList(project));
    }

}
