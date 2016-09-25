package io.fabric8.maven.docker.assembly;

import io.fabric8.maven.docker.config.*;
import io.fabric8.maven.docker.util.EnvUtil;
import io.fabric8.maven.docker.util.Logger;
import io.fabric8.maven.docker.util.MojoParameters;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.assembly.AssemblerConfigurationSource;
import org.apache.maven.plugin.assembly.InvalidAssemblerConfigurationException;
import org.apache.maven.plugin.assembly.archive.ArchiveCreationException;
import org.apache.maven.plugin.assembly.archive.AssemblyArchiver;
import org.apache.maven.plugin.assembly.format.AssemblyFormattingException;
import org.apache.maven.plugin.assembly.io.AssemblyReadException;
import org.apache.maven.plugin.assembly.io.AssemblyReader;
import org.apache.maven.plugin.assembly.model.Assembly;
import org.apache.maven.shared.utils.PathTool;
import org.apache.maven.shared.utils.io.FileUtils;
import org.codehaus.plexus.archiver.Archiver;
import org.codehaus.plexus.archiver.manager.ArchiverManager;
import org.codehaus.plexus.archiver.manager.NoSuchArchiverException;
import org.codehaus.plexus.archiver.tar.TarArchiver;
import org.codehaus.plexus.archiver.tar.TarLongFileMode;
import org.codehaus.plexus.archiver.util.DefaultArchivedFileSet;
import org.codehaus.plexus.archiver.util.DefaultFileSet;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


/**
 * Tool for creating a docker image tar ball including a Dockerfile for building
 * a docker image.
 *
 * @author roland
 * @since 08.05.14
 */
@Component(role = DockerAssemblyManager.class, instantiationStrategy = "per-lookup")
public class DockerAssemblyManager {

    public static final String DEFAULT_DATA_BASE_IMAGE = "busybox:latest";
    public static final String SCRATCH_IMAGE = "scratch";

    // Assembly name used also as build directory within outputBuildDir
    public static final String ASSEMBLY_NAME = "maven";
    public static final String DOCKER_IGNORE = ".maven-dockerignore";
    public static final String DOCKER_EXCLUDE = ".maven-dockerexclude";
    public static final String DOCKER_INCLUDE = ".maven-dockerinclude";

    @Requirement
    private AssemblyArchiver assemblyArchiver;

    @Requirement
    private AssemblyReader assemblyReader;

    @Requirement
    private ArchiverManager archiverManager;

    @Requirement(hint = "track")
    private Archiver trackArchiver;

    /**
     * Create an docker tar archive from the given configuration which can be send to the Docker host for
     * creating the image.
     *
     * @param imageName Name of the image to create (used for creating build directories)
     * @param params Mojos parameters (used for finding the directories)
     * @param buildConfig configuration for how to build the image
     * @param log Logger used to display warning if permissions are to be normalized
     * @return file holding the path to the created assembly tar file
     * @throws MojoExecutionException
     */
    public File createDockerTarArchive(String imageName, MojoParameters params, BuildImageConfiguration buildConfig, Logger log)
            throws MojoExecutionException {
        BuildDirs buildDirs = createBuildDirs(imageName, params);

        AssemblyConfiguration assemblyConfig = buildConfig.getAssemblyConfiguration();
        AssemblyMode assemblyMode = (assemblyConfig == null) ? AssemblyMode.dir : assemblyConfig.getMode();

        // Build up assembly
        if (hasAssemblyConfiguration(assemblyConfig)) {
            createAssemblyArchive(assemblyConfig, params, buildDirs);
        }

        ArchiverCustomizer customizer;
        try {
            if (buildConfig.isDockerFileMode()) {
                // Use specified docker directory which must include a Dockerfile.
                final File dockerFile = buildConfig.getAbsoluteDockerFilePath(params);
                if (!dockerFile.exists()) {
                    throw new MojoExecutionException("Configured Dockerfile \"" +
                                                     buildConfig.getDockerFile() + "\" (resolved to \"" + dockerFile + "\") doesnt exist");
                }
                // User dedicated Dockerfile from extra director
                customizer = new ArchiverCustomizer() {
                    @Override
                    public TarArchiver customize(TarArchiver archiver) throws IOException {
                        DefaultFileSet fileSet = DefaultFileSet.fileSet(dockerFile.getParentFile());
                        addDockerIgnoreIfPresent(fileSet);
                        archiver.addFileSet(fileSet);
                        return archiver;
                    }
                };
            } else {
                // Create custom docker file in output dir
                DockerFileBuilder builder = createDockerFileBuilder(buildConfig, assemblyConfig);
                builder.write(buildDirs.getOutputDirectory());
                // Add own Dockerfile
                final File dockerFile = new File(buildDirs.getOutputDirectory(),"Dockerfile");
                customizer = new ArchiverCustomizer() {
                    @Override
                    public TarArchiver customize(TarArchiver archiver) throws IOException {
                        archiver.addFile(dockerFile, "Dockerfile");
                        return archiver;
                    }
                };
            }

            // If required make all files in the assembly executable
            if (assemblyConfig != null) {
                AssemblyConfiguration.PermissionMode mode = assemblyConfig.getPermissions();
                if (mode == AssemblyConfiguration.PermissionMode.exec ||
                    mode == AssemblyConfiguration.PermissionMode.auto && EnvUtil.isWindows()) {
                    customizer = new AllFilesExecCustomizer(customizer, log);
                }
            }

            return createBuildTarBall(buildDirs, customizer, assemblyMode, buildConfig.getCompression());

        } catch (IOException e) {
            throw new MojoExecutionException(String.format("Cannot create Dockerfile in %s", buildDirs.getOutputDirectory()), e);
        }
    }

    /**
     * Extract all files with a tracking archiver. These can be used to track changes in the filesystem and triggering
     * a rebuild of the image if needed ('docker:watch')
     */
    public AssemblyFiles getAssemblyFiles(String name, BuildImageConfiguration buildConfig, MojoParameters mojoParams, Logger log)
            throws InvalidAssemblerConfigurationException, ArchiveCreationException, AssemblyFormattingException, MojoExecutionException {

        BuildDirs buildDirs = createBuildDirs(name, mojoParams);

        AssemblyConfiguration assemblyConfig = buildConfig.getAssemblyConfiguration();
        DockerAssemblyConfigurationSource source =
                        new DockerAssemblyConfigurationSource(mojoParams, buildDirs, assemblyConfig);
        Assembly assembly = getAssemblyConfig(assemblyConfig, source);


        synchronized (trackArchiver) {
            MappingTrackArchiver ta = (MappingTrackArchiver) trackArchiver;
            ta.init(log);
            assembly.setId("tracker");
            assemblyArchiver.createArchive(assembly, ASSEMBLY_NAME, "track", source, false);
            return ta.getAssemblyFiles(mojoParams.getSession());
        }
    }

    private BuildDirs createBuildDirs(String imageName, MojoParameters params) {
        BuildDirs buildDirs = new BuildDirs(imageName, params);
        buildDirs.createDirs();
        return buildDirs;
    }

    private boolean hasAssemblyConfiguration(AssemblyConfiguration assemblyConfig) {
        return assemblyConfig != null &&
                (assemblyConfig.getInline() != null ||
                        assemblyConfig.getDescriptor() != null ||
                        assemblyConfig.getDescriptorRef() != null);
    }

    public File createChangedFilesArchive(List<AssemblyFiles.Entry> entries, File assemblyDirectory,
                                          String imageName, MojoParameters mojoParameters)
            throws MojoExecutionException {
        BuildDirs dirs = createBuildDirs(imageName, mojoParameters);
        try {
            File archive = new File(dirs.getTemporaryRootDirectory(), "changed-files.tar");
            File archiveDir = createArchiveDir(dirs);
            for (AssemblyFiles.Entry entry : entries) {
                File dest = prepareChangedFilesArchivePath(archiveDir,entry.getDestFile(),assemblyDirectory);
                FileUtils.copyFile(entry.getSrcFile(), dest);
            }
            return createChangedFilesTarBall(archive, archiveDir);
        } catch (IOException exp) {
            throw new MojoExecutionException("Error while creating " + dirs.getTemporaryRootDirectory() +
                                             "/changed-files.tar: " + exp);
        }
    }

    private File prepareChangedFilesArchivePath(File archiveDir, File destFile, File assemblyDir) throws IOException {
        // Replace build target dir from destfile and add changed-files build dir instead
        String relativePath = PathTool.getRelativeFilePath(assemblyDir.getCanonicalPath(),destFile.getCanonicalPath());
        return new File(archiveDir,relativePath);
    }

    // Create final tar-ball to be used for building the archive to send to the Docker daemon
    private File createBuildTarBall(BuildDirs buildDirs, ArchiverCustomizer archiverCustomizer,
                                    AssemblyMode buildMode, BuildTarArchiveCompression compression) throws MojoExecutionException {
        File archive = new File(buildDirs.getTemporaryRootDirectory(), "docker-build." + compression.getFileSuffix());
        try {
            TarArchiver archiver = createBuildArchiver(buildDirs.getOutputDirectory(), archive, buildMode);
            archiver = archiverCustomizer.customize(archiver);
            archiver.setCompression(compression.getTarCompressionMethod());
            archiver.createArchive();
            return archive;
        } catch (NoSuchArchiverException e) {
            throw new MojoExecutionException("No archiver for type 'tar' found", e);
        } catch (IOException e) {
            throw new MojoExecutionException("Cannot create archive " + archive, e);
        }
    }

    private void addDockerIgnoreIfPresent(DefaultFileSet fileSet) throws IOException {
        File directory = fileSet.getDirectory();
        addDockerExcludes(fileSet, directory);
        addDockerIncludes(fileSet, directory);
    }

    private void addDockerExcludes(DefaultFileSet fileSet, File directory) throws IOException {
        for (String file : new String[] { DOCKER_EXCLUDE, DOCKER_IGNORE } ) {
            File dockerIgnore = new File(directory, file);
            if (dockerIgnore.exists()) {
                ArrayList<String> excludes = new ArrayList<>(Arrays.asList(FileUtils.fileReadArray(dockerIgnore)));
                excludes.add(DOCKER_IGNORE);
                fileSet.setExcludes(excludes.toArray(new String[excludes.size()]));
            }
        }
    }

    private void addDockerIncludes(DefaultFileSet fileSet, File directory) throws IOException {
        File dockerInclude = new File(directory, DOCKER_INCLUDE);
        if (dockerInclude.exists()) {
            ArrayList<String> includes = new ArrayList<>(Arrays.asList(FileUtils.fileReadArray(dockerInclude)));
            fileSet.setIncludes(includes.toArray(new String[includes.size()]));
        }
    }

    private File createChangedFilesTarBall(File archive, File archiveDir) throws MojoExecutionException {
        try {
            TarArchiver archiver = (TarArchiver) archiverManager.getArchiver("tar");
            archiver.setLongfile(TarLongFileMode.posix);
            archiver.addFileSet(DefaultFileSet.fileSet(archiveDir));
            archiver.setDestFile(archive);
            archiver.createArchive();
            return archive;
        } catch (NoSuchArchiverException e) {
            throw new MojoExecutionException("No archiver for type 'tar' found", e);
        } catch (IOException e) {
            throw new MojoExecutionException("Cannot create archive " + archive, e);
        } catch (RuntimeException e) {
            e.printStackTrace();
            throw e;
        }
    }

    private File createArchiveDir(BuildDirs dirs) throws IOException, MojoExecutionException {
        File archiveDir = new File(dirs.getTemporaryRootDirectory(), "changed-files");
        if (archiveDir.exists()) {
            // Remove old stuff to
            FileUtils.cleanDirectory(archiveDir);
        } else {
            if (!archiveDir.mkdir()) {
                throw new MojoExecutionException("Cannot create " + archiveDir);
            }
        }
        return archiveDir;
    }

    private TarArchiver createBuildArchiver(File outputDir, File archive, AssemblyMode buildMode) throws NoSuchArchiverException {
        TarArchiver archiver = (TarArchiver) archiverManager.getArchiver("tar");
        archiver.setLongfile(TarLongFileMode.posix);

        if (buildMode.isArchive()) {
            DefaultArchivedFileSet archiveSet =
                    DefaultArchivedFileSet.archivedFileSet(new File(outputDir, "maven." + buildMode.getExtension()));
            archiveSet.setPrefix(ASSEMBLY_NAME + "/");
            archiveSet.setIncludingEmptyDirectories(true);
            archiveSet.setUsingDefaultExcludes(false);
            archiver.addArchivedFileSet(archiveSet);
        } else {
            DefaultFileSet fileSet = DefaultFileSet.fileSet(outputDir);
            fileSet.setUsingDefaultExcludes(false);
            archiver.addFileSet(fileSet);
        }
        archiver.setDestFile(archive);
        return archiver;
    }

    // visible for testing
    @SuppressWarnings("deprecation")
    DockerFileBuilder createDockerFileBuilder(BuildImageConfiguration buildConfig, AssemblyConfiguration assemblyConfig) {
        DockerFileBuilder builder =
                new DockerFileBuilder()
                        .env(buildConfig.getEnv())
                        .labels(buildConfig.getLabels())
                        .expose(buildConfig.getPorts())
                        .run(buildConfig.getRunCmds())
                        .volumes(buildConfig.getVolumes())
                        .user(buildConfig.getUser());
        if (buildConfig.getMaintainer() != null) {
            builder.maintainer(buildConfig.getMaintainer());
        }
        if (buildConfig.getWorkdir() != null) {
            builder.workdir(buildConfig.getWorkdir());
        }
        if (assemblyConfig != null) {
            builder.add(ASSEMBLY_NAME, "")
                   .basedir(assemblyConfig.getBasedir())
                   .assemblyUser(assemblyConfig.getUser())
                   .exportBasedir(assemblyConfig.exportBasedir());
        } else {
            builder.exportBasedir(false);
        }

        builder.baseImage(buildConfig.getFrom());

        if (buildConfig.getCmd() != null){
            builder.cmd(buildConfig.getCmd());
        } else if (buildConfig.getCommand() != null) {
            Arguments args = Arguments.Builder.get().withShell(buildConfig.getCommand()).build();
            builder.cmd(args);
        }

        if (buildConfig.getEntryPoint() != null){
            builder.entryPoint(buildConfig.getEntryPoint());
        }

        if (buildConfig.optimise()) {
            builder.optimise();
        }

        return builder;
    }

    private void createAssemblyArchive(AssemblyConfiguration assemblyConfig, MojoParameters params, BuildDirs buildDirs)
            throws MojoExecutionException {
        DockerAssemblyConfigurationSource source = new DockerAssemblyConfigurationSource(params, buildDirs, assemblyConfig);
        Assembly assembly = getAssemblyConfig(assemblyConfig, source);

        AssemblyMode buildMode = assemblyConfig.getMode();
        try {
            assembly.setId("docker");
            assemblyArchiver.createArchive(assembly, ASSEMBLY_NAME, buildMode.getExtension(), source, false);
        } catch (ArchiveCreationException | AssemblyFormattingException e) {
            throw new MojoExecutionException( "Failed to create assembly for docker image " +
                                              " (with mode '" + buildMode + "'): " + e.getMessage(), e );
        } catch (InvalidAssemblerConfigurationException e) {
            throw new MojoExecutionException(assembly, "Assembly is incorrectly configured: " + assembly.getId(),
                                            "Assembly: " + assembly.getId() + " is not configured correctly: "
                                            + e.getMessage());
        }
    }


    private Assembly getAssemblyConfig(AssemblyConfiguration assemblyConfig, DockerAssemblyConfigurationSource source)
            throws MojoExecutionException {
        Assembly assembly = assemblyConfig.getInline();
        if (assembly == null) {
            assembly = extractAssembly(source);
        }
        return assembly;
    }

    private Assembly extractAssembly(AssemblerConfigurationSource config) throws MojoExecutionException {
        try {
            List<Assembly> assemblies = assemblyReader.readAssemblies(config);
            if (assemblies.size() != 1) {
                throw new MojoExecutionException("Only one assembly can be used for creating a Docker base image (and not "
                        + assemblies.size() + ")");
            }
            return assemblies.get(0);
        }
        catch (AssemblyReadException e) {
            throw new MojoExecutionException("Error reading assembly: " + e.getMessage(), e);
        }
        catch (InvalidAssemblerConfigurationException e) {
            throw new MojoExecutionException(assemblyReader, e.getMessage(), "Docker assembly configuration is invalid: " + e.getMessage());
        }
    }

    // Archiver used to adapt for customizations
    interface ArchiverCustomizer {
        TarArchiver customize(TarArchiver archiver) throws IOException;
    }

}
