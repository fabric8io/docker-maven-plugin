package io.fabric8.maven.docker.assembly;/*
 * 
 * Copyright 2014 Roland Huss
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.*;
import java.util.ArrayList;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import io.fabric8.maven.docker.util.Logger;
import org.apache.maven.artifact.*;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.shared.utils.io.DirectoryScanner;
import org.codehaus.plexus.archiver.Archiver;
import org.codehaus.plexus.archiver.FileSet;
import org.codehaus.plexus.archiver.diags.TrackingArchiver;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.components.io.resources.PlexusIoFileResource;

/**
 * An archiver which remembers all resolved files and directories and returns them
 * on request.
 *
 * @author roland
 * @since 15/06/15
 */
@Component(role = Archiver.class, hint = "track", instantiationStrategy = "singleton")
public class MappingTrackArchiver extends TrackingArchiver {

    // Logger to use
    protected Logger log;

    /**
     * Get all files depicted by this assembly.
     *
     * @return assembled files
     */
    public AssemblyFiles getAssemblyFiles(MavenSession session) {
        AssemblyFiles ret = new AssemblyFiles(getDestFile());
        // Where the 'real' files are copied to
        for (Addition addition : added) {
            Object resource = addition.resource;
            File target = new File(ret.getAssemblyDirectory(), addition.destination);
            if (resource instanceof File && addition.destination != null) {
                addFileEntry(ret, session, (File) resource, target);
            } else if (resource instanceof PlexusIoFileResource) {
                addFileEntry(ret, session, ((PlexusIoFileResource) resource).getFile(), target);
            } else if (resource instanceof FileSet) {
                FileSet fs = (FileSet) resource;
                DirectoryScanner ds = new DirectoryScanner();
                File base = addition.directory;
                ds.setBasedir(base);
                ds.setIncludes(fs.getIncludes());
                ds.setExcludes(fs.getExcludes());
                ds.setCaseSensitive(fs.isCaseSensitive());
                ds.scan();
                for (String f : ds.getIncludedFiles()) {
                    File source = new File(base, f);
                    File subTarget = new File(target, f);
                    addFileEntry(ret, session, source, subTarget);
                }
            } else {
                throw new IllegalStateException("Unknown resource type " + resource.getClass() + ": " + resource);
            }
        }
        return ret;
    }

    private void addFileEntry(AssemblyFiles ret, MavenSession session, File source, File target) {
        ret.addEntry(source, target);
        addLocalMavenRepoEntry(ret, session, source, target);
    }

    private void addLocalMavenRepoEntry(AssemblyFiles ret, MavenSession session, File source, File target) {
        File localMavenRepoFile = getLocalMavenRepoFile(session, source);
        try {
            if (localMavenRepoFile != null &&
                ! source.getCanonicalFile().equals(localMavenRepoFile.getCanonicalFile())) {
                ret.addEntry(localMavenRepoFile, target);
            }
        } catch (IOException e) {
            log.warn("Cannot add %s for watching: %s. Ignoring for watch ...", localMavenRepoFile, e.getMessage());
        }
    }

    private File getLocalMavenRepoFile(MavenSession session, File source) {
        ArtifactRepository localRepo = session.getLocalRepository();
        if (localRepo == null) {
            log.warn("No local repo found so not adding any extra watches in the local repository");
            return null;
        }

        Artifact artifact = getArtifactFromJar(source);
        if (artifact != null) {
            try {
                return new File(localRepo.getBasedir(), localRepo.pathOf(artifact));
            } catch (InvalidArtifactRTException e) {
                log.warn("Cannot get the local repository path for %s in base dir %s : %s",
                         artifact, localRepo.getBasedir(), e.getMessage());
            }
        }
        return null;
    }

    // look into a jar file and check for pom.properties. The first pom.properties found are returned.
    private Artifact getArtifactFromJar(File jar) {
        // Lets figure the real mvn source of file.
        String type = extractFileType(jar);
        if (type != null) {
            try {
                ArrayList<Properties> options = new ArrayList<Properties>();
                try (ZipInputStream in = new ZipInputStream(new FileInputStream(jar))) {
                    ZipEntry entry;
                    while ((entry = in.getNextEntry()) != null) {
                        if (entry.getName().startsWith("META-INF/maven/") && entry.getName().endsWith("pom.properties")) {
                            byte[] buf = new byte[1024];
                            int len;
                            ByteArrayOutputStream out = new ByteArrayOutputStream(); //change ouptut stream as required
                            while ((len = in.read(buf)) > 0) {
                                out.write(buf, 0, len);
                            }
                            Properties properties = new Properties();
                            properties.load(new ByteArrayInputStream(out.toByteArray()));
                            options.add(properties);
                        }
                    }
                }
                if (options.size() == 1) {
                    return getArtifactFromPomProperties(type,options.get(0));
                } else {
                    log.warn("Found %d pom.properties in %s", options.size(), jar);
                }
            } catch (IOException e) {
                log.warn("IO Exception while examining %s for maven coordinates: %s. Ignoring for watching ...",
                         jar, e.getMessage());
            }
        }
        return null;
    }

    // type when it is a Java archive, null otherwise
    private final static Pattern JAVA_ARCHIVE_DETECTOR = Pattern.compile("^.*\\.(jar|war|ear)$");
    private String extractFileType(File source) {
        Matcher matcher = JAVA_ARCHIVE_DETECTOR.matcher(source.getName());
        return matcher.matches() ? matcher.group(1) : null;
    }

    private Artifact getArtifactFromPomProperties(String type, Properties pomProps) {
        return new DefaultArtifact(
                pomProps.getProperty("groupId"),
                pomProps.getProperty("artifactId"),
                pomProps.getProperty("version"),
                "runtime",
                type,
                pomProps.getProperty("classifier", ""),
                new DefaultArtifactHandler(type)
        );
    }

    public void init(Logger log) {
        this.log = log;
        added.clear();
    }
}
