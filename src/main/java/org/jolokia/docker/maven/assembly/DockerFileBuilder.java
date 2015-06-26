package org.jolokia.docker.maven.assembly;

import java.io.File;
import java.io.IOException;
import java.util.*;

import com.google.common.base.Joiner;
import org.apache.commons.lang3.StringUtils;
import org.codehaus.plexus.util.FileUtils;
import org.jolokia.docker.maven.config.Arguments;

/**
 * Create a dockerfile
 *
 * @author roland
 * @since 17.04.14
 */
public class DockerFileBuilder {

    private static final Joiner JOIN_ON_COMMA = Joiner.on("\",\"");

    // Base image to use as from
    private String baseImage;

    // Maintainer of this image
    private String maintainer = "docker-maven-plugin@jolokia.org";

    // Workdir
    private String workdir = null;

    // Basedir to be export
    private String basedir = "/maven";

    private Arguments entryPoint;
    private Arguments cmd;

    private Boolean exportBasedir = null;

    // User under which the files should be added
    private String user;

    // List of files to add. Source and destination follow except that destination
    // in interpreted as a relative path to the exportDir
    // See also http://docs.docker.io/reference/builder/#add
    private List<AddEntry> addEntries = new ArrayList<>();

    // list of ports to expose and environments to use
    private List<String> ports = new ArrayList<>();
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

        StringBuilder b = new StringBuilder();
        
        b.append("FROM ").append(baseImage != null ? baseImage : DockerAssemblyManager.DEFAULT_DATA_BASE_IMAGE).append("\n");
        b.append("MAINTAINER ").append(maintainer).append("\n");
        addEnv(b);
        addPorts(b);
        addVolumes(b);
        addEntries(b);
        if (workdir != null) b.append("WORKDIR ").append(workdir).append("\n");
        addCmd(b);
        addEntryPoint(b);

        return b.toString();
    }

    private void addEntryPoint(StringBuilder b){
        if (entryPoint != null) {
            buildArguments(b, "ENTRYPOINT", entryPoint);
        }
    }

    private void addCmd(StringBuilder b){
        if (cmd != null) {
            buildArguments(b, "CMD", cmd);
        }
    }

    private static void buildArguments(StringBuilder b, String name, Arguments arguments) {
        b.append(name).append(" ");
        if (arguments.getShell() != null) {
            b.append(arguments.getShell());
        } else {
            b.append("[\"").append(JOIN_ON_COMMA.join(arguments.getExec())).append("\"]");
        }
        b.append("\n");
    }
    
    private void addEntries(StringBuilder b) {
        List<String> destinations = new ArrayList<>();
        for (AddEntry entry : addEntries) {
            String dest =  (basedir.equals("/") ? "" : basedir) +  "/" + entry.destination;
            destinations.add(dest);
            b.append("COPY ").append(entry.source).append(" ").append(dest).append("\n");
        }
        if (user != null) {
            String[] userParts = StringUtils.split(user,':');
            String userArg = userParts.length > 1 ? userParts[0] + ":" + userParts[1] : userParts[0];
            String cmd = "RUN [\"chown\", \"-R\", \"" + userArg + "\",\"" +
                         StringUtils.join(destinations, "\",\"") + "\"]\n";
            if (userParts.length > 2) {
                b.append("USER root\n");
                b.append(cmd);
                b.append("USER ").append(userParts[2]).append("\n");
            } else {
                b.append(cmd);
            }
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
            for (String port : ports) {
                b.append(" ").append(port);
            }
            b.append("\n");
        }
    }

    private void addVolumes(StringBuilder b) {
        if (exportBasedir != null ? exportBasedir : baseImage == null) {
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

    public DockerFileBuilder workdir(String workdir) {
        this.workdir = workdir;
        return this;
    }

    public DockerFileBuilder basedir(String dir) {
        if (dir != null) {
            basedir = dir;
        }
        return this;
    }

    public DockerFileBuilder cmd(Arguments cmd) {
        cmd.validate();
        this.cmd = cmd;
        return this;
    }

    public DockerFileBuilder entryPoint(Arguments entryPoint) {
        entryPoint.validate();
        this.entryPoint = entryPoint;
        return this;
    }

    public DockerFileBuilder user(String user) {
        this.user = user;
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
                    this.ports.add(port);
                }
            }
        }
        return this;
    }

    public DockerFileBuilder exportBasedir(Boolean exportBasedir) {
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
