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
    private String baseImage = "busybox:latest";

    // Maintainer of this image
    private String maintainer = "docker-maven-plugin@jolokia.org";

    // Basedir to be export
    private String exportDir = "/maven";

    // Default command and arguments
    private String command = "true";
    private String[] arguments = new String[0];

    // List of files to add. Source and destination follow except that destination
    // in interpreted as a relative path to the exportDir
    // See also http://docs.docker.io/reference/builder/#add
    private List<AddEntry> addEntries;

    // list of ports to expose and environments to use
    private List<Integer> ports;
    private Map<String,String> envEntries;

    /**
     * Cretate a DockerFile in the given directory
     * @param  destDir directory where to store the dockerfile
     * @return the full path to the docker file
     * @throws IOException if writing fails
     */
    public File create(File destDir) throws IOException {
        File target = new File(destDir,"Dockerfile");
        FileUtils.fileWrite(target, content());
        return target;
    }

    /**
     * Create a Dockerfile following the format described in the
     * <a href="http://docs.docker.io/reference/builder/#usage">Docker reference manual</a>
     *
     * @return the dockerfile create
     */
    public String content() {
        if (addEntries.size() == 0) {
            throw new IllegalArgumentException("No entries added");
        }
        StringBuilder b = new StringBuilder();
        b.append("FROM ").append(baseImage).append("\n");
        b.append("MAINTAINER ").append(maintainer).append("\n");

        // Entries
        for (AddEntry entry : addEntries) {
            b.append("ADD ").append(entry.source).append(" ")
             .append(exportDir).append("/").append(entry.destination).append("\n");
        }

        // Ports
        if (ports.size() > 0) {
            b.append("EXPOSE");
            for (Integer port : ports) {
                b.append(" " + port);
            }
            b.append("\n");
        }

        // Volume export
        b.append("VOLUME [\"").append(exportDir).append("\"]\n");

        // Environment variable support
        for (Map.Entry<String,String> entry : envEntries.entrySet()) {
            b.append("ENV ").append(entry.getKey()).append(" ")
             .append(entry.getValue()).append("\n");
        }

        // Default command mit args
        if (command != null) {
            b.append("CMD [\"").append(command).append("\"");
            for (String arg : arguments) {
                b.append(",\"").append(arg).append("\"");
            }
            b.append("]").append("\n");
        }

        return b.toString();
    }

    // ==========================================================================
    // Builder stuff ....
    public DockerFileBuilder() {
        addEntries = new ArrayList<AddEntry>();
        
        ports = new ArrayList<Integer>();
        
        envEntries = new HashMap<String, String>();
    }

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

    public DockerFileBuilder exportDir(String exportDir) {
        if (exportDir != null) {
            this.exportDir = exportDir;
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

    public DockerFileBuilder expose(int port) {
        this.ports.add(port);
        return this;
    }

    public DockerFileBuilder expose(List<String> ports) {
        if (ports != null) {
            for (String port : ports) {
                if (port != null) {
                    expose(Integer.parseInt(port));
                }
            }
        }
        return this;
    }

    public DockerFileBuilder env(Map<String, String> values) {
        this.envEntries = values != null ? values : new HashMap<String,String>();
        validateEnv(envEntries);
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
