package org.jolokia.docker.maven.assembly;

import java.io.File;
import java.io.IOException;
import java.util.*;

import org.codehaus.plexus.util.FileUtils;

/**
 * Create a dockerfile
 *
 * @author roland
 * @since 17.04.14
 */
public class DockerFileBuilder {

    // Defaults, shouldnt be overwritten
    private String baseImage;

    // Maintainer of this image
    private String maintainer = "docker-maven-plugin@jolokia.org";

    // Basedir to be export
    private String basedir = "/maven";

    // Default command and arguments
    private String command = "true";
    private String[] arguments = new String[0];

    private boolean exportBasedir = true;
    
    // List of files to add. Source and destination follow except that destination
    // in interpreted as a relative path to the exportDir
    // See also http://docs.docker.io/reference/builder/#add
    private List<AddEntry> addEntries = new ArrayList<>();

    // list of ports to expose and environments to use
    private List<Integer> ports = new ArrayList<>();
    private Map<String,String> envEntries = new HashMap<>();
    
    // exposed volumes
    private List<String> volumes = new ArrayList<>();

    /**
     * Create a DockerFile in the given directory
     * @param  destDir directory where to store the dockerfile
     * @return the full path to the docker file
     * @throws IOException if writing fails
     */
    public File write(File destDir) throws IOException {
        File target = new File(destDir,"Dockerfile");
        FileUtils.fileWrite(target, content());
        return target;
    }

    /**
     * Create a Dockerfile following the format described in the
     * <a href="http://docs.docker.io/reference/builder/#usage">Docker reference manual</a>
     *
     * @return the dockerfile create
     * @throws IllegalArgumentException if no src/dest entries have been added
     */
    public String content() throws IllegalArgumentException {
        if (addEntries.size() == 0) {
            throw new IllegalArgumentException("No entries added");
        }

        StringBuilder b = new StringBuilder();
        
        b.append("FROM ").append(baseImage).append("\n");
        b.append("MAINTAINER ").append(maintainer).append("\n");

        // Environment variable support
        addEnv(b);
        // Ports
        addPorts(b);
        // Volume export
        addVolumes(b);
        // Entries
        addEntries(b);
        // Default command mit args
        addCommands(b);

        return b.toString();
    }

    private void addCommands(StringBuilder b) {
        if (command != null) {
            b.append("CMD [\"").append(command).append("\"");
            for (String arg : arguments) {
                b.append(",\"").append(arg).append("\"");
            }
            b.append("]").append("\n");
        }
    }
    
    private void addEntries(StringBuilder b) {
        for (AddEntry entry : addEntries) {
            b.append("COPY ").append(entry.source).append(" ")
             .append(basedir).append("/").append(entry.destination).append("\n");
        }
    }

    private void addEnv(StringBuilder b) {
        for (Map.Entry<String,String> entry : envEntries.entrySet()) {
            b.append("ENV ").append(entry.getKey()).append(" ")
             .append(entry.getValue()).append("\n");
        }
    }

    private void addPorts(StringBuilder b) {
        if (ports.size() > 0) {
            b.append("EXPOSE");
            for (Integer port : ports) {
                b.append(" " + port);
            }
            b.append("\n");
        }
    }

    private void addVolumes(StringBuilder b) {
        if (exportBasedir) {
            addVolume(b, basedir);
        }
        
        for (String volume : volumes) {
            addVolume(b, volume);
        }
    }
    
    private void addVolume(StringBuilder buffer, String volume) {
        while (volume.endsWith("/")) {
            volume = volume.substring(0, volume.length() - 1);
        }
        // don't export '/'
        if (volume.length() > 0) {        
            buffer.append("VOLUME [\"").append(volume).append("\"]\n");
        }
    }
    
    // ==========================================================================
    // Builder stuff ....
    public DockerFileBuilder() {}

    public DockerFileBuilder baseImage(String baseImage) {
        if (baseImage != null) {
            this.baseImage = baseImage;
        }
        return this;
    }

    public DockerFileBuilder maintainer(String maintainer) {
        this.maintainer = maintainer;
        return this;
    }

    public DockerFileBuilder basedir(String dir) {
        if (dir != null) {
            basedir = dir;
        }
        return this;
    }

    public DockerFileBuilder command(String ... args) {
        if (args == null || args.length == 0) {
            this.command = null;
            this.arguments = new String[0];
        } else {
            this.command = args[0];
            this.arguments = args.length > 1 ? Arrays.copyOfRange(args, 1, args.length) : new String[0];
        }
        return this;
    }

    public DockerFileBuilder add(String source, String destination) {
        this.addEntries.add(new AddEntry(source, destination));
        return this;
    }

    public DockerFileBuilder expose(List<String> ports) {
        if (ports != null) {
            for (String port : ports) {
                if (port != null) {
                    this.ports.add(Integer.parseInt(port));
                }
            }
        }
        return this;
    }

    public DockerFileBuilder exportBasedir(boolean exportBasedir) {
        this.exportBasedir = exportBasedir;
        return this;
    }
    
    public DockerFileBuilder env(Map<String, String> values) {
        if (values != null) {
            this.envEntries.putAll(values);
            validateEnv(envEntries);
        }
        return this;
    }

    
    public DockerFileBuilder volumes(List<String> volumes) {
        if (volumes != null) {
           this.volumes.addAll(volumes);
        }
        return this;
    }
    
    private void validateEnv(Map<String,String> env) {
        for (Map.Entry<String,String> entry : env.entrySet()) {
            if (entry.getValue() == null || entry.getValue().length() == 0) {
                throw new IllegalArgumentException("Environment variable '" +
                                                   entry.getKey() + "' must not be null or empty if building an image");
            }
        }
    }

    // All entries required, destination is relative to exportDir
    private static final class AddEntry {
        private String source,destination;

        private AddEntry(String src, String dest) {
            source = src;

            // Strip leading slashes
            destination = dest;

            // squeeze slashes
            while (destination.startsWith("/")) {
                destination = destination.substring(1);
            }
        }
    }

}
