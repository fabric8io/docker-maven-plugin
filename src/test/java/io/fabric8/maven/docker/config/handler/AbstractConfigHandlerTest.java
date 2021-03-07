package io.fabric8.maven.docker.config.handler;

import io.fabric8.maven.docker.config.RestartPolicy;
import io.fabric8.maven.docker.config.RunImageConfiguration;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public abstract class AbstractConfigHandlerTest {

    protected List<String> a(String ... args) {
        return Arrays.asList(args);
    }

    protected abstract String getEnvPropertyFile();
    
    protected abstract RunImageConfiguration.NamingStrategy getRunNamingStrategy();
    
    protected abstract void validateEnv(Map<String, String> env);
    
    protected void validateRunConfiguration(RunImageConfiguration runConfig) {
        assertEquals(a("/foo", "/tmp:/tmp"), runConfig.getVolumeConfiguration().getBind());
        assertEquals(a("CAP"), runConfig.getCapAdd());
        assertEquals(a("CAP"), runConfig.getCapDrop());
        assertEquals("command.sh", runConfig.getCmd().getShell());
        assertEquals(a("8.8.8.8"), runConfig.getDns());
        assertEquals(a("example.com"), runConfig.getDnsSearch());
        assertEquals("domain.com", runConfig.getDomainname());
        assertEquals("entrypoint.sh", runConfig.getEntrypoint().getShell());
        assertEquals(a("localhost:127.0.0.1"), runConfig.getExtraHosts());
        assertEquals("subdomain", runConfig.getHostname());
        assertEquals(a("redis"), runConfig.getLinks());
        assertEquals((Long) 1L, runConfig.getMemory());
        assertEquals((Long) 1L, runConfig.getMemorySwap());
        assertEquals((Long) 1000000000L, runConfig.getCpus());
        assertEquals((Long) 1L, runConfig.getCpuShares());
        assertEquals("0,1", runConfig.getCpuSet());
        assertEquals(getEnvPropertyFile(),runConfig.getEnvPropertyFile());
        
        assertEquals("/tmp/props.txt", runConfig.getPortPropertyFile());
        assertEquals(a("8081:8080"), runConfig.getPorts());
        assertEquals(true, runConfig.getPrivileged());
        assertEquals("tomcat", runConfig.getUser());
        assertEquals(a("from"), runConfig.getVolumeConfiguration().getFrom());
        assertEquals("foo", runConfig.getWorkingDir());
    
        validateEnv(runConfig.getEnv());
    
        // not sure it's worth it to implement 'equals/hashcode' for these
        RestartPolicy policy = runConfig.getRestartPolicy();
        assertEquals("on-failure", policy.getName());
        assertEquals(1, policy.getRetry());
    }
}
