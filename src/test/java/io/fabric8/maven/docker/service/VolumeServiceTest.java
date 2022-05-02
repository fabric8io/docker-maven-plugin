package io.fabric8.maven.docker.service;

import com.google.gson.JsonObject;
import io.fabric8.maven.docker.access.DockerAccess;
import io.fabric8.maven.docker.access.VolumeCreateConfig;
import io.fabric8.maven.docker.config.VolumeConfiguration;
import io.fabric8.maven.docker.util.JsonFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

/**
 *  Basic Unit Tests for {@link VolumeService}
 *
 *  @author Tom Burton
 *  @version Dec 16, 2016
 */
@ExtendWith(MockitoExtension.class)
class VolumeServiceTest {
   private VolumeCreateConfig volumeConfig;

   @Mock
   private DockerAccess docker;

   /*
    * methods to test
    *
    * volumeService.createVolumeConfig(volumeName, driver, driverOpts, labels);
    * volumeService.removeVolume(volumeName);
    */

   private Map<String, String > withMap(String what) {
      Map<String, String> map = new HashMap<>();
      map.put(what + "Key1", "value1");
      map.put(what + "Key2", "value2");

      return map;
   }

   @Test
   void testCreateVolumeConfig() throws Exception {
      final VolumeConfiguration config =
          new VolumeConfiguration.Builder()
              .name("testVolume")
              .driver("test")
              .opts(withMap("opts"))
              .labels(withMap("labels"))
              .build();

      Mockito.doReturn("testVolume").when(docker).createVolume(Mockito.any(VolumeCreateConfig.class));

      String volume = new VolumeService(docker).createVolume(config);
      Assertions.assertEquals("testVolume", volume);

      ArgumentCaptor<VolumeCreateConfig> volumeCreateConfigCaptor = ArgumentCaptor.forClass(VolumeCreateConfig.class);
      Mockito.verify(docker).createVolume(volumeCreateConfigCaptor.capture());
      VolumeCreateConfig vcc= volumeCreateConfigCaptor.getValue();
      Assertions.assertEquals("testVolume", vcc.getName());
      JsonObject vccJson = JsonFactory.newJsonObject(vcc.toJson());
      Assertions.assertEquals("test", vccJson.get("Driver").getAsString());
   }

   @Test
   void testCreateVolume() throws Exception {
      VolumeConfiguration vc = new VolumeConfiguration.Builder()
                                     .name("testVolume")
                                     .driver("test").opts(withMap("opts"))
                                     .labels(withMap("labels"))
                                     .build();

      Mockito.doReturn("testVolume")
          .when(docker).createVolume(Mockito.any(VolumeCreateConfig.class));

      Assertions.assertEquals("testVolume", vc.getName());
      String name = new VolumeService(docker).createVolume(vc);
      Assertions.assertEquals("testVolume", name);
   }

   @Test
   void testRemoveVolume() throws Exception {
      VolumeConfiguration vc = new VolumeConfiguration.Builder()
            .name("testVolume")
            .driver("test").opts(withMap("opts"))
            .labels(withMap("labels"))
            .build();

      Mockito.doReturn("testVolume").when(docker).createVolume(Mockito.any(VolumeCreateConfig.class));

      VolumeService volumeService = new VolumeService(docker);
      String name = volumeService.createVolume(vc);
      volumeService.removeVolume(name);

      Mockito.verify(docker).removeVolume("testVolume");
   }

}
