package org.jolokia.docker.maven.config.handler.compose;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

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
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class DockerComposeConfigHandlerTest extends AbstractConfigHandlerTest {

    private DockerComposeConfigHandler handler;

    private List<ImageConfiguration> resolved;

    private ImageConfiguration unresolved;

    private String composeBuildDir;
    private String dockerFileDir;
    
    @Mock
    private DockerComposeValueProvider provider;
    
    
    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        
        this.handler = new DockerComposeConfigHandler();
    }

    @Test
    public void testFullResolve() {
        givenAnUnresolvedImage();
        whenResolveImages();
        thenResolveImageSizeIs(1);
        thenResolvedImageIsCorrect();
    }

//    @Test
//    public void testBuildPathIsDot()
//    {
//        whenBuildDockerFile();
//
//    }
    
    private void givenBuildDirIsDot()
    {
        composeBuildDir = ".";
        when(provider.getBuildDir()).thenReturn(composeBuildDir);
    }
    
    private void whenBuildDockerFile()
    {
        dockerFileDir = handler.buildDockerFileDir(provider, composeBuildDir);
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

        ImageConfiguration service = new ServiceImageBuilder("service")
                .portPropertyFile("/tmp/props.txt")
                .skipRun(true)
                .build();
        
        DockerComposeConfiguration composeConfig = new DockerComposeConfiguration.Builder()
                .yamlFile(getClass().getResource("/compose/docker-compose.yml").getFile())
                .addService(service)
                .build();

        ExternalImageConfiguration externalConfig = new ExternalImageConfiguration.Builder()
                .compose(composeConfig)
                .build();

        unresolved = new ImageConfiguration.Builder()
                .externalConfig(externalConfig)
                .build();
    }

    private void thenBuildImageIsCorrect() {
        
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

    class ServiceImageBuilder {
        private final BuildImageConfiguration.Builder buildBuilder = new BuildImageConfiguration.Builder();

        private final ImageConfiguration.Builder imageBuilder = new ImageConfiguration.Builder();

        private final RunImageConfiguration.Builder runBuilder = new RunImageConfiguration.Builder();

        public ServiceImageBuilder(String alias) {
            imageBuilder.alias(alias);
        }

        public ImageConfiguration build() {
            return imageBuilder.buildConfig(buildBuilder.build())
                    .runConfig(runBuilder.build())
                    .build();
        }

        public ServiceImageBuilder cleanup(boolean cleanup) {
            buildBuilder.cleanup(toStr(cleanup));
            return this;
        }

        public ServiceImageBuilder compression(String compression) {
            buildBuilder.compression(compression);
            return this;
        }

        public ServiceImageBuilder image(String image) {
            imageBuilder.name(image);
            return this;
        }

        public ServiceImageBuilder noCache(boolean noCache) {
            buildBuilder.nocache(toStr(noCache));
            return this;
        }
        
        public ServiceImageBuilder portPropertyFile(String portPropertyFile) {
            runBuilder.portPropertyFile(portPropertyFile);
            return this;
        }
        
        public ServiceImageBuilder skipBuild(boolean skip) {
            buildBuilder.skip(toStr(skip));
            return this;
        }
        
        public ServiceImageBuilder skipRun(boolean skip) {
            runBuilder.skip(toStr(skip));
            return this;
        }
        
        private String toStr(boolean bool) {
            return String.valueOf(bool);
        }
    }
}
