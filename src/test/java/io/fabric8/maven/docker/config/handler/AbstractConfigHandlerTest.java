package io.fabric8.maven.docker.config.handler;

import io.fabric8.maven.docker.config.RestartPolicy;
import io.fabric8.maven.docker.config.RunImageConfiguration;
import org.junit.jupiter.api.Assertions;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;



public abstract class AbstractConfigHandlerTest {

    protected List<String> a(String ... args) {
        return Arrays.asList(args);
    }

    protected abstract String getEnvPropertyFile();
    
    protected abstract RunImageConfiguration.NamingStrategy getRunNamingStrategy();
    
    protected abstract void validateEnv(Map<String, String> env);
    
    protected void validateRunConfiguration(RunImageConfiguration runConfig) {
        Assertions.assertEquals(a("/foo", "/tmp:/tmp"), runConfig.getVolumeConfiguration().getBind());
        Assertions.assertEquals(a("CAP"), runConfig.getCapAdd());
        Assertions.assertEquals(a("CAP"), runConfig.getCapDrop());
        Assertions.assertEquals(Collections.singletonMap("key", "value"), runConfig.getSysctls());
        Assertions.assertEquals("command.sh", runConfig.getCmd().getShell());
        Assertions.assertEquals(a("8.8.8.8"), runConfig.getDns());
        Assertions.assertEquals(a("example.com"), runConfig.getDnsSearch());
        Assertions.assertEquals("domain.com", runConfig.getDomainname());
        Assertions.assertEquals("entrypoint.sh", runConfig.getEntrypoint().getShell());
        Assertions.assertEquals(a("localhost:127.0.0.1"), runConfig.getExtraHosts());
        Assertions.assertEquals("subdomain", runConfig.getHostname());
        Assertions.assertEquals(a("redis"), runConfig.getLinks());
        Assertions.assertEquals((Long) 1L, runConfig.getMemory());
        Assertions.assertEquals((Long) 1L, runConfig.getMemorySwap());
        Assertions.assertEquals((Long) 1000000000L, runConfig.getCpus());
        Assertions.assertEquals("default", runConfig.getIsolation());
        Assertions.assertEquals((Long) 1L, runConfig.getCpuShares());
        Assertions.assertEquals("0,1", runConfig.getCpuSet());
        Assertions.assertEquals(getEnvPropertyFile(),runConfig.getEnvPropertyFile());
        
        Assertions.assertEquals("/tmp/props.txt", runConfig.getPortPropertyFile());
        Assertions.assertEquals(a("8081:8080"), runConfig.getPorts());
        Assertions.assertEquals(true, runConfig.getPrivileged());
        Assertions.assertEquals("tomcat", runConfig.getUser());
        Assertions.assertEquals(a("from"), runConfig.getVolumeConfiguration().getFrom());
        Assertions.assertEquals("foo", runConfig.getWorkingDir());
    
        validateEnv(runConfig.getEnv());
    
        // not sure it's worth it to implement 'equals/hashcode' for these
        RestartPolicy policy = runConfig.getRestartPolicy();
        Assertions.assertEquals("on-failure", policy.getName());
        Assertions.assertEquals(1, policy.getRetry());
    }
}
