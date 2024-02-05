package io.fabric8.maven.docker;

import io.fabric8.maven.docker.access.DockerAccessException;
import io.fabric8.maven.docker.service.RegistryService;
import io.fabric8.maven.docker.util.MojoParameters;
import io.fabric8.maven.docker.util.ProjectPaths;
import org.apache.maven.plugin.MojoExecutionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;

@ExtendWith(MockitoExtension.class)
class PushMojoTest extends MojoTestBase {
  @InjectMocks
  private PushMojo pushMojo;

  @Test
  void executeInternal_whenSkipPomEnabledInPomPackaging_thenImagePushSkipped() throws MojoExecutionException, IOException {
    givenMavenProject(pushMojo);
    givenPackaging("pom");
    givenSkipPom(true);

    whenMojoExecutes();

    thenImageNotPushed();
  }

  @Test
  void executeInternal_whenPomPackaging_thenImageIsPushed() throws MojoExecutionException, IOException {
    givenMavenProject(pushMojo);
    givenPackaging("pom");
    givenSkipPom(false);

    whenMojoExecutes();

    thenImagePushed();
  }

  @Test
  void executeInternal_whenJarPackaging_thenImageIsPushed() throws MojoExecutionException, IOException {
    givenMavenProject(pushMojo);
    givenPackaging("jar");

    whenMojoExecutes();

    thenImagePushed();
  }

  @Test
  void executeInternal_whenSkipEnabled_thenImageIsPushed() throws MojoExecutionException, IOException {
    givenMavenProject(pushMojo);
    givenPackaging("jar");
    givenSkipPush(true);

    whenMojoExecutes();

    thenImageNotPushed();
  }

  @Test
  void executeInternal_whenSkipDisabled_thenImageIsPushed() throws MojoExecutionException, IOException {
    givenMavenProject(pushMojo);
    givenPackaging("jar");
    givenSkipPush(false);

    whenMojoExecutes();

    thenImagePushed();
  }

  private void thenImagePushed() throws MojoExecutionException, DockerAccessException {
    verifyPush(1);
  }

  private void thenImageNotPushed() throws DockerAccessException, MojoExecutionException {
    verifyPush(0);
  }

  private void verifyPush(int wantedNumberOfInvocations) throws DockerAccessException, MojoExecutionException {
    Mockito.verify(registryService, Mockito.times(wantedNumberOfInvocations))
        .pushImages(Mockito.any(ProjectPaths.class), Mockito.anyCollection(), Mockito.anyInt(), Mockito.any(RegistryService.RegistryConfig.class), Mockito.anyBoolean(), Mockito.any(MojoParameters.class));
  }

  private void whenMojoExecutes() throws IOException, MojoExecutionException {
    pushMojo.executeInternal(serviceHub);
  }

  private void givenPackaging(String packaging) {
    pushMojo.packaging= packaging;
  }

  private void givenSkipPom(boolean skipPom) {
    pushMojo.skipPom= skipPom;
  }

  private void givenSkipPush(boolean skip) {
    pushMojo.skipPush= skip;
  }
}
