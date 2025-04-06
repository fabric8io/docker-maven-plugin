package io.fabric8.maven.docker.assembly;

import io.fabric8.maven.docker.util.Logger;
import org.apache.maven.execution.MavenSession;

import javax.inject.Named;
import javax.inject.Singleton;
import java.util.HashMap;
import java.util.Map;

/**
 * Contains several {@link MappingTrackArchiver} instances, one per assembly
 */
@Singleton
@Named
public class TrackArchiverCollection {
    private final Map<String, MappingTrackArchiver> archivers = new HashMap<>();

    public AssemblyFiles getAssemblyFiles(MavenSession session, String assemblyName) {
        if (archivers.containsKey(assemblyName)) {
            return archivers.get(assemblyName).getAssemblyFiles(session);
        }
        return null;
    }

    public MappingTrackArchiver get(String assemblyName) {
        return archivers.get(assemblyName);
    }

    public void init(Logger log, String assemblyName) {
        MappingTrackArchiver archiver = archivers.computeIfAbsent(assemblyName, k -> new MappingTrackArchiver());
        archiver.init(log, assemblyName);
    }
}
