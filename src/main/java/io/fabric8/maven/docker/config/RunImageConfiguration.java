package io.fabric8.maven.docker.config;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.maven.plugins.annotations.Parameter;


/**
 * @author roland
 * @since 02.09.14
 */
public class RunImageConfiguration {

    static final RunImageConfiguration DEFAULT = new RunImageConfiguration();

    /**
     * Environment variables to set when starting the container. key: variable name, value: env value
     */
    @Parameter
    private Map<String, String> env;

    @Parameter
    private Map<String,String> labels;

    // Path to a property file holding environment variables
    @Parameter
    private String envPropertyFile;

    // Command to execute in container
    @Parameter
    private Arguments cmd;

    // container domain name
    @Parameter
    private String domainname;

    // container entry point
    @Parameter
    private Arguments entrypoint;

    // container hostname
    @Parameter
    private String hostname;

    // container user
    @Parameter
    private String user;

    // working directory
    @Parameter
    private String workingDir;

    // Size of /dev/shm in bytes
    /** @parameter */
    private Long shmSize;

    // memory in bytes
    @Parameter
    private Long memory;

    // total memory (swap + ram) in bytes, -1 to disable
    @Parameter
    private Long memorySwap;

    // Path to a file where the dynamically mapped properties are written to
    @Parameter
    private String portPropertyFile;

    @Parameter
    private String net;

    @Parameter
    private List<String> dns;

    @Parameter
    private List<String> dnsSearch;

    @Parameter
    private List<String> capAdd;

    @Parameter
    private List<String> capDrop;

    @Parameter
    private Boolean privileged;

    @Parameter
    private List<String> extraHosts;

    // Port mapping. Can contain symbolic names in which case dynamic
    // ports are used
    @Parameter
    private List<String> ports;

    @Parameter
    private NamingStrategy namingStrategy;

    // Mount volumes from the given image's started containers
    @Parameter
    private VolumeConfiguration volumes;

    // Links to other container started
    @Parameter
    private List<String> links;

    // Configuration for how to wait during startup of the container
    @Parameter
    private WaitConfiguration wait;

    @Parameter
    private Integer fetchLimit;

    @Parameter
    private LogConfiguration log;

    @Parameter
    private RestartPolicy restartPolicy;

    @Parameter
    private boolean skip = false;

    public RunImageConfiguration() { }

    public String initAndValidate() {
        if (entrypoint != null) {
            entrypoint.validate();
        }
        if (cmd != null) {
            cmd.validate();
        }

        // Custom networks are available since API 1.21 (Docker 1.9)
        NetworkingMode mode = getNetworkingMode();
        if (mode.isCustomNetwork()) {
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
        return entrypoint;
    }

    public String getHostname() {
        return hostname;
    }

    public String getDomainname() {
        return domainname;
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

    public List<String> getPorts() {
        return (ports != null) ? ports : Collections.<String>emptyList();
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

    public Integer getFetchLimit() {
        return fetchLimit;
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

    public List<String> getDns() {
        return dns;
    }

    public NetworkingMode getNetworkingMode() {
        return new NetworkingMode(net);
    }

    public List<String> getDnsSearch() {
        return dnsSearch;
    }

    public List<String> getExtraHosts() {
        return extraHosts;
    }

    public VolumeConfiguration getVolumeConfiguration() {
        return volumes;
    }

    public List<String> getLinks() {
        return links;
    }

    // Naming scheme for how to name container
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

    public NamingStrategy getNamingStrategy() {
        return namingStrategy == null ? NamingStrategy.none : namingStrategy;
    }

    public Boolean getPrivileged() {
        return privileged;
    }

    public RestartPolicy getRestartPolicy() {
        return (restartPolicy == null) ? RestartPolicy.DEFAULT : restartPolicy;
    }

    public boolean skip() {
        return skip;
    }

    // ======================================================================================

    public static class Builder {

        private RunImageConfiguration config = new RunImageConfiguration();

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

        public Builder domainname(String domainname) {
            config.domainname = domainname;
            return this;
        }

        public Builder entrypoint(String entrypoint) {
            if (entrypoint != null) {
                config.entrypoint = new Arguments(entrypoint);
            }
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

        public Builder net(String net) {
            config.net = net;
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

        public Builder ports(List<String> ports) {
            config.ports = ports;
            return this;
        }

        public Builder volumes(VolumeConfiguration volumes) {
            config.volumes = volumes;
            return this;
        }

        public Builder links(List<String> links) {
            config.links = links;
            return this;
        }

        public Builder wait(WaitConfiguration wait) {
            config.wait = wait;
            return this;
        }

        public Builder fetchLimit(Integer fetchLimit) {
            config.fetchLimit = fetchLimit;
            return this;
        }

        public Builder log(LogConfiguration log) {
            config.log = log;
            return this;
        }

        public Builder namingStrategy(String namingStrategy) {
            config.namingStrategy = namingStrategy == null ?
                    NamingStrategy.none :
                    NamingStrategy.valueOf(namingStrategy.toLowerCase());
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

        public Builder skip(String skip) {
            if (skip != null) {
                config.skip = Boolean.valueOf(skip);
            }
            return this;
        }

        public RunImageConfiguration build() {
            return config;
        }
    }

}
