package io.fabric8.maven.docker.util;

import com.google.cloud.tools.jib.api.JibContainerBuilder;
import com.google.cloud.tools.jib.api.buildplan.AbsoluteUnixPath;
import com.google.cloud.tools.jib.api.buildplan.FileEntriesLayer;
import com.google.cloud.tools.jib.api.buildplan.ImageFormat;
import com.google.cloud.tools.jib.api.buildplan.Port;
import io.fabric8.maven.docker.config.Arguments;
import io.fabric8.maven.docker.config.AssemblyConfiguration;
import io.fabric8.maven.docker.config.BuildImageConfiguration;
import io.fabric8.maven.docker.config.ImageConfiguration;
import org.apache.maven.plugins.assembly.model.Assembly;
import org.apache.maven.plugins.assembly.model.FileItem;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static io.fabric8.maven.docker.util.JibServiceUtil.BUSYBOX;
import static io.fabric8.maven.docker.util.JibServiceUtil.containerFromImageConfiguration;
import static org.junit.jupiter.api.condition.OS.LINUX;
import static org.junit.jupiter.api.condition.OS.MAC;

@ExtendWith(MockitoExtension.class)
class JibServiceUtilTest {
    @Test
    void testGetBaseImageWithNullBuildConfig() {
        Assertions.assertEquals(BUSYBOX, JibServiceUtil.getBaseImage(new ImageConfiguration.Builder().build()));
    }

    @Test
    void testGetBaseImageWithNotNullBuildConfig() {
        Assertions.assertEquals("quay.io/jkubeio/jkube-test-image:0.0.1", JibServiceUtil.getBaseImage(new ImageConfiguration.Builder()
                .buildConfig(new BuildImageConfiguration.Builder()
                        .from("quay.io/jkubeio/jkube-test-image:0.0.1")
                        .build())
                .build()));
    }

    @Test
    @Disabled("Cannot mock static methods, JibServiceUtil needs to be refactored as non-static and/or expose methods to spy on")
    void testContainerFromImageConfiguration() throws Exception {
        // Given
        ImageConfiguration imageConfiguration = getSampleImageConfiguration();
        // When
        JibContainerBuilder jcb = containerFromImageConfiguration(ImageFormat.Docker.name(), imageConfiguration, null);
        JibContainerBuilder jibContainerBuilder= Mockito.spy(jcb);
        // Then
        Mockito.verify(jibContainerBuilder).addLabel("foo", "bar");
        Mockito.verify(jibContainerBuilder).setEntrypoint(Arrays.asList("java", "-jar", "foo.jar"));
        Mockito.verify(jibContainerBuilder).setExposedPorts(new HashSet<>(Collections.singletonList(Port.tcp(8080))));
        Mockito.verify(jibContainerBuilder).setUser("root");
        Mockito.verify(jibContainerBuilder).setWorkingDirectory(AbsoluteUnixPath.get("/home/foo"));
        Mockito.verify(jibContainerBuilder).setVolumes(new HashSet<>(Collections.singletonList(AbsoluteUnixPath.get("/mnt/volume1"))));
        Mockito.verify(jibContainerBuilder).setFormat(ImageFormat.Docker);
    }

    @Test
    @EnabledOnOs({ LINUX, MAC })
    void testCopyToContainer(@Mock JibContainerBuilder containerBuilder) throws IOException {
        // Given
        File temporaryDirectory = Files.createTempDirectory("jib-test").toFile();
        File temporaryFile = new File(temporaryDirectory, "foo.txt");
        boolean wasNewFileCreated = temporaryFile.createNewFile();
        String tmpRoot = temporaryDirectory.getParent();

        // When
        JibServiceUtil.copyToContainer(containerBuilder, temporaryDirectory, tmpRoot, Collections.emptyMap());

        // Then
        Assertions.assertTrue(wasNewFileCreated);
        ArgumentCaptor<FileEntriesLayer> fileEntriesLayerCaptor = ArgumentCaptor.forClass(FileEntriesLayer.class);

        Mockito.verify(containerBuilder).addFileEntriesLayer(fileEntriesLayerCaptor.capture());
        FileEntriesLayer fileEntriesLayer= fileEntriesLayerCaptor.getValue();

        Assertions.assertNotNull(fileEntriesLayer);
        Assertions.assertEquals(1, fileEntriesLayer.getEntries().size());
        Assertions.assertEquals(temporaryFile.toPath(), fileEntriesLayer.getEntries().get(0).getSourceFile());
        Assertions.assertEquals(AbsoluteUnixPath.fromPath(Paths.get(temporaryFile.getAbsolutePath().substring(tmpRoot.length()))),
                fileEntriesLayer.getEntries().get(0).getExtractionPath());
    }

    @Test
    void testAppendOriginalImageNameTagIfApplicable() {
        // Given
        List<String> imageTagList = Arrays.asList("0.0.1", "0.0.1-SNAPSHOT");

        // When
        Set<String> result = JibServiceUtil.getAllImageTags(imageTagList, "test-project");

        // Then
        Assertions.assertNotNull(result);
        Assertions.assertEquals(3, result.size());
        Assertions.assertArrayEquals(new String[]{"0.0.1-SNAPSHOT", "0.0.1", "latest"}, result.toArray());
    }

    @Test
    void testGetFullImageNameWithDefaultTag() {
        Assertions.assertEquals("test/test-project:latest", JibServiceUtil.getFullImageName(getSampleImageConfiguration(), null));
    }

    @Test
    void testGetFullImageNameWithProvidedTag() {
        Assertions.assertEquals("test/test-project:0.0.1", JibServiceUtil.getFullImageName(getSampleImageConfiguration(), "0.0.1"));
    }

    @Test
    void testGetImageFormat() {
        Assertions.assertEquals(ImageFormat.Docker, JibServiceUtil.getImageFormat("Docker"));
        Assertions.assertEquals(ImageFormat.OCI, JibServiceUtil.getImageFormat("OCI"));
        Assertions.assertEquals(ImageFormat.OCI, JibServiceUtil.getImageFormat("oci"));
    }

    private ImageConfiguration getSampleImageConfiguration() {
        Assembly assembly = new Assembly();
        FileItem fileItem = new FileItem();
        fileItem.setSource("${project.basedir}/foo");
        fileItem.setOutputDirectory("/deployments");
        assembly.addFile(fileItem);

        BuildImageConfiguration bc = new BuildImageConfiguration.Builder()
                .from("quay.io/test/testimage:testtag")
                .assembly(new AssemblyConfiguration.Builder()
                        .assemblyDef(assembly)
                        .build())
                .entryPoint(new Arguments.Builder().withParam("java")
                        .withParam("-jar")
                        .withParam("foo.jar")
                        .build())
                .labels(Collections.singletonMap("foo", "bar"))
                .user("root")
                .workdir("/home/foo")
                .ports(Collections.singletonList("8080"))
                .volumes(Collections.singletonList("/mnt/volume1"))
                .build();

        return new ImageConfiguration.Builder()
                .name("test/test-project")
                .buildConfig(bc)
                .build();
    }
}