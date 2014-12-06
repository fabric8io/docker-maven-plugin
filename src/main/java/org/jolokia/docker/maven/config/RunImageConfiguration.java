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

    private boolean privileged;

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
    private long memory;

    // total memory (swap + ram) in bytes, -1 to disable
    /**
     * @parameter
     */
    private long memorySwap;

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
    private List<String> extraHosts;
    
    // Port mapping. Can contain symbolic names in which case dynamic
    // ports are used
    /**
     * @parameter
     */
    private List<String> ports;

    // Mount volumes from the given image's started containers
    /**
     * @parameter
     */
    private List<String> volumes;

    /**
     * @parameter
     */
    private List<String> bind;

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

    public long getMemory() {
        return memory;
    }

    public long getMemorySwap() {
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

    public List<String> getBind() {
        return bind;
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
    
    public List<String> getVolumesFrom() {
        return volumes;
    }

    public List<String> getLinks() {
        return links;
    }

    public boolean getPrivileged() {
        return privileged;
    }

    public RunImageConfiguration.RestartPolicy getRestartPolicy() {
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

        public Builder memory(long memory) {
            config.memory = memory;
            return this;
        }

        public Builder memorySwap(long memorySwap) {
            config.memorySwap = memorySwap;
            return this;
        }

        public Builder bind(List<String> bind) {
            config.bind = bind;
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

        public Builder volumes(List<String> volumes) {
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

        public Builder privileged(boolean privileged) {
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

    public static class RestartPolicy {

        public static final RestartPolicy DEFAULT = new RestartPolicy();
        
        /**
         * @parameter
         */
        private String name;

        /**
         * @parameter
         */
        private int retry;

        public RestartPolicy() { }

        public RestartPolicy(String name, int retry) {
            this.name = name;
            this.retry = retry;
        }

        public String getName() {
            return name;
        }

        public int getRetry() {
            return retry;
        }
    }
}
