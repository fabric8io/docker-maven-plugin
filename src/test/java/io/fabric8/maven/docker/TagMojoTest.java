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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TagMojoTest extends MojoTestBase {

    private static final String NON_NATIVE_PLATFORM = "linux/amd64";
    private static final String NATIVE_PLATFORM = "linux/arm64";

    @InjectMocks
    private TagMojo tagMojo;

    void tag(boolean skipTag, boolean buildx) throws IOException, MojoExecutionException {
        givenMavenProject(tagMojo);

        ImageConfiguration imageConfiguration = singleImageConfiguration(builder -> {
            builder.skipTag(skipTag);
            if (buildx) {
                ArrayList<String> platforms = new ArrayList<>();
                platforms.add(NON_NATIVE_PLATFORM);
                platforms.add(NATIVE_PLATFORM);
                BuildXConfiguration buildXConfiguration = new BuildXConfiguration.Builder().platforms(platforms).build();
                builder.buildx(buildXConfiguration);
            }
        });
        givenResolvedImages(tagMojo, Collections.singletonList(imageConfiguration));

        tagMojo.executeInternal(serviceHub);

        verify(buildService, times(skipTag || buildx ? 0 : 1)).tagImage(any(), any(), any(), any());
    }

    @Test
    void skipTag() throws IOException, MojoExecutionException {
        tag(true, false);
    }

    @Test
    void tag() throws IOException, MojoExecutionException {
        tag(false, false);
    }

    @Test
    void tagWhenUsingBuildx() throws IOException, MojoExecutionException {
        tag(false, true);
    }
}
