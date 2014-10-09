package org.jolokia.docker.maven.assembly;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

        // Default command mit args
        if (command != null) {
            b.append("CMD [\"").append(command).append("\"");
            for (String arg : arguments) {
                b.append(",\"").append(arg).append("\"");
            }
            b.append("]").append("\n");
        }

        // Entries
        for (AddEntry entry : addEntries) {
            b.append("ADD ").append(entry.source).append(" ")
             .append(exportDir).append("/").append(entry.destination).append("\n");
        }

        // Volume export
        b.append("VOLUME [\"").append(exportDir).append("\"]\n");

        // Environment variable support
        for (Map.Entry<String,String> entry : envEntries.entrySet()) {
            b.append("ENV ").append(entry.getKey()).append(" ")
             .append(entry.getValue()).append("\n");
        }

        return b.toString();
    }

    // ==========================================================================
    // Builder stuff ....
    public DockerFileBuilder() {
        addEntries = new ArrayList<AddEntry>();
        envEntries = new HashMap<String, String>();
    }

    public DockerFileBuilder baseImage(String baseImage) {
        this.baseImage = baseImage;
        return this;
    }

    public DockerFileBuilder maintainer(String maintainer) {
        this.maintainer = maintainer;
        return this;
    }

    public DockerFileBuilder exportDir(String exportDir) {
        this.exportDir = exportDir;
        return this;
    }

    public DockerFileBuilder command(String command, String ... args) {
        this.command = command;
        this.arguments = args;
        return this;
    }

    public DockerFileBuilder add(String source, String destination) {
        this.addEntries.add(new AddEntry(source, destination));
        return this;
    }

    public DockerFileBuilder environmentVariables(Map<String, String> values) {
        this.envEntries = values;
        return this;
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
