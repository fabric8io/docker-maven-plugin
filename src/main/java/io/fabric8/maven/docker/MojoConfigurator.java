package io.fabric8.maven.docker;

import io.fabric8.maven.docker.assembly.DockerFileKeyword;
import io.fabric8.maven.docker.config.RunCommand;
import org.codehaus.plexus.component.configurator.BasicComponentConfigurator;
import org.codehaus.plexus.component.configurator.ComponentConfigurationException;
import org.codehaus.plexus.component.configurator.ConfigurationListener;
import org.codehaus.plexus.component.configurator.converters.ConfigurationConverter;
import org.codehaus.plexus.component.configurator.converters.lookup.ConverterLookup;
import org.codehaus.plexus.component.configurator.expression.ExpressionEvaluator;
import org.codehaus.plexus.configuration.PlexusConfiguration;

import javax.inject.Named;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Configure a converter for runcmds that can be either USER or RUN with value as parameter
 */

@Named("fabric8-mojo-configurator")
public class MojoConfigurator extends BasicComponentConfigurator {
    private static final Set<String> VALID_KEYWORDS = new HashSet<>(Arrays.asList("RUN", "USER"));

    public MojoConfigurator() {
        converterLookup.registerConverter(new ConfigurationConverter() {
            @Override
            public boolean canConvert(Class<?> aClass) {
                return RunCommand.class == aClass;
            }

            @Override
            public Object fromConfiguration(ConverterLookup converterLookup, PlexusConfiguration plexusConfiguration, Class<?> aClass, Class<?> aClass1, ClassLoader classLoader, ExpressionEvaluator expressionEvaluator) throws ComponentConfigurationException {
                return fromConfiguration(converterLookup, plexusConfiguration, aClass, aClass1, classLoader, expressionEvaluator, null);
            }

            @Override
            public Object fromConfiguration(ConverterLookup converterLookup, PlexusConfiguration plexusConfiguration, Class<?> aClass, Class<?> aClass1, ClassLoader classLoader, ExpressionEvaluator expressionEvaluator, ConfigurationListener configurationListener) throws ComponentConfigurationException {
                String keyword = plexusConfiguration.getName().trim().toUpperCase();
                if (!VALID_KEYWORDS.contains(keyword) || plexusConfiguration.getValue() == null) return null;
                return new RunCommand(DockerFileKeyword.valueOf(keyword), plexusConfiguration.getValue().trim());
            }
        });
    }
}
