package io.fabric8.maven.docker.config;

import java.io.File;
import java.util.*;

import io.fabric8.maven.docker.util.EnvUtil;
import io.fabric8.maven.docker.util.Logger;
import io.fabric8.maven.docker.util.MojoParameters;

/**
 * @author roland
 * @since 02.09.14
 */
public class BuildImageConfiguration {

    /**
     * Directory holding an external Dockerfile which is used to build the
     * image. This Dockerfile will be enriched by the addition build configuration
     *
     * @parameter
     */
    private String dockerFileDir;

    /**
     * Path to a dockerfile to use. Its parent directory is used as build context (i.e. as <code>dockerFileDir</code>).
     * Multiple different Dockerfiles can be specified that way. If set overwrites a possibly givem
     * <code>dockerFileDir</code>
     *
     * @parameter
     */
    private String dockerFile;

    // Base Image name of the data image to use.
    /**
     * @parameter
     */
    private String from;

    // Extended version for <from>
    /**
     * @parameter
     */
    private Map<String, String> fromExt;

    /**
     * @parameter
     */
    private String registry;

    /**
     * @parameter
     */
    private String maintainer;

    /**
     * @parameter
     */
    private List<String> ports;

    /**
     * RUN Commands within Build/Image
     * @parameter
     */
    private List<String> runCmds;

    /**
     * @parameter default-value="try"
     */
    private String cleanup = "try";

    /**
     * @parameter default-value="false"
     */
    private boolean nocache = false;

    /**
     * @parameter default-value="false"
     */
    private boolean optimise = false;

    /**
     * @parameter
     */
    private List<String> volumes;

    /**
     * @parameter
     */
    private List<String> tags;

    /**
     * @parameter
     */
    private Map<String, String> env;

    /**
     * @parameter
     */
    private Map<String, String> labels;

    /**
     * @parameter
     */
    private Map<String, String> args;

    /**
     * @parameter
     */
    private Arguments entryPoint;

    /**
     * @parameter
     * @deprecated
     */
    private String command;

    /**
     * @parameter
     */
    private String workdir;

    /**
     * @parameter
     */
    private Arguments cmd;

    /** @parameter */
    private String user;

    /**
     * @parameter
     */
    private AssemblyConfiguration assembly;

    /**
     * @parameter
     */
    private boolean skip = false;

    /**
     * @parameter
     */
    private BuildTarArchiveCompression compression = BuildTarArchiveCompression.none;

    // Path to Dockerfile to use, initialized lazily ....
    File dockerFileFile;
    private boolean dockerFileMode;

    public BuildImageConfiguration() {}

    public boolean isDockerFileMode() {
        return dockerFileMode;
    }

    public File getDockerFile() {
        return dockerFileFile;
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

    public List<String> getPorts() {
        return ports;
    }

    public List<String> getVolumes() {
        return volumes != null ? volumes : Collections.<String>emptyList();
    }

    public List<String> getTags() {
        return tags != null ? tags : Collections.<String>emptyList();
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

    public CleanupMode cleanupMode() {
        return CleanupMode.parse(cleanup);
    }

    public boolean nocache() {
        return nocache;
    }

    public boolean optimise() {
        return optimise;
    }

    public boolean skip() {
        return skip;
    }

    public BuildTarArchiveCompression getCompression() {
        return compression;
    }

    public Arguments getEntryPoint() {
        return entryPoint;
    }

    public List<String> getRunCmds() {
        return runCmds;
    }

    public String getUser() {
      return user;
    }

    public Map<String, String> getArgs() {
        return args;
    }

    public File getAbsoluteDockerFilePath(MojoParameters mojoParams) {
        return EnvUtil.prepareAbsoluteSourceDirPath(mojoParams, getDockerFile().getPath());
    }

    public static class Builder {
        private final BuildImageConfiguration config = new BuildImageConfiguration();

        public Builder dockerFileDir(String dir) {
            config.dockerFileDir = dir;
            return this;
        }

        public Builder dockerFile(String file) {
            config.dockerFile = file;
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

        public Builder runCmds(List<String> theCmds) {
            if (config.runCmds == null) {
                config.runCmds = new ArrayList<>();
            }
            else
            	config.runCmds = theCmds;
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

        public Builder cmd(String cmd) {
            if (cmd != null) {
                config.cmd = new Arguments(cmd);
            }
            return this;
        }

        public Builder cleanup(String cleanup) {
            config.cleanup = cleanup;
            return this;
        }

        public Builder nocache(String nocache) {
            if (nocache != null) {
                config.nocache = Boolean.valueOf(nocache);
            }
            return this;
        }

        public Builder optimise(String optimise) {
            if (optimise != null) {
                config.optimise = Boolean.valueOf(optimise);
            }
            return this;
        }

        public Builder entryPoint(String entryPoint) {
            if (entryPoint != null) {
                config.entryPoint = new Arguments(entryPoint);
            }
            return this;
        }

        public Builder user(String user) {
            config.user = user;
            return this;
        }

        public Builder skip(String skip) {
            if (skip != null) {
                config.skip = Boolean.valueOf(skip);
            }
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

        if (args != null) {
            // ARG support came in later
            return "1.21";
        } else {
            return null;
        }
    }

    // Initialize the dockerfile location and the build mode
    private void initDockerFileFile(Logger log) {
        if (dockerFile != null) {
            dockerFileFile = new File(dockerFile);
            dockerFileMode = true;
        } else if (dockerFileDir != null) {
            dockerFileFile = new File(dockerFileDir, "Dockerfile");
            dockerFileMode = true;
        } else {
            String deprecatedDockerFileDir = getAssemblyConfiguration() != null ?
                getAssemblyConfiguration().getDockerFileDir() :
                null;
            if (deprecatedDockerFileDir != null) {
                log.warn("<dockerFileDir> in the <assembly> section of a <build> configuration is deprecated");
                log.warn("Please use <dockerFileDir> or <dockerFile> directly within the <build> configuration instead");
                dockerFileFile = new File(deprecatedDockerFileDir,"Dockerfile");
                dockerFileMode = true;
            } else {
                dockerFileMode = false;
            }
        }
    }
}
