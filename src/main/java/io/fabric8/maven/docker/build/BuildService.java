package io.fabric8.maven.docker.build;

import java.io.IOException;
import java.util.Map;

import io.fabric8.maven.docker.config.ImageConfiguration;

/**
 * @author roland
 * @since 16.10.18
 */
public interface BuildService {
    void buildImage(ImageConfiguration imageConfig, BuildContext buildContext, Map<String, String> buildArgs)
        throws IOException;
}
