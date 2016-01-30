package org.jolokia.docker.maven.config.handler.compose;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Map;

import org.jolokia.docker.maven.config.BuildImageConfiguration;
import org.jolokia.docker.maven.config.ImageConfiguration;
import org.jolokia.docker.maven.config.RunImageConfiguration;
import org.jolokia.docker.maven.config.RunImageConfiguration.NamingStrategy;
import org.jolokia.docker.maven.config.external.DockerComposeConfiguration;
import org.jolokia.docker.maven.config.external.ExternalImageConfiguration;
import org.jolokia.docker.maven.config.handler.AbstractConfigHandlerTest;
import org.junit.Before;
import org.junit.Test;

public class DockerComposeConfigHandlerTest extends AbstractConfigHandlerTest {

    private DockerComposeConfigHandler handler;

    private List<ImageConfiguration> resolved;

    private ImageConfiguration unresolved;

    @Before
    public void setup() {
        this.handler = new DockerComposeConfigHandler();
    }

    @Test
    public void testFullResolve() {
        givenAnUnresolvedImage();
        whenResolveImages();
        thenResolveImageSizeIs(1);
        thenResolvedImageIsCorrect();
    }

    @Override
    protected String getEnvPropertyFile() {
        // this predates compose support and doesn't work the same way
        return null;
    }
    
    @Override
    protected NamingStrategy getRunNamingStrategy() {
        return NamingStrategy.alias;
    }

    @Override
    protected void validateEnv(Map<String, String> env) {
        assertEquals(2, env.size());
        assertEquals("name", env.get("NAME"));
        assertEquals("true", env.get("BOOL"));
    }
    
    private void givenAnUnresolvedImage() {
        
        
        
        
//        DockerComposeConfiguration.Service service = new DockerComposeConfiguration.Service.Builder("service")
//                .portPropertyFile("/tmp/props.txt")
//                .skipRun(true)
//                .build();
//        
        DockerComposeConfiguration composeConfig = new DockerComposeConfiguration.Builder()
                .yamlFile(getClass().getResource("/compose/docker-compose.yml").getFile())
               // .addService(service)
                .build();

        ExternalImageConfiguration externalConfig = new ExternalImageConfiguration.Builder()
                .compose(composeConfig)
                .build();

        unresolved = new ImageConfiguration.Builder()
                .externalConfig(externalConfig)
                .build();
    }
    
    private void thenResolvedImageIsCorrect() {
        ImageConfiguration config = resolved.get(0);

        assertEquals("image", config.getName());
        assertEquals("service", config.getAlias());

        validateRunConfiguration(config.getRunConfiguration());
        assertTrue(config.getRunConfiguration().skip());
    }

    private void thenResolveImageSizeIs(int size) {
        assertEquals(size, resolved.size());
    }

    private void whenResolveImages() {
        resolved = handler.resolve(unresolved, null);
    }
    
    public static class ServiceImageBuilder {
        private final RunImageConfiguration.Builder runBuilder = new RunImageConfiguration.Builder();

        private final BuildImageConfiguration.Builder buildBuilder = new BuildImageConfiguration.Builder();

        private final ImageConfiguration.Builder imageBuilder = new ImageConfiguration.Builder();

        public ServiceImageBuilder image(String image) {
            imageBuilder.name(image);
            return this;
        }

        public ServiceImageBuilder alias(String alias) {
            imageBuilder.alias(alias);
            return this;
        }

        public ServiceImageBuilder cleanup(boolean cleanup) {
            buildBuilder.cleanup(String.valueOf(cleanup));
            return this;
        }
        
//        public ServiceImageBuilder noCache(boolean noCache) {
//
//        }
        
        public ImageConfiguration build() {
            return imageBuilder.buildConfig(buildBuilder.build())
                    .runConfig(runBuilder.build())
                    .build();
        }
    }
}
