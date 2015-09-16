package org.jolokia.docker.maven.assembly;/*
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
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.InvalidArtifactRTException;
import org.apache.maven.artifact.handler.ArtifactHandler;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.model.Repository;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.utils.io.DirectoryScanner;
import org.codehaus.plexus.archiver.FileSet;
import org.codehaus.plexus.archiver.diags.TrackingArchiver;
import org.codehaus.plexus.components.io.resources.PlexusIoFileResource;

/**
 * An archiver which remembers all resolved files and directories and returns them
 * on request
 *
 * @author roland
 * @since 15/06/15
 */
public class MappingTrackArchiver extends TrackingArchiver {

    private ArtifactRepository localRepository;

    public void clear() {
        added.clear();
    }

    /**
     * Get all files depicted by this assembly.
     *
     * @return assembled files
     */
    public AssemblyFiles getAssemblyFiles() {
        AssemblyFiles ret = new AssemblyFiles();
        File destFile = getDestFile();
        destFile = new File(destFile.getParentFile(), "maven");
        for (Addition addition : added) {
            Object resource = addition.resource;
            File target = new File(destFile, addition.destination);
            if (resource instanceof File && addition.destination != null) {
                File source = (File) resource;
                ret.addEntry(source, target);
                addLocalMavenRepoWatch(ret, source, target);
            } else if (resource instanceof PlexusIoFileResource) {
                File source = ((PlexusIoFileResource) resource).getFile();
                ret.addEntry(source, target);
                addLocalMavenRepoWatch(ret, source, target);
            } else if (resource instanceof FileSet) {
                FileSet fs = (FileSet) resource;
                DirectoryScanner ds = new DirectoryScanner();
                File base = addition.directory;
                ds.setBasedir(base);
                ds.setIncludes(fs.getIncludes());
                ds.setExcludes(fs.getExcludes());
                ;
                ds.setCaseSensitive(fs.isCaseSensitive());
                ds.scan();
                for (String f : ds.getIncludedFiles()) {
                    File source = new File(base, f);
                    File subTarget = new File(target, f);
                    ret.addEntry(source, subTarget);
                    addLocalMavenRepoWatch(ret, source, target);
                }
            } else {
                throw new IllegalStateException("Unknown resource type " + resource.getClass() + ": " + resource);
            }
        }
        return ret;
    }

    private void addLocalMavenRepoWatch(AssemblyFiles ret, File source, File target) {
        try {
            File localMavenRepoFile = getlocalMavenRepoFile(source);
            if (localMavenRepoFile!=null && !source.getCanonicalFile().equals(localMavenRepoFile.getCanonicalFile())) {
                ret.addEntry(localMavenRepoFile, target);
            }
        } catch (IOException e) {
            // Looks like we could not figure out if that file had a local mvn repo version.
        }
    }

    private File getlocalMavenRepoFile(File source) {

        if (localRepository == null) {
            return null;
        }

        // Lets figure the real mvn source of file.
        String type = null;
        if (source.getName().endsWith(".jar")) {
            type = "jar";
        }
        if (source.getName().endsWith(".war")) {
            type = "war";
        }
        if (type != null) {
            Properties pomPropertiesFromJar = getPomPropertiesFromJar(source);
            if (pomPropertiesFromJar != null) {
                try {
                    DefaultArtifact a = new DefaultArtifact(
                            pomPropertiesFromJar.getProperty("groupId"),
                            pomPropertiesFromJar.getProperty("artifactId"),
                            pomPropertiesFromJar.getProperty("version"),
                            "runtime",
                            type,
                            pomPropertiesFromJar.getProperty("classifier", ""),
                            new DefaultArtifactHandler(type)
                    );
                    File file = new File(localRepository.getBasedir(), localRepository.pathOf(a));
                    if( file !=null ) {
                        if( isSame(file, source) ) {
                            return file;
                        }
                    }
                } catch (InvalidArtifactRTException e) {
                    return null;
                }
            }
        }
        return null;
    }

    private boolean isSame(File file, File source) {
        return true;
    }

    private Properties getPomPropertiesFromJar(File jar) {
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
                return options.get(0);
            }
        } catch (IOException e) {
        }
        return null;
    }

    public ArtifactRepository getLocalRepository() {
        return localRepository;
    }

    public void setLocalRepository(ArtifactRepository localRepository) {
        this.localRepository = localRepository;
    }

}
