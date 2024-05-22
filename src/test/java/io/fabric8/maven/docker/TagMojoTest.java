package io.fabric8.maven.docker;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import io.fabric8.maven.docker.config.BuildXConfiguration;
import io.fabric8.maven.docker.config.ImageConfiguration;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import org.apache.maven.plugin.MojoExecutionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TagMojoTest extends MojoTestBase {

    private static final String NON_NATIVE_PLATFORM = "linux/amd64";
    private static final String NATIVE_PLATFORM = "linux/arm64";

    @InjectMocks
    private TagMojo tagMojo;

    @Nested
    class NormalBuild {
        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        @DisplayName("If skipTag is true, call tag; otherwise, do not call")
        void tag(boolean skipTag) throws IOException, MojoExecutionException {
            givenMavenProject(tagMojo);

            ImageConfiguration imageConfiguration = singleImageConfiguration(builder -> {
                builder.skipTag(skipTag);
            });
            givenResolvedImages(tagMojo, Collections.singletonList(imageConfiguration));

            tagMojo.executeInternal(serviceHub);

            verify(buildService, times(skipTag ? 0 : 1)).tagImage(any(), any(), any(), any());
        }
    }

    @Nested
    class BuildX {
        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        @DisplayName("Always skip tag")
        void tag(boolean skipTag) throws IOException, MojoExecutionException {
            givenMavenProject(tagMojo);

            ImageConfiguration imageConfiguration = singleImageConfiguration(builder -> {
                builder.skipTag(skipTag);
                ArrayList<String> platforms = new ArrayList<>();
                platforms.add(NON_NATIVE_PLATFORM);
                platforms.add(NATIVE_PLATFORM);
                BuildXConfiguration buildXConfiguration =
                        new BuildXConfiguration.Builder().platforms(platforms).build();
                builder.buildx(buildXConfiguration);
            });
            givenResolvedImages(tagMojo, Collections.singletonList(imageConfiguration));

            tagMojo.executeInternal(serviceHub);

            verify(buildService, times(0)).tagImage(any(), any(), any(), any());
        }
    }
}
