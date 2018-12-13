package io.fabric8.maven.docker.util;/*
 *
 * Copyright 2015 Roland Huss
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
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import io.fabric8.maven.docker.build.maven.MavenBuildContext;
import org.apache.commons.io.FileUtils;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.MavenArtifactRepository;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;
import org.apache.maven.settings.Settings;
import org.codehaus.plexus.interpolation.fixed.FixedStringSearchInterpolator;
import org.codehaus.plexus.util.IOUtil;
import org.junit.Test;

import static io.fabric8.maven.docker.util.PathTestUtil.createTmpFile;
import static org.junit.Assert.assertEquals;

/**
 * @author roland
 * @since 21/01/16
 */
public class DockerFileUtilTest {

    @Test
    public void testSimple() throws Exception {
        File toTest = copyToTempDir("Dockerfile_from_simple");
        assertEquals("fabric8/s2i-java", DockerFileUtil.extractBaseImage(
            toTest, s -> FixedStringSearchInterpolator.create().interpolate(s)));
    }

    private File copyToTempDir(String resource) throws IOException {
        File dir = Files.createTempDirectory("d-m-p").toFile();
        File ret = new File(dir, "Dockerfile");
        try (FileOutputStream os = new FileOutputStream(ret)) {
            IOUtil.copy(getClass().getResourceAsStream(resource), os);
        }
        return ret;
    }

    @Test
    public void interpolate() throws Exception {
        MavenBuildContext buildContext = mockMavenBuildContext();

        Map<String, String> filterMapping = new HashMap<>();
        filterMapping.put("none", "false");
        filterMapping.put("var", "${*}");
        filterMapping.put("at", "@");

        for (Map.Entry<String, String> entry : filterMapping.entrySet()) {
            for (int i = 1; i < 2; i++) {
                File dockerFile = getDockerfilePath(i, entry.getKey());
                File expectedDockerFile = new File(dockerFile.getParent(), dockerFile.getName() + ".expected");
                File actualDockerFile = createTmpFile(dockerFile.getName());
                FixedStringSearchInterpolator interpolator = DockerFileUtil.createInterpolator(buildContext, entry.getValue());
                FileUtils.write(actualDockerFile, DockerFileUtil.interpolate(dockerFile, interpolator), "UTF-8");
                // Compare text lines without regard to EOL delimiters
                assertEquals(FileUtils.readLines(expectedDockerFile, Charset.defaultCharset()),
                             FileUtils.readLines(actualDockerFile, Charset.defaultCharset()));
            }
        }
    }

    private File getDockerfilePath(int i, String dir) {
        ClassLoader classLoader = getClass().getClassLoader();
        return new File(classLoader.getResource(
            String.format("interpolate/%s/Dockerfile_%d", dir, i)).getFile());
    }

    private MavenBuildContext mockMavenBuildContext() {
        MavenProject project = new MavenProject();
        project.setArtifactId("docker-maven-plugin");

        Properties projectProperties = project.getProperties();
        projectProperties.put("base", "java");
        projectProperties.put("name", "guenther");
        projectProperties.put("age", "42");
        projectProperties.put("ext", "png");

        Settings settings = new Settings();
        ArtifactRepository localRepository = new MavenArtifactRepository() {
            public String getBasedir() {
                return "repository";
            }
        };
        @SuppressWarnings("deprecation")
        MavenSession session = new MavenSession(null, settings, localRepository, null, null, Collections.emptyList(), ".", null, null, new Date(System.currentTimeMillis()));
        session.getUserProperties().setProperty("cliOverride", "cliValue"); // Maven CLI override: -DcliOverride=cliValue
        session.getSystemProperties().put("user.name", "somebody"); // Java system property: -Duser.name=somebody

        return new MavenBuildContext.Builder()
            .project(project)
            .settings(settings)
            .session(session)
            .sourceDirectory("src")
            .outputDirectory("target")
            .reactorProjects(Collections.singletonList(project))
            .build();
    }
}
