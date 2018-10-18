package io.fabric8.maven.docker.build;

import java.io.File;
import java.io.IOException;
import java.util.Properties;
import java.util.function.Function;

import io.fabric8.maven.docker.config.build.BuildImageConfiguration;
import io.fabric8.maven.docker.util.Logger;

/**
 * @author roland
 * @since 16.10.18
 */
public interface BuildContext {

    String getSourceDirectory();

    File getBasedir();

    String getOutputDirectory();

    Properties getProperties();

    Function<String, String> createInterpolator(String filter);

    File createImageContentArchive(String imageName, BuildImageConfiguration buildConfig, Logger log) throws IOException;

    RegistryContext getRegistryContext();
}
