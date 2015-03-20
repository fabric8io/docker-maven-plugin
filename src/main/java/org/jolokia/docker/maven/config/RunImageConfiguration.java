package org.jolokia.docker.maven.config;

import java.util.List;
import java.util.Map;


/**
 * @author roland
 * @since 02.09.14
 */
public class RunImageConfiguration {

    static final RunImageConfiguration DEFAULT = new RunImageConfiguration();
    
    // Environment variables to set when starting the container. key: variable name, value: env value
    /**
     * @parameter
     */
    private Map<String, String> env;

    // Command to execute in container
    /**
     * @parameter
     */
    private String command;

    // container domain name
    /**
     * @parameter
     */
    private String domainname;

    // container entry point
    /**
     * @parameter
     */
    private String entrypoint;

    // container hostname
    /**
     * @parameter
     */
    private String hostname;

    // container user
    /**
     * @parameter
     */
    private String user;

    // working directory
    /**
     * @paramter
     */
    private String workingDir;

    // memory in bytes
    /**
     * @parameter
     */
    private Long memory;

    // total memory (swap + ram) in bytes, -1 to disable
    /**
     * @parameter
     */
    private Long memorySwap;

    // Path to a file where the dynamically mapped properties are written to
    /**
     * @parameter
     */
    private String portPropertyFile;

    /**
     * @parameter
     */
    private List<String> dns;

    /**
     * @parameter
     */
    private List<String> dnsSearch;

    /**
     * @parameter
     */
    private List<String> capAdd;

    /**
     * @parameter
     */
    private List<String> capDrop;

    /**
     * @parameter
     */
    private Boolean privileged;

    /**
     * @parameter
     */
    private List<String> extraHosts;
    
    // Port mapping. Can contain symbolic names in which case dynamic
    // ports are used
    /**
     * @parameter
     */
    private List<String> ports;

    /** @parameter */
    private NamingStrategy namingStrategy;

    // Naming scheme for how to name container
    public enum NamingStrategy {
        none,  // No extra naming
        alias; // Use the alias as defined in the configuration
    }
    // Mount volumes from the given image's started containers
    /**
     * @parameter
     */
    private VolumeConfiguration volumes;

    // Links to other container started
    /**
     * @parameter
     */
    private List<String> links;

    // Wait that many milliseconds after starting the container in order to allow the
    // container to warm up
    /**
     * @parameter
     */
    private WaitConfiguration wait;

    /**
     * @parameter
     */
    private LogConfiguration log;

    /**
     * @parameter
     */
    private RestartPolicy restartPolicy;

    public RunImageConfiguration() { }

    public Map<String, String> getEnv() {
        return env;
    }

    public String getEntrypoint() {
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

    public Long getMemory() {
        return memory;
    }

    public Long getMemorySwap() {
        return memorySwap;
    }

    public List<String> getPorts() {
        return ports;
    }

    public String getCommand() {
        return command;
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

    public LogConfiguration getLog() {
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

    public NamingStrategy getNamingStrategy() {
        return namingStrategy == null ? NamingStrategy.none : namingStrategy;
    }
    
    public Boolean getPrivileged() {
        return privileged;
    }

    public RestartPolicy getRestartPolicy() {
        return (restartPolicy == null) ? RestartPolicy.DEFAULT : restartPolicy;
    }
    
    // ======================================================================================

    public static class Builder {

        private RunImageConfiguration config = new RunImageConfiguration();
        public Builder env(Map<String, String> env) {
            config.env = env;
            return this;
        }

        public Builder command(String command) {
            config.command = command;
            return this;
        }

        public Builder domainname(String domainname) {
            config.domainname = domainname;
            return this;
        }

        public Builder entrypoint(String entrypoint) {
            config.entrypoint = entrypoint;
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

        public Builder log(LogConfiguration log) {
            config.log = log;
            return this;
        }

        public Builder namingScheme(String namingScheme) {
            config.namingStrategy = namingScheme == null ? NamingStrategy.none : NamingStrategy.valueOf(namingScheme.toLowerCase());
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

        public RunImageConfiguration build() {
            return config;
        }
    }

}
