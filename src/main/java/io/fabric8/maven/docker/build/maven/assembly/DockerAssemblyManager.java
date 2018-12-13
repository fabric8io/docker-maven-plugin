package io.fabric8.maven.docker.build.maven.assembly;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import io.fabric8.maven.docker.build.docker.DockerFileBuilder;
import io.fabric8.maven.docker.build.maven.MavenBuildContext;
import io.fabric8.maven.docker.config.build.ArchiveCompression;
import io.fabric8.maven.docker.config.build.AssemblyConfiguration;
import io.fabric8.maven.docker.config.build.AssemblyMode;
import io.fabric8.maven.docker.config.build.BuildConfiguration;
import io.fabric8.maven.docker.config.maven.MavenAssemblyConfiguration;
import io.fabric8.maven.docker.util.DockerFileUtil;
import io.fabric8.maven.docker.util.EnvUtil;
import io.fabric8.maven.docker.util.Logger;
import org.apache.commons.io.IOUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Build;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.assembly.AssemblerConfigurationSource;
import org.apache.maven.plugins.assembly.InvalidAssemblerConfigurationException;
import org.apache.maven.plugins.assembly.archive.ArchiveCreationException;
import org.apache.maven.plugins.assembly.archive.AssemblyArchiver;
import org.apache.maven.plugins.assembly.format.AssemblyFormattingException;
import org.apache.maven.plugins.assembly.io.AssemblyReadException;
import org.apache.maven.plugins.assembly.io.AssemblyReader;
import org.apache.maven.plugins.assembly.model.Assembly;
import org.apache.maven.project.MavenProject;
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
import org.codehaus.plexus.interpolation.fixed.FixedStringSearchInterpolator;

/**
 * Tool for creating a docker image tar ball including a Dockerfile for building
 * a docker image.
 *
 * @author roland
 * @since 08.05.14
 */
@Component(role = DockerAssemblyManager.class, instantiationStrategy = "per-lookup")
public class DockerAssemblyManager {

    // Assembly name used also as build directory within outputBuildDir
    public static final String DOCKER_IGNORE = ".maven-dockerignore";
    public static final String DOCKER_EXCLUDE = ".maven-dockerexclude";
    public static final String DOCKER_INCLUDE = ".maven-dockerinclude";
    public static final String DOCKERFILE_NAME = "Dockerfile";

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
     * @param context Mojos parameters (used for finding the directories)
     * @param buildConfig configuration for how to build the image
     * @param finalCustomizer finalCustomizer to be applied to the tar archive
     * @return file holding the path to the created assembly tar file
     * @throws MojoExecutionException
     */
    public File createDockerTarArchive(String imageName, final MavenBuildContext context, final BuildConfiguration buildConfig, ArchiverCustomizer finalCustomizer, Logger log)
            throws IOException {

        final BuildDirs buildDirs = createBuildDirs(imageName, context);
        final AssemblyConfiguration assemblyConfig = buildConfig.getAssemblyConfiguration();

        final List<ArchiverCustomizer> archiveCustomizers = new ArrayList<>();

        // Build up assembly. In dockerfile mode this must be added explicitly in the Dockerfile with an ADD
        if (hasAssemblyConfiguration(assemblyConfig)) {
            createAssemblyArchive(assemblyConfig, context, buildDirs);
        }
        try {
            if (buildConfig.isDockerFileMode()) {
                // Use specified docker directory which must include a Dockerfile.
                final File dockerFile =
                    EnvUtil.prepareAbsoluteSourceDirPath(context, buildConfig.calculateDockerFilePath().getPath());
                if (!dockerFile.exists()) {
                    throw new IOException("Configured Dockerfile \"" +
                                          buildConfig.getDockerFile() + "\" (resolved to \"" + dockerFile + "\") doesn't exist");
                }

                FixedStringSearchInterpolator interpolator = DockerFileUtil.createInterpolator(context, buildConfig.getFilter());
                verifyGivenDockerfile(dockerFile, buildConfig, interpolator::interpolate, log);
                interpolateDockerfile(dockerFile, buildDirs, interpolator);
                // User dedicated Dockerfile from extra directory
                archiveCustomizers.add(archiver -> {
                    File contextDir = buildConfig.getContextDir() != null ? new File(buildConfig.getContextDir()) : dockerFile.getParentFile();
                    if (!contextDir.exists()) {
                        throw new IOException(String.format("Docker context directory %s doesn't exist", contextDir.getAbsolutePath()));
                    }
                    DefaultFileSet fileSet = DefaultFileSet.fileSet(contextDir);
                    addDockerIncludesExcludesIfPresent(fileSet, context);
                    // Exclude non-interpolated dockerfile from source tree
                    // Interpolated Dockerfile is already added as it was created into the output directory when
                    // using dir dir mode
                    excludeDockerfile(fileSet, dockerFile);

                    // If the content is added as archive, then we need to add the Dockerfile from the builddir
                    // directly to docker.tar (as the output builddir is not picked up in archive mode)
                    if (isArchive(assemblyConfig)) {
                        String name = dockerFile.getName();
                        archiver.addFile(new File(buildDirs.getOutputDirectory(), name), name);
                    }

                    archiver.addFileSet(fileSet);
                    return archiver;
                });
            } else {
                // Create custom docker file in output dir
                DockerFileBuilder builder = createDockerFileBuilder(buildConfig, assemblyConfig);
                builder.write(buildDirs.getOutputDirectory());
                // Add own Dockerfile
                final File dockerFile = new File(buildDirs.getOutputDirectory(), DOCKERFILE_NAME);
                archiveCustomizers.add(archiver -> {
                    archiver.addFile(dockerFile, DOCKERFILE_NAME);
                    return archiver;
                });
            }

            // If required make all files in the assembly executable
            if (assemblyConfig != null) {
                AssemblyConfiguration.PermissionMode mode = assemblyConfig.getPermissions();
                if (mode == AssemblyConfiguration.PermissionMode.exec ||
                    mode == AssemblyConfiguration.PermissionMode.auto && EnvUtil.isWindows()) {
                    archiveCustomizers.add(new AllFilesExecCustomizer(log));
                }
            }

            if (finalCustomizer != null) {
                archiveCustomizers.add(finalCustomizer);
            }

            return createBuildTarBall(buildDirs, archiveCustomizers, assemblyConfig, buildConfig.getCompression());

        } catch (IOException e) {
            throw new IOException(String.format("Cannot create %s in %s", DOCKERFILE_NAME, buildDirs.getOutputDirectory()), e);
        }
    }


    private void excludeDockerfile(DefaultFileSet fileSet, File dockerFile) {
        ArrayList<String> excludes =
            fileSet.getExcludes() != null ?
                new ArrayList<>(Arrays.asList(fileSet.getExcludes())) :
                new ArrayList<String>();
        excludes.add(dockerFile.getName());
        fileSet.setExcludes(excludes.toArray(new String[0]));
    }

    private void interpolateDockerfile(File dockerFile, BuildDirs params, FixedStringSearchInterpolator interpolator) throws IOException {
        File targetDockerfile = new File(params.getOutputDirectory(), dockerFile.getName());
        String dockerFileInterpolated = DockerFileUtil.interpolate(dockerFile, interpolator);
        try (Writer writer = new FileWriter(targetDockerfile)) {
            IOUtils.write(dockerFileInterpolated, writer);
        }
    }

    // visible for testing
    void verifyGivenDockerfile(File dockerFile, BuildConfiguration buildConfig, Function<String, String> interpolator, Logger log) throws IOException {
        AssemblyConfiguration assemblyConfig = buildConfig.getAssemblyConfiguration();
        if (assemblyConfig == null) {
            return;
        }

        String name = assemblyConfig.getName();
            for (String keyword : new String[] { "ADD", "COPY" }) {
                List<String[]> lines = DockerFileUtil.extractLines(dockerFile, keyword, interpolator);
                for (String[] line : lines) {
                    if (!line[0].startsWith("#")) {
                        // Skip command flags like --chown
                        int i;
                        for (i = 1; i < line.length; i++) {
                            String component = line[i];
                            if (!component.startsWith("--")) {
                                break;
                            }
                        }

                        // contains an ADD/COPY ... targetDir .... All good.
                        if (i < line.length && line[i].contains(name)) {
                            return;
                        }
                    }
                }
            }
        log.warn("Dockerfile %s does not contain an ADD or COPY directive to include assembly created at %s. Ignoring assembly.",
                 dockerFile.getPath(), name);
    }

    /**
     * Extract all files with a tracking archiver. These can be used to track changes in the filesystem and triggering
     * a rebuild of the image if needed ('docker:watch')
     */
    public AssemblyFiles getAssemblyFiles(String name, BuildConfiguration buildConfig, MavenBuildContext context, Logger log)
        throws InvalidAssemblerConfigurationException, ArchiveCreationException, AssemblyFormattingException, IOException {

        BuildDirs buildDirs = createBuildDirs(name, context);

        AssemblyConfiguration assemblyConfig = buildConfig.getAssemblyConfiguration();
        String assemblyName = assemblyConfig.getName();
        DockerAssemblyConfigurationSource source =
                        new DockerAssemblyConfigurationSource(context, buildDirs, assemblyConfig);
        Assembly assembly = getAssemblyConfig(assemblyConfig, source);


        synchronized (trackArchiver) {
            MappingTrackArchiver ta = (MappingTrackArchiver) trackArchiver;
            ta.init(log, assemblyName);
            assembly.setId("tracker");
            assemblyArchiver.createArchive(assembly, assemblyName, "track", source, false, null);
            return ta.getAssemblyFiles(context.getSession());
        }
    }

    private BuildDirs createBuildDirs(String imageName, MavenBuildContext context) {
        BuildDirs buildDirs = new BuildDirs(imageName, context);
        buildDirs.createDirs();
        return buildDirs;
    }

    private boolean hasAssemblyConfiguration(AssemblyConfiguration assemblyConfig) {
        boolean hasInlineAssembly = (assemblyConfig instanceof MavenAssemblyConfiguration) &&
                                    ((MavenAssemblyConfiguration) assemblyConfig).getInline() != null;
        return assemblyConfig != null &&
                (hasInlineAssembly ||
                 assemblyConfig.getDescriptor() != null ||
                 assemblyConfig.getDescriptorRef() != null);
    }

    private boolean isArchive(AssemblyConfiguration assemblyConfig) {
        return hasAssemblyConfiguration(assemblyConfig) &&
               assemblyConfig.getMode() != null &&
               assemblyConfig.getMode().isArchive();
    }

    public File createChangedFilesArchive(List<AssemblyFiles.Entry> entries, File assemblyDirectory,
                                          String imageName, MavenBuildContext context)
            throws IOException {
        BuildDirs dirs = createBuildDirs(imageName, context);
        try {
            File archive = new File(dirs.getTemporaryRootDirectory(), "changed-files.tar");
            File archiveDir = createArchiveDir(dirs);
            for (AssemblyFiles.Entry entry : entries) {
                File dest = prepareChangedFilesArchivePath(archiveDir,entry.getDestFile(),assemblyDirectory);
                FileUtils.copyFile(entry.getSrcFile(), dest);
            }
            return createChangedFilesTarBall(archive, archiveDir);
        } catch (IOException exp) {
            throw new IOException("Error while creating " + dirs.getTemporaryRootDirectory() +
                                  "/changed-files.tar: " + exp);
        }
    }

    private File prepareChangedFilesArchivePath(File archiveDir, File destFile, File assemblyDir) throws IOException {
        // Replace build target dir from destfile and add changed-files build dir instead
        String relativePath = PathTool.getRelativeFilePath(assemblyDir.getCanonicalPath(),destFile.getCanonicalPath());
        return new File(archiveDir,relativePath);
    }

    // Create final tar-ball to be used for building the archive to send to the Docker daemon
    private File createBuildTarBall(BuildDirs buildDirs, List<ArchiverCustomizer> archiverCustomizers,
                                    AssemblyConfiguration assemblyConfig, ArchiveCompression compression) throws IOException {
        File archive = new File(buildDirs.getTemporaryRootDirectory(), "docker-build." + compression.getFileSuffix());
        try {
            TarArchiver archiver = createBuildArchiver(buildDirs.getOutputDirectory(), archive, assemblyConfig);
            for (ArchiverCustomizer customizer : archiverCustomizers) {
                if (customizer != null) {
                    archiver = customizer.customize(archiver);
                }
            }
            archiver.setCompression(getTarCompressionMethod(compression));
            archiver.createArchive();
            return archive;
        } catch (NoSuchArchiverException e) {
            throw new IOException("No archiver for type 'tar' found", e);
        } catch (IOException e) {
            throw new IOException("Cannot create archive " + archive, e);
        }
    }

    private TarArchiver.TarCompressionMethod getTarCompressionMethod(ArchiveCompression compression) {
        if (compression == null) {
            return TarArchiver.TarCompressionMethod.none;
        }
        switch (compression) {
            case gzip:
                return TarArchiver.TarCompressionMethod.gzip;
            case bzip2:
                return TarArchiver.TarCompressionMethod.bzip2;
            case none:
            default:
                return TarArchiver.TarCompressionMethod.none;
        }
    }

    private void addDockerIncludesExcludesIfPresent(DefaultFileSet fileSet, MavenBuildContext params) throws IOException {
        addDockerExcludes(fileSet, params);
        addDockerIncludes(fileSet);
    }

    private void addDockerExcludes(DefaultFileSet fileSet, MavenBuildContext params) throws IOException {
        File directory = fileSet.getDirectory();
        List<String> excludes = new ArrayList<>();
        // Output directory will be always excluded
        excludes.add(params.getOutputDirectory() + "/**");
        for (String file : new String[] { DOCKER_EXCLUDE, DOCKER_IGNORE } ) {
            File dockerIgnore = new File(directory, file);
            if (dockerIgnore.exists()) {
                excludes.addAll(Arrays.asList(FileUtils.fileReadArray(dockerIgnore)));
                excludes.add(DOCKER_IGNORE);
            }
        }
        fileSet.setExcludes(excludes.toArray(new String[0]));
    }

    private void addDockerIncludes(DefaultFileSet fileSet) throws IOException {
        File directory = fileSet.getDirectory();
        File dockerInclude = new File(directory, DOCKER_INCLUDE);
        if (dockerInclude.exists()) {
            ArrayList<String> includes = new ArrayList<>(Arrays.asList(FileUtils.fileReadArray(dockerInclude)));
            fileSet.setIncludes(includes.toArray(new String[0]));
        }
    }

    private File createChangedFilesTarBall(File archive, File archiveDir) throws IOException {
        try {
            TarArchiver archiver = (TarArchiver) archiverManager.getArchiver("tar");
            archiver.setLongfile(TarLongFileMode.posix);
            archiver.addFileSet(DefaultFileSet.fileSet(archiveDir));
            archiver.setDestFile(archive);
            archiver.createArchive();
            return archive;
        } catch (NoSuchArchiverException e) {
            throw new IOException("No archiver for type 'tar' found", e);
        } catch (IOException e) {
            throw new IOException("Cannot create archive " + archive, e);
        } catch (RuntimeException e) {
            e.printStackTrace();
            throw e;
        }
    }

    private File createArchiveDir(BuildDirs dirs) throws IOException {
        File archiveDir = new File(dirs.getTemporaryRootDirectory(), "changed-files");
        if (archiveDir.exists()) {
            // Remove old stuff to
            FileUtils.cleanDirectory(archiveDir);
        } else {
            if (!archiveDir.mkdir()) {
                throw new IOException("Cannot create " + archiveDir);
            }
        }
        return archiveDir;
    }

    private TarArchiver createBuildArchiver(File outputDir, File archive, AssemblyConfiguration assemblyConfig) throws NoSuchArchiverException {
        TarArchiver archiver = (TarArchiver) archiverManager.getArchiver("tar");
        archiver.setLongfile(TarLongFileMode.posix);

        AssemblyMode mode = assemblyConfig != null ? assemblyConfig.getMode() : null;
        if (mode != null && mode.isArchive()) {
            DefaultArchivedFileSet archiveSet =
                    DefaultArchivedFileSet.archivedFileSet(new File(outputDir,  assemblyConfig.getName() + "." + mode.getExtension()));
            archiveSet.setPrefix(assemblyConfig.getName() + "/");
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
    DockerFileBuilder createDockerFileBuilder(BuildConfiguration buildConfig, AssemblyConfiguration assemblyConfig) {
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
            builder.add(assemblyConfig.getName(), "")
                   .basedir(assemblyConfig.getTargetDir())
                   .assemblyUser(assemblyConfig.getUser())
                   .exportTargetDir(assemblyConfig.getExportTargetDir());
        } else {
            builder.exportTargetDir(false);
        }

        builder.baseImage(buildConfig.getFrom());

        if (buildConfig.getHealthCheck() != null) {
            builder.healthCheck(buildConfig.getHealthCheck());
        }

        if (buildConfig.getCmd() != null){
            builder.cmd(buildConfig.getCmd());
        }

        if (buildConfig.getEntryPoint() != null){
            builder.entryPoint(buildConfig.getEntryPoint());
        }

        if (buildConfig.getOptimise() != null && buildConfig.getOptimise()) {
            builder.optimise();
        }

        return builder;
    }

    private void createAssemblyArchive(AssemblyConfiguration assemblyConfig, MavenBuildContext params, BuildDirs buildDirs)
            throws IOException {
        DockerAssemblyConfigurationSource source = new DockerAssemblyConfigurationSource(params, buildDirs, assemblyConfig);
        Assembly assembly = getAssemblyConfig(assemblyConfig, source);

        AssemblyMode buildMode = assemblyConfig.getMode() != null ? assemblyConfig.getMode() : AssemblyMode.dir;
        File originalArtifactFile = null;
        try {
            originalArtifactFile = ensureThatArtifactFileIsSet(params.getProject());
            assembly.setId("docker");
            assemblyArchiver.createArchive(assembly, assemblyConfig.getName(), buildMode.getExtension(), source, false, null);
        } catch (ArchiveCreationException | AssemblyFormattingException e) {
            String error = "Failed to create assembly for docker image " +
                           " (with mode '" + buildMode + "'): " + e.getMessage() + ".";
            if (params.getProject().getArtifact().getFile() == null) {
                error += " If you include the build artifact please ensure that you have " +
                         "built the artifact before with 'mvn package' (should be available in the target/ dir). " +
                         "Please see the documentation (section \"Assembly\") for more information.";
            }
            throw new IOException(error, e);
        } catch (InvalidAssemblerConfigurationException e) {
            throw new IOException("Assembly: " + assembly.getId() + " is not configured correctly: " + e.getMessage(), e);
        } finally {
            setArtifactFile(params.getProject(), originalArtifactFile);
        }
    }

    // Set an artifact file if it is missing. This workaround the issues
    // mentioned first in https://issues.apache.org/jira/browse/MASSEMBLY-94 which requires the package
    // phase to run so set the ArtifactFile. There is no good solution, so we are trying
    // to be very defensive and add a workaround for some situation which won't work for every occasion.
    // Unfortunately a plain forking of the Maven lifecycle is not good enough, since the MavenProject
    // gets cloned before the fork, and the 'package' plugin (e.g. JarPlugin) sets the file on the cloned
    // object which is then not available for the BuildMojo (there the file is still null leading to the
    // the "Cannot include project artifact: ... The following patterns were never triggered in this artifact inclusion filter: <artifact>"
    // warning with an error following.
    private File ensureThatArtifactFileIsSet(MavenProject project) {
        Artifact artifact = project.getArtifact();
        if (artifact == null) {
            return null;
        }
        File oldFile = artifact.getFile();
        if (oldFile != null) {
            return oldFile;
        }
        Build build = project.getBuild();
        if (build == null) {
            return null;
        }
        String finalName = build.getFinalName();
        String target = build.getDirectory();
        if (finalName == null || target == null) {
            return null;
        }
        File artifactFile = new File(target, finalName + "." + project.getPackaging());
        if (artifactFile.exists() && artifactFile.isFile()) {
            setArtifactFile(project, artifactFile);
        }
        return null;
    }

    private void setArtifactFile(MavenProject project, File artifactFile) {
        Artifact artifact = project.getArtifact();
        if (artifact != null) {
            artifact.setFile(artifactFile);
        }
    }

    private Assembly getAssemblyConfig(AssemblyConfiguration assemblyConfig, DockerAssemblyConfigurationSource source)
            throws IOException {
        Assembly assembly = null;
        if (assemblyConfig instanceof MavenAssemblyConfiguration) {
            assembly = ((MavenAssemblyConfiguration) assemblyConfig).getInline();
        }
        if (assembly == null) {
            assembly = extractAssembly(source);
        }
        return assembly;
    }

    private Assembly extractAssembly(AssemblerConfigurationSource config) throws IOException {
        try {
            List<Assembly> assemblies = assemblyReader.readAssemblies(config);
            if (assemblies.size() != 1) {
                throw new IllegalArgumentException("Only one assembly can be used for creating a Docker base image (and not "
                        + assemblies.size() + ")");
            }
            return assemblies.get(0);
        }
        catch (AssemblyReadException e) {
            throw new IOException("Error reading assembly: " + e.getMessage(), e);
        }
        catch (InvalidAssemblerConfigurationException e) {
            throw new IOException("Docker assembly configuration is invalid: " + e.getMessage(), e);
        }
    }

}
