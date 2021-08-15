package io.fabric8.maven.docker;

import io.fabric8.maven.docker.access.DockerAccessException;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.service.RegistryService;
import mockit.Deencapsulation;
import mockit.Mocked;
import mockit.Tested;
import mockit.Verifications;
import org.apache.maven.plugin.MojoExecutionException;
import org.junit.Test;

import java.io.IOException;
import java.util.Collection;

public class PushMojoTest extends BaseMojoTest {
  @Tested
  private PushMojo pushMojo;

  @Mocked
  private RegistryService registryService;

  @Test
  public void executeInternal_whenSkipPomEnabledInPomPackaging_thenImagePushSkipped() throws MojoExecutionException, IOException {
    Deencapsulation.setField(serviceHub, "registryService", registryService);
    givenMavenProject(pushMojo);
    givenPackaging("pom");
    givenSkipPom(true);

    whenMojoExecutes();

    thenImageNotPushed();
  }

  @Test
  public void executeInternal_whenPomPackaging_thenImageIsPushed() throws MojoExecutionException, IOException {
    Deencapsulation.setField(serviceHub, "registryService", registryService);
    givenMavenProject(pushMojo);
    givenPackaging("pom");
    givenSkipPom(false);

    whenMojoExecutes();

    thenImagePushed();
  }

  @Test
  public void executeInternal_whenJarPackaging_thenImageIsPushed() throws MojoExecutionException, IOException {
    Deencapsulation.setField(serviceHub, "registryService", registryService);
    givenMavenProject(pushMojo);
    givenPackaging("jar");

    whenMojoExecutes();

    thenImagePushed();
  }

  @Test
  public void executeInternal_whenSkipEnabled_thenImageIsPushed() throws MojoExecutionException, IOException {
    Deencapsulation.setField(serviceHub, "registryService", registryService);
    givenMavenProject(pushMojo);
    givenPackaging("jar");
    givenSkipPush(true);

    whenMojoExecutes();

    thenImageNotPushed();
  }

  @Test
  public void executeInternal_whenSkipDisabled_thenImageIsPushed() throws MojoExecutionException, IOException {
    Deencapsulation.setField(serviceHub, "registryService", registryService);
    givenMavenProject(pushMojo);
    givenPackaging("jar");
    givenSkipPush(false);

    whenMojoExecutes();

    thenImagePushed();
  }

  private void thenImagePushed() throws MojoExecutionException, DockerAccessException {
    new Verifications() {{
      registryService.pushImages((Collection<ImageConfiguration>) any, anyInt, (RegistryService.RegistryConfig)any, anyBoolean);
      times = 1;
    }};
  }

  private void thenImageNotPushed() throws DockerAccessException, MojoExecutionException {
    new Verifications() {{
      registryService.pushImages((Collection<ImageConfiguration>) any, anyInt, (RegistryService.RegistryConfig)any, anyBoolean);
      times = 0;
    }};
  }

  private void whenMojoExecutes() throws IOException, MojoExecutionException {
    pushMojo.executeInternal(serviceHub);
  }

  private void givenPackaging(String packaging) {
    Deencapsulation.setField(pushMojo, "packaging", packaging);
  }

  private void givenSkipPom(boolean skipPom) {
    Deencapsulation.setField(pushMojo, "skipPom", skipPom);
  }

  private void givenSkipPush(boolean skip) {
    Deencapsulation.setField(pushMojo, "skipPush", skip);
  }

}
