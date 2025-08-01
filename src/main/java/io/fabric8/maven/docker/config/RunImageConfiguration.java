package io.fabric8.maven.docker.config;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;

import io.fabric8.maven.docker.util.DeepCopy;
import io.fabric8.maven.docker.util.EnvUtil;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * @author roland
 * @since 02.09.14
 */
public class RunImageConfiguration implements Serializable {

    static final RunImageConfiguration DEFAULT = new RunImageConfiguration();

    /**
     * Environment variables to set when starting the container. key: variable name, value: env value
     */
    @Parameter
    private Map<String, String> env;

    @Parameter
    private Map<String, String> labels;

    // Path to a property file holding environment variables
    @Parameter
    private String envPropertyFile;

    // Command to execute in container
    @Parameter
    private Arguments cmd;

    // container domain name
    @Parameter
    private String domainname;

    // container domain name
    @Parameter
    private List<String> dependsOn;

    // healthcheck
    @Parameter
    private HealthCheckConfiguration healthCheckConfiguration;

    /**
     * container entry point
     *
     * @deprecated This field would be removed in upcoming releases. Use <code>entryPoint</code> instead.
     */
    @Parameter
    @Deprecated
    private Arguments entrypoint;

    @Parameter
    private Arguments entryPoint;

    // container hostname
    @Parameter
    private String hostname;

    // container user
    @Parameter
    private String user;

    @Parameter
    private String userns;

    // working directory
    @Parameter
    private String workingDir;

    // Size of /dev/shm in bytes
    /**
     * @parameter
     */
    private Long shmSize;

    // memory in bytes
    @Parameter
    private Long memory;

    // total memory (swap + ram) in bytes; set equal to memory to disable; set to -1 to allow unlimited swap
    @Parameter
    private Long memorySwap;

    // Path to a file where the dynamically mapped properties are written to
    @Parameter
    private String portPropertyFile;

    // For simple network setups. For complex stuff use "network"
    @Parameter
    private String net;

    @Parameter
    private NetworkConfig network;

    @Parameter
    private List<String> dns;

    @Parameter
    private List<String> dnsSearch;

    @Parameter
    private List<String> capAdd;

    @Parameter
    private List<String> capDrop;

    @Parameter
    private Map<String, String> sysctls;

    @Parameter
    private List<String> securityOpts;

    @Parameter
    private Boolean privileged;

    @Parameter
    private List<String> extraHosts;

    @Parameter
    private String isolation;

    @Parameter
    private Long cpuShares;

    @Parameter
    private Double cpus;

    @Parameter
    private String cpuSet;

    // Port mapping. Can contain symbolic names in which case dynamic
    // ports are used
    @Parameter
    private List<String> ports;

    /**
     * @deprecated
     */
    @Parameter
    @Deprecated
    private NamingStrategy namingStrategy;

    /**
     * A pattern to define the naming of the container where
     * <p>
     * - %a for the "alias" mode
     * - %n for the image name
     * - %t for a timestamp
     * - %i for an increasing index of container names
     */
    @Parameter
    private String containerNamePattern;

    /**
     * Property key part used to expose the container ip when running.
     */
    @Parameter
    private String exposedPropertyKey;

    // Mount volumes from the given image's started containers
    @Parameter
    private RunVolumeConfiguration volumes;

    // Links to other container started
    @Parameter
    private List<String> links;

    // Configuration for how to wait during startup of the container
    @Parameter
    private WaitConfiguration wait;

    // Mountpath for tmps
    @Parameter
    private List<String> tmpfs;

    @Parameter
    private LogConfiguration log;

    @Parameter
    private RestartPolicy restartPolicy;

    @Parameter
    private List<UlimitConfig> ulimits;

    @Parameter
    private Boolean skip;

    /**
     * Policy for pulling the image to start
     */
    @Parameter
    private String imagePullPolicy;

    /**
     * Use explicit platform
     */
    @Parameter
    private String platform;

    // Mount the container's root filesystem as read only
    @Parameter
    private Boolean readOnly;

    // Automatically remove the container when it exists
    @Parameter
    private Boolean autoRemove;

    // How to stop a container
    @Parameter
    private StopMode stopMode;

    public RunImageConfiguration() {
    }

    public String initAndValidate() {
        if (entrypoint != null) {
            entrypoint.validate();
        }
        if (entryPoint != null) {
            entryPoint.validate();
        }
        if (cmd != null) {
            cmd.validate();
        }

        // Custom networks are available since API 1.21 (Docker 1.9)
        NetworkConfig config = getNetworkingConfig();
        if (config != null && config.isCustomNetwork()) {
            return "1.21";
        }

        return null;
    }

    public Map<String, String> getEnv() {
        return env;
    }

    public Map<String, String> getLabels() {
        return labels;
    }

    public String getEnvPropertyFile() {
        return envPropertyFile;
    }

    public Arguments getEntrypoint() {
        if (entrypoint != null) {
            return entrypoint;
        }
        return entryPoint;
    }

    public String getHostname() {
        return hostname;
    }

    public String getDomainname() {
        return domainname;
    }

    @Nonnull
    public List<String> getDependsOn() {
        return EnvUtil.splitAtCommasAndTrim(dependsOn);
    }

    public HealthCheckConfiguration getHealthCheckConfiguration() {
        return healthCheckConfiguration;
    }

    public String getUser() {
        return user;
    }

    public Long getShmSize() {
        return shmSize;
    }

    public Long getMemory() {
        return memory;
    }

    public Long getMemorySwap() {
        return memorySwap;
    }

    public String getIsolation() {
        return isolation;
    }

    public Long getCpuShares() {
        return cpuShares;
    }

    public Double getCpus() {
        return cpus;
    }

    public String getCpuSet() {
        return cpuSet;
    }

    @Nonnull
    public List<String> getPorts() {
        return EnvUtil.removeEmptyEntries(ports);
    }

    public Arguments getCmd() {
        return cmd;
    }

    public String getPortPropertyFile() {
        return portPropertyFile;
    }

    public String getWorkingDir() {
        return workingDir;
    }

    public WaitConfiguration getWaitConfiguration() {
        return wait;
    }

    public LogConfiguration getLogConfiguration() {
        return log;
    }

    public List<String> getCapAdd() {
        return capAdd;
    }

    public List<String> getCapDrop() {
        return capDrop;
    }

    public Map<String, String> getSysctls() {
        return sysctls;
    }

    public List<String> getSecurityOpts() {
        return securityOpts;
    }

    public List<String> getDns() {
        return dns;
    }

    @Deprecated
    public String getNetRaw() {
        return net;
    }

    public NetworkConfig getNetworkingConfig() {
        if (network != null) {
            return network;
        } else if (net != null) {
            return new NetworkConfig(net);
        } else {
            return new NetworkConfig();
        }
    }

    public List<String> getDnsSearch() {
        return dnsSearch;
    }

    public List<String> getExtraHosts() {
        return extraHosts;
    }

    public RunVolumeConfiguration getVolumeConfiguration() {
        return volumes;
    }

    @Nonnull
    public List<String> getLinks() {
        return EnvUtil.splitAtCommasAndTrim(links);
    }

    public List<UlimitConfig> getUlimits() {
        return ulimits;
    }

    public List<String> getTmpfs() {
        return tmpfs;
    }

    /**
     * @deprecated
     */
    // Naming scheme for how to name container
    @Deprecated // for backward compatibility, us containerNamePattern instead
    public enum NamingStrategy {
        /**
         * No extra naming
         */
        none,
        /**
         * Use the alias as defined in the configuration
         */
        alias
    }

    public String getExposedPropertyKey() {
        return exposedPropertyKey;
    }

    public Boolean getPrivileged() {
        return privileged;
    }

    public RestartPolicy getRestartPolicy() {
        return (restartPolicy == null) ? RestartPolicy.DEFAULT : restartPolicy;
    }

    public RestartPolicy getRestartPolicyRaw() {
        return restartPolicy;
    }

    public boolean skip() {
        return skip != null ? skip : false;
    }

    public Boolean getSkip() {
        return skip;
    }

    public String getImagePullPolicy() {
        return imagePullPolicy;
    }

    public String getPlatform() {
        return platform;
    }

    public String getContainerNamePattern() {
        return containerNamePattern;
    }

    public Boolean getReadOnly() {
        return readOnly;
    }

    public Boolean getAutoRemove() {
        return autoRemove;
    }

    public String getUserns() {
        return userns;
    }

    public StopMode getStopMode() {
        if (stopMode == null) {
            return StopMode.graceful;
        }
        return stopMode;
    }

    /**
     * @deprecated use {@link #getContainerNamePattern} instead
     */
    @Deprecated
    public NamingStrategy getNamingStrategy() {
        return namingStrategy;
    }

    // ======================================================================================

    public static class Builder {

        public Builder(RunImageConfiguration config) {
            if (config == null) {
                this.config = new RunImageConfiguration();
            } else {
                this.config = DeepCopy.copy(config);
            }
        }

        public Builder() {
            this(null);
        }

        private RunImageConfiguration config;

        public Builder env(Map<String, String> env) {
            config.env = env;
            return this;
        }

        public Builder labels(Map<String, String> labels) {
            config.labels = labels;
            return this;
        }

        public Builder envPropertyFile(String envPropertyFile) {
            config.envPropertyFile = envPropertyFile;
            return this;
        }

        public Builder cmd(String cmd) {
            if (cmd != null) {
                config.cmd = new Arguments(cmd);
            }
            return this;
        }

        public Builder cmd(Arguments args) {
            config.cmd = args;
            return this;
        }

        public Builder domainname(String domainname) {
            config.domainname = domainname;
            return this;
        }

        public Builder entrypoint(Arguments args) {
            config.entrypoint = args;
            config.entryPoint = args;
            return this;
        }

        public Builder hostname(String hostname) {
            config.hostname = hostname;
            return this;
        }

        public Builder portPropertyFile(String portPropertyFile) {
            config.portPropertyFile = portPropertyFile;
            return this;
        }

        public Builder workingDir(String workingDir) {
            config.workingDir = workingDir;
            return this;
        }

        public Builder user(String user) {
            config.user = user;
            return this;
        }

        public Builder shmSize(Long shmSize) {
            config.shmSize = shmSize;
            return this;
        }

        public Builder memory(Long memory) {
            config.memory = memory;
            return this;
        }

        public Builder memorySwap(Long memorySwap) {
            config.memorySwap = memorySwap;
            return this;
        }

        public Builder capAdd(List<String> capAdd) {
            config.capAdd = capAdd;
            return this;
        }

        public Builder capDrop(List<String> capDrop) {
            config.capDrop = capDrop;
            return this;
        }

        public Builder sysctls(Map<String, String> sysctls) {
            config.sysctls = sysctls;
            return this;
        }

        public Builder securityOpts(List<String> securityOpts) {
            config.securityOpts = securityOpts;
            return this;
        }

        public Builder net(String net) {
            config.net = net;
            return this;
        }

        public Builder network(NetworkConfig networkConfig) {
            config.network = networkConfig;
            return this;
        }

        public Builder dependsOn(List<String> dependsOn) {
            config.dependsOn = dependsOn;
            return this;
        }

        public Builder healthcheck(HealthCheckConfiguration healthCheckConfiguration) {
            config.healthCheckConfiguration = healthCheckConfiguration;
            return this;
        }

        public Builder dns(List<String> dns) {
            config.dns = dns;
            return this;
        }

        public Builder dnsSearch(List<String> dnsSearch) {
            config.dnsSearch = dnsSearch;
            return this;
        }

        public Builder extraHosts(List<String> extraHosts) {
            config.extraHosts = extraHosts;
            return this;
        }

        public Builder ulimits(List<UlimitConfig> ulimits) {
            config.ulimits = ulimits;
            return this;
        }

        public Builder ports(List<String> ports) {
            config.ports = ports;
            return this;
        }

        public Builder volumes(RunVolumeConfiguration volumes) {
            config.volumes = volumes;
            return this;
        }

        public Builder links(List<String> links) {
            config.links = links;
            return this;
        }

        public Builder tmpfs(List<String> tmpfs) {
            config.tmpfs = tmpfs;
            return this;
        }

        public Builder wait(WaitConfiguration wait) {
            config.wait = wait;
            return this;
        }

        public Builder log(LogConfiguration log) {
            config.log = log;
            return this;
        }

        public Builder isolation(String isolation) {
            config.isolation = isolation;
            return this;
        }

        public Builder cpuShares(Long cpuShares) {
            config.cpuShares = cpuShares;
            return this;
        }

        public Builder cpus(Double cpus) {
            config.cpus = cpus;
            return this;
        }

        public Builder cpuSet(String cpuSet) {
            config.cpuSet = cpuSet;
            return this;
        }

        public Builder containerNamePattern(String pattern) {
            config.containerNamePattern = pattern;
            return this;
        }

        /**
         * @deprecated use {@link #containerNamePattern} instead
         */
        @Deprecated
        public Builder namingStrategy(String namingStrategy) {
            config.namingStrategy = namingStrategy == null ?
                NamingStrategy.none :
                NamingStrategy.valueOf(namingStrategy.toLowerCase());
            return this;
        }

        /**
         * @deprecated use {@link #containerNamePattern} instead
         */
        @Deprecated
        public Builder namingStrategy(NamingStrategy namingStrategy) {
            config.namingStrategy = namingStrategy;
            return this;
        }

        public Builder exposedPropertyKey(String key) {
            config.exposedPropertyKey = key;
            return this;
        }

        public Builder privileged(Boolean privileged) {
            config.privileged = privileged;
            return this;
        }

        public Builder restartPolicy(RestartPolicy restartPolicy) {
            config.restartPolicy = restartPolicy;
            return this;
        }

        public Builder skip(Boolean skip) {
            config.skip = skip;
            return this;
        }

        public Builder stopMode(StopMode stopMode) {
            config.stopMode = stopMode;
            return this;
        }

        public Builder imagePullPolicy(String imagePullPolicy) {
            if (imagePullPolicy != null) {
                config.imagePullPolicy = imagePullPolicy;
            }
            return this;
        }

        public Builder platform(String platform) {
            if (platform != null) {
                config.platform = platform;
            }
            return this;
        }

        public Builder readOnly(Boolean readOnly) {
            config.readOnly = readOnly;
            return this;
        }

        public Builder autoRemove(Boolean autoRemove) {
            config.autoRemove = autoRemove;
            return this;
        }
        
        public Builder userns(String userns) {
            config.userns = userns;
            return this;
        }

        public RunImageConfiguration build() {
            return config;
        }
    }
}
