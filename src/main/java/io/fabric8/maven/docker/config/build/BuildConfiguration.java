package io.fabric8.maven.docker.config.build;

import java.io.File;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.lang3.SerializationUtils;

/**
 * @author roland
 * @since 02.09.14
 */
public class BuildConfiguration implements Serializable {

    /**
     * Directory used as the contexst directory, e.g. for a docker build.
     */
    private String contextDir;

    /**
     * Path to a dockerfile to use. Its parent directory is used as build context (i.e. as <code>dockerFileDir</code>).
     * Multiple different Dockerfiles can be specified that way. If set overwrites a possibly given
     * <code>contextDir</code>
     */
    private String dockerFile;

    /**
     * Path to a docker archive to load an image instead of building from scratch.
     * Note only either dockerFile/dockerFileDir or
     * dockerArchive can be used.
     */
    private String dockerArchive;

    /**
     * How interpolation of a dockerfile should be performed
     */
    private String filter;

    /**
     * Base Image
     */
    private String from;

    /**
     * Extended version for <from>
     */
    private Map<String, String> fromExt;

    private String registry;

    private String maintainer;

    private List<String> ports;

    /**
     * Policy for pulling the base images
     */
    private String imagePullPolicy;

    /**
     * RUN Commands within Build/Image
     */
    private List<String> runCmds;

    private String cleanup;

    private Boolean nocache;

    private Boolean optimise;

    private List<String> volumes;

    private List<String> tags;

    private Map<String, String> env;

    private Map<String, String> labels;

    private Map<String, String> args;

    private Arguments entryPoint;

    private String workdir;

    private Arguments cmd;

    private String user;

    private HealthCheckConfiguration healthCheck;

    private AssemblyConfiguration assembly;

    private Boolean skip;

    private ArchiveCompression compression = ArchiveCompression.none;

    private Map<String,String> buildOptions;

    public BuildConfiguration() {}

    public boolean isDockerFileMode() {
        return dockerFile != null || contextDir != null;
    }

    public String getDockerFile() {
        return dockerFile;
    }

    public String getDockerArchive() {
        return dockerArchive;
    }

    public String getContextDir() {
        return contextDir;
    }

    public String getFilter() {
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
        return removeEmptyEntries(ports);
    }

    public String getImagePullPolicy() {
        return imagePullPolicy;
    }

    public List<String> getVolumes() {
        return removeEmptyEntries(volumes);
    }

    public List<String> getTags() {
        return removeEmptyEntries(tags);
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

    public String getCleanupMode() {
        return cleanup;
    }

    public Boolean getNoCache() {
        return nocache;
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

    public List<String> getRunCmds() {
        return removeEmptyEntries(runCmds);
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

    // ===========================================================================================
    public static class Builder {


        protected BuildConfiguration config;

        public Builder() {
            this(null);
        }

        public Builder(BuildConfiguration that) {
            if (that == null) {
                this.config = new BuildConfiguration();
            } else {
                this.config = SerializationUtils.clone(that);
            }
        }

        public Builder contextDir(String dir) {
            config.contextDir = dir;
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

        public Builder runCmds(List<String> theCmds) {
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
                config.compression = null;
            } else {
                config.compression = ArchiveCompression.valueOf(compression);
            }
            return this;
        }

        public Builder nocache(Boolean nocache) {
            config.nocache = nocache;
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

        public BuildConfiguration build() {
            return config;
        }
    }

    public String validate() throws IllegalArgumentException {
        if (entryPoint != null) {
            entryPoint.validate();
        }
        if (cmd != null) {
            cmd.validate();
        }
        if (healthCheck != null) {
            healthCheck.validate();
        }

        // can't have dockerFile/dockerFileDir and dockerArchive
        if ((dockerFile != null || contextDir != null) && dockerArchive != null) {
            throw new IllegalArgumentException("Both <dockerFile> (<dockerFileDir>) and <dockerArchive> are set. " +
                                               "Only one of them can be specified.");
        }

        if (healthCheck != null) {
            // HEALTHCHECK support added later
            return "1.24";
        } else if (args != null) {
            // ARG support came in later
            return "1.21";
        } else {
            return null;
        }
    }

    public File calculateDockerFilePath() {
        if (dockerFile != null) {
            File dFile = new File(dockerFile);
            if (contextDir == null) {
                return dFile;
            }
            if (dFile.isAbsolute()) {
                return dFile;
            }
            if (System.getProperty("os.name").toLowerCase().contains("windows") &&
                !isValidWindowsFileName(dockerFile)) {
                throw new IllegalArgumentException(String.format("Invalid Windows file name %s for <dockerFile>", dockerFile));
            }
            return new File(contextDir, dFile.getPath());
        }

        if (contextDir != null) {
            return new File(contextDir, "Dockerfile");
        }

        // No dockerfile mode
        throw new IllegalArgumentException("Can't calculate a docker file path if neither dockerFile nor contextDir is specified");
    }

    // ===============================================================================================================

    private List<String> removeEmptyEntries(List<String> list) {
        if (list == null) {
            return Collections.emptyList();
        }
        return list.stream()
                   .filter(Objects::nonNull)
                   .map(String::trim)
                   .filter(s -> !s.isEmpty())
                   .collect(Collectors.toList());
    }


   /**
     * Validate that the provided filename is a valid Windows filename.
     *
     * The validation of the Windows filename is copied from stackoverflow: https://stackoverflow.com/a/6804755
     *
     * @param filename the filename
     * @return filename is a valid Windows filename
     */
    boolean isValidWindowsFileName(String filename) {
        Pattern pattern = Pattern.compile(
            "# Match a valid Windows filename (unspecified file system).          \n" +
            "^                                # Anchor to start of string.        \n" +
            "(?!                              # Assert filename is not: CON, PRN, \n" +
            "  (?:                            # AUX, NUL, COM1, COM2, COM3, COM4, \n" +
            "    CON|PRN|AUX|NUL|             # COM5, COM6, COM7, COM8, COM9,     \n" +
            "    COM[1-9]|LPT[1-9]            # LPT1, LPT2, LPT3, LPT4, LPT5,     \n" +
            "  )                              # LPT6, LPT7, LPT8, and LPT9...     \n" +
            "  (?:\\.[^.]*)?                  # followed by optional extension    \n" +
            "  $                              # and end of string                 \n" +
            ")                                # End negative lookahead assertion. \n" +
            "[^<>:\"/\\\\|?*\\x00-\\x1F]*     # Zero or more valid filename chars.\n" +
            "[^<>:\"/\\\\|?*\\x00-\\x1F .]    # Last char is not a space or dot.  \n" +
            "$                                # Anchor to end of string.            ",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.COMMENTS);
        Matcher matcher = pattern.matcher(filename);
        return matcher.matches();
    }

}
