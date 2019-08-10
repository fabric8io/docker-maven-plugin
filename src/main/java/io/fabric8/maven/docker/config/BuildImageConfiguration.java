package io.fabric8.maven.docker.config;

import java.io.File;
import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.fabric8.maven.docker.util.*;
import org.apache.maven.plugins.annotations.Parameter;

import javax.annotation.Nonnull;

/**
 * @author roland
 * @since 02.09.14
 */
public class BuildImageConfiguration implements Serializable {

    public static final String DEFAULT_FILTER = "${*}";
    public static final String DEFAULT_CLEANUP = "try";

    /**
     * Directory is used as build context.
     * If not specified, dockerfile's parent directory is used as build context.
     */
    @Parameter
    private String contextDir;

    /**
     * Directory holding an external Dockerfile which is used to build the
     * image. This Dockerfile will be enriched by the addition build configuration
     */
    @Parameter
    @Deprecated
    private String dockerFileDir;

    /**
     * Path to a dockerfile to use.
     * Multiple different Dockerfiles can be specified that way. If set overwrites a possibly givem
     * <code>dockerFileDir</code>
     */
    @Parameter
    private String dockerFile;

    /**
     * Path to a docker archive to load an image instead of building from scratch.
     * Note only either dockerFile/dockerFileDir or
     * dockerArchive can be used.
     */
    @Parameter
    private String dockerArchive;

    /**
     * Pattern for the image name we expect to find in the dockerArchive.
     *
     * If set, the archive is scanned prior to sending to Docker and checked to
     * ensure a matching name is found linked to one of the images in the archive.
     * After loading, the image with the matching name will be tagged with the
     * image name configured in this project.
     */
    @Parameter
    private String loadNamePattern;

    /**
     * How interpolation of a dockerfile should be performed
     */
    @Parameter
    private String filter;

    /**
     * Base Image
     */
    @Parameter
    private String from;

    /**
     * Extended version for <from>
     */
    @Parameter
    private Map<String, String> fromExt;

    @Parameter
    private List<String> cacheFrom;

    @Parameter
    private String registry;

    @Parameter
    private String maintainer;

    @Parameter
    private List<String> ports;

    /**
     * Policy for pulling the base images
     */
    @Parameter
    private String imagePullPolicy;

    /**
     * SHELL excutable with params
     */
    @Parameter
    private Arguments shell;

    /**
     * RUN Commands within Build/Image
     */
    @Parameter
    private List<String> runCmds;

    @Parameter
    private String cleanup;

    @Deprecated
    @Parameter
    private Boolean nocache;

    @Parameter
    private Boolean noCache;

    @Parameter
    private Boolean optimise;

    @Parameter
    private List<String> volumes;

    @Parameter
    private List<String> tags;

    @Parameter
    private Map<String, String> env;

    @Parameter
    private Map<String, String> labels;

    @Parameter
    private Map<String, String> args;

    @Parameter
    private Arguments entryPoint;

    @Parameter
    @Deprecated
    private String command;

    @Parameter
    private String workdir;

    @Parameter
    private Arguments cmd;

    @Parameter
    private String user;

    @Parameter
    private HealthCheckConfiguration healthCheck;

    @Parameter
    private AssemblyConfiguration assembly;

    @Parameter
    private Boolean skip;

    @Parameter
    private ArchiveCompression compression = ArchiveCompression.none;

    @Parameter
    private Map<String,String> buildOptions;

    // Path to Dockerfile to use, initialized lazily ....
    private File dockerFileFile, dockerArchiveFile;

    public BuildImageConfiguration() {}

    public boolean isDockerFileMode() {
        return dockerFileFile != null;
    }

    public String getLoadNamePattern() {
        return loadNamePattern;
    }

    public File getContextDir() {
        if (!isDockerFileMode()) {
            return null;
        }
        if (contextDir != null) {
            return new File(contextDir);
        }
        if (getDockerFile().getParentFile() == null) {
            return new File("");
        }
        return getDockerFile().getParentFile();
    }

    public String getContextDirRaw() {
        return contextDir;
    }

    public File getDockerFile() {
        return dockerFileFile;
    }

    public File getDockerArchive() {
        return dockerArchiveFile;
    }

    public String getDockerFileRaw() {
        return dockerFile;
    }

    public String getDockerArchiveRaw() {
        return dockerArchive;
    }

    public String getDockerFileDirRaw() {
        return dockerFileDir;
    }

    public String getFilter() {
        return filter != null ? filter : DEFAULT_FILTER;
    }

    public String getFilterRaw() {
        return filter;
    }

    public String getFrom() {
        if (from == null && getFromExt() != null) {
            return getFromExt().get("name");
        }
        return from;
    }

    public Map<String, String> getFromExt() {
        return fromExt;
    }

    public List<String> getCacheFrom() {
        return cacheFrom;
    }

    public String getRegistry() {
        return registry;
    }

    public String getMaintainer() {
        return maintainer;
    }

    public String getWorkdir() {
        return workdir;
    }

    public AssemblyConfiguration getAssemblyConfiguration() {
        return assembly;
    }

    @Nonnull
    public List<String> getPorts() {
        return EnvUtil.removeEmptyEntries(ports);
    }

    public String getImagePullPolicy() {
        return imagePullPolicy;
    }

    @Nonnull
    public List<String> getVolumes() {
        return EnvUtil.removeEmptyEntries(volumes);
    }

    @Nonnull
    public List<String> getTags() {
        return EnvUtil.removeEmptyEntries(tags);
    }

    public Map<String, String> getEnv() {
        return env;
    }

    public Map<String, String> getLabels() {
        return labels;
    }

    public Arguments getCmd() {
        return cmd;
    }

    @Deprecated
    public String getCommand() {
        return command;
    }

    public String getCleanup() {
        return cleanup;
    }

    public CleanupMode cleanupMode() {
        return CleanupMode.parse(cleanup != null ? cleanup : DEFAULT_CLEANUP);
    }

    public boolean noCache() {
        if (noCache != null) {
            return noCache;
        }
        if (nocache != null) {
            return nocache;
        }
        return false;
    }

    public boolean optimise() {
        return optimise != null ? optimise : false;
    }

    public boolean skip() {
        return skip != null ? skip : false;
    }

    public Boolean getNoCache() {
        return noCache != null ? noCache : nocache;
    }

    public Boolean getOptimise() {
        return optimise;
    }

    public Boolean getSkip() {
        return skip;
    }

    public ArchiveCompression getCompression() {
        return compression;
    }

    public Map<String, String> getBuildOptions() {
        return buildOptions;
    }

    public Arguments getEntryPoint() {
        return entryPoint;
    }

    public Arguments getShell() {
        return shell;
    }

    @Nonnull
    public List<String> getRunCmds() {
        return EnvUtil.removeEmptyEntries(runCmds);
    }

    public String getUser() {
      return user;
    }

    public HealthCheckConfiguration getHealthCheck() {
        return healthCheck;
    }

    public Map<String, String> getArgs() {
        return args;
    }

    public File getAbsoluteContextDirPath(MojoParameters mojoParams) {
        return EnvUtil.prepareAbsoluteSourceDirPath(mojoParams, getContextDir().getPath());
    }

    public File getAbsoluteDockerFilePath(MojoParameters mojoParams) {
        return EnvUtil.prepareAbsoluteSourceDirPath(mojoParams, getDockerFile().getPath());
    }

    public File getAbsoluteDockerTarPath(MojoParameters mojoParams) {
        return EnvUtil.prepareAbsoluteSourceDirPath(mojoParams, getDockerArchive().getPath());
    }

    public static class Builder {
        private final BuildImageConfiguration config;

        public Builder() {
            this(null);
        }

        public Builder(BuildImageConfiguration that) {
            if (that == null) {
                this.config = new BuildImageConfiguration();
            } else {
                this.config = DeepCopy.copy(that);
            }
        }

        public Builder contextDir(String dir) {
            config.contextDir = dir;
            return this;
        }

        public Builder dockerFileDir(String dir) {
            config.dockerFileDir = dir;
            return this;
        }

        public Builder dockerFile(String file) {
            config.dockerFile = file;
            return this;
        }

        public Builder dockerArchive(String archive) {
            config.dockerArchive = archive;
            return this;
        }

        public Builder loadNamePattern(String archiveEntryRepoTagPattern) {
            config.loadNamePattern = archiveEntryRepoTagPattern;
            return this;
        }

        public Builder filter(String filter) {
            config.filter = filter;
            return this;
        }

        public Builder from(String from) {
            config.from = from;
            return this;
        }

        public Builder fromExt(Map<String, String> fromExt) {
            config.fromExt = fromExt;
            return this;
        }

        public Builder cacheFrom(String cacheFrom, String ...more) {
            if (more == null || more.length == 0) {
                return cacheFrom(Collections.singletonList(cacheFrom));
            }

            List<String> list = new ArrayList<>();
            list.add(cacheFrom);
            list.addAll(Arrays.asList(more));
            return cacheFrom(list);
        }

        public Builder cacheFrom(Collection<String> cacheFrom) {
            config.cacheFrom = cacheFrom != null ? new ArrayList<>(cacheFrom) : null;
            return this;
        }

        public Builder registry(String registry) {
            config.registry = registry;
            return this;
        }

        public Builder maintainer(String maintainer) {
            config.maintainer = maintainer;
            return this;
        }

        public Builder workdir(String workdir) {
            config.workdir = workdir;
            return this;
        }

        public Builder assembly(AssemblyConfiguration assembly) {
            config.assembly = assembly;
            return this;
        }

        public Builder ports(List<String> ports) {
            config.ports = ports;
            return this;
        }

        public Builder imagePullPolicy(String imagePullPolicy) {
            config.imagePullPolicy = imagePullPolicy;
            return this;
        }

        public Builder shell(Arguments shell) {
            if(shell != null) {
                config.shell = shell;
            }

            return this;
        }

        public Builder runCmds(List<String> theCmds) {
            if (theCmds == null) {
                config.runCmds = new ArrayList<>();
            } else {
                config.runCmds = theCmds;
            }
            return this;
        }

        public Builder volumes(List<String> volumes) {
            config.volumes = volumes;
            return this;
        }

        public Builder tags(List<String> tags) {
            config.tags = tags;
            return this;
        }

        public Builder env(Map<String, String> env) {
            config.env = env;
            return this;
        }

        public Builder args(Map<String, String> args) {
            config.args = args;
            return this;
        }

        public Builder labels(Map<String, String> labels) {
            config.labels = labels;
            return this;
        }

        public Builder cmd(Arguments cmd) {
            if (cmd != null) {
                config.cmd = cmd;
            }
            return this;
        }

        public Builder cleanup(String cleanup) {
            config.cleanup = cleanup;
            return this;
        }

        public Builder compression(String compression) {
            if (compression == null) {
                config.compression = ArchiveCompression.none;
            } else {
                config.compression = ArchiveCompression.valueOf(compression);
            }
            return this;
        }

        public Builder noCache(Boolean noCache) {
            config.noCache = noCache;
            return this;
        }

        public Builder optimise(Boolean optimise) {
            config.optimise = optimise;
            return this;
        }

        public Builder entryPoint(Arguments entryPoint) {
            if (entryPoint != null) {
                config.entryPoint = entryPoint;
            }
            return this;
        }

        public Builder user(String user) {
            config.user = user;
            return this;
        }

        public Builder healthCheck(HealthCheckConfiguration healthCheck) {
            config.healthCheck = healthCheck;
            return this;
        }

        public Builder skip(Boolean skip) {
            config.skip = skip;
            return this;
        }

        public Builder buildOptions(Map<String,String> buildOptions) {
            config.buildOptions = buildOptions;
            return this;
        }

        public BuildImageConfiguration build() {
            return config;
        }
    }

    public String initAndValidate(Logger log) throws IllegalArgumentException {
        if (entryPoint != null) {
            entryPoint.validate();
        }
        if (cmd != null) {
            cmd.validate();
        }
        if (healthCheck != null) {
            healthCheck.validate();
        }

        if (command != null) {
            log.warn("<command> in the <build> configuration is deprecated and will be be removed soon");
            log.warn("Please use <cmd> with nested <shell> or <exec> sections instead.");
            log.warn("");
            log.warn("More on this is explained in the user manual: ");
            log.warn("https://github.com/fabric8io/docker-maven-plugin/blob/master/doc/manual.md#start-up-arguments");
            log.warn("");
            log.warn("Migration is trivial, see changelog to version 0.12.0 -->");
            log.warn("https://github.com/fabric8io/docker-maven-plugin/blob/master/doc/changelog.md");
            log.warn("");
            log.warn("For now, the command is automatically translated for you to the shell form:");
            log.warn("   <cmd>%s</cmd>", command);
        }

        initDockerFileFile(log);

        if (cacheFrom != null && !cacheFrom.isEmpty()) {
            // cachefrom query param was introduced in v1.25
            return "1.25";
        } else if (healthCheck != null) {
            // HEALTHCHECK support added later
            return "1.24";
        } else if (args != null) {
            // ARG support came in later
            return "1.21";
        } else {
            return null;
        }
    }

    // Initialize the dockerfile location and the build mode
    private void initDockerFileFile(Logger log) {
        // can't have dockerFile/dockerFileDir and dockerArchive
        if ((dockerFile != null || dockerFileDir != null) && dockerArchive != null) {
            throw new IllegalArgumentException("Both <dockerFile> (<dockerFileDir>) and <dockerArchive> are set. " +
                                               "Only one of them can be specified.");
        }
        dockerFileFile = findDockerFileFile(log);

        if (dockerArchive != null) {
            dockerArchiveFile = new File(dockerArchive);
        }
    }

    private File findDockerFileFile(Logger log) {
        if(dockerFileDir != null && contextDir != null) {
            log.warn("Both contextDir (%s) and deprecated dockerFileDir (%s) are configured. Using contextDir.", contextDir, dockerFileDir);
        }

        if (dockerFile != null) {
            File dFile = new File(dockerFile);
            if (dockerFileDir == null && contextDir == null) {
                return dFile;
            } else {
                if(contextDir != null) {
                    if (dFile.isAbsolute()) {
                        return dFile;
                    }
                    return new File(contextDir, dockerFile);
                }

                if (dockerFileDir != null) {
                    if (dFile.isAbsolute()) {
                        throw new IllegalArgumentException("<dockerFile> can not be absolute path if <dockerFileDir> also set.");
                    }
                    log.warn("dockerFileDir parameter is deprecated, please migrate to contextDir");
                    return new File(dockerFileDir, dockerFile);
                }
            }
        }


        if (contextDir != null) {
            return new File(contextDir, "Dockerfile");
        }

        if (dockerFileDir != null) {
            return new File(dockerFileDir, "Dockerfile");
        }

        // TODO: Remove the following deprecated handling section
        if (dockerArchive == null) {
            String deprecatedDockerFileDir =
                getAssemblyConfiguration() != null ?
                    getAssemblyConfiguration().getDockerFileDir() :
                    null;
            if (deprecatedDockerFileDir != null) {
                log.warn("<dockerFileDir> in the <assembly> section of a <build> configuration is deprecated");
                log.warn("Please use <dockerFileDir> or <dockerFile> directly within the <build> configuration instead");
                return new File(deprecatedDockerFileDir,"Dockerfile");
            }
        }

        // No dockerfile mode
        return null;
    }
}
