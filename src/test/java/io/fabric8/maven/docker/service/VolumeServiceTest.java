package io.fabric8.maven.docker.service;


import static org.junit.Assert.*;
import static org.hamcrest.Matchers.*;

import io.fabric8.maven.docker.access.DockerAccess;
import io.fabric8.maven.docker.access.VolumeCreateConfig;
import io.fabric8.maven.docker.config.VolumeConfiguration;
import io.fabric8.maven.docker.util.Logger;

import java.lang.String;
import java.util.HashMap;
import java.util.Map;

import mockit.Expectations;
import mockit.Mocked;

import org.hamcrest.core.Is;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 *  Basic Unit Tests for {@link VolumeService}  
 *  
 *  @author Tom Burton
 *  @version Dec 16, 2016
 */
public class VolumeServiceTest
{
   private VolumeCreateConfig volumeConfig;
   
   @Mocked
   private DockerAccess docker;
   
   private VolumeService volumeService;

   /**
    * @throws java.lang.Exception
    */
   @Before
   public void setUp() throws Exception 
   { volumeService = new VolumeService(docker); }

   /**
    * @throws java.lang.Exception
    */
   @After
   public void tearDown() throws Exception { }
   
   /*
    * methods to test
    * 
    * volumeService.createVolumeConfig(volumeName, driver, driverOpts, labels);
    * volumeService.removeVolume(volumeName);
    */
   
   private Map<String, String > withMap()
   {
      Map<String, String> map = new HashMap<String, String>();
      map.put("key1", "value1");
      map.put("key2", "value2");
      
      return map;
   }
   
   private void validateMap(Map<String, String> map)
   {
      assertThat(map.size(), is(2));
      
      assertThat(map, hasKey("key1"));
      assertThat(map, hasValue("value1"));
      assertThat(map.get("key1"), is("value1"));
      
      assertThat(map, hasKey("key2"));
      assertThat(map, hasValue("value2"));
      assertThat(map.get("key2"), is("value2"));
   }
   
   
   @Test
   public void testCreateVolumeConfig() throws Exception
   {
      /* in case VolumeConfiguration ever implements equals
      VolumeConfiguration vc = VolumeConfiguration.builder()
            .name("testVolume")
            .driver("test").driverOpts(withMap())
            .labels(withMap())
            .build();
      */

      VolumeCreateConfig vcc = volumeService.createVolumeConfig("testVolume", 
                                                                "test", withMap(), 
                                                                withMap());
      assertThat(vcc.getVolumeName(), is("testVolume"));
      
      String vccJson = vcc.toJson();
      
      assertThat(vccJson, containsString('"'+"Driver"+'"'+':'+'"'+"test"+'"'));
   }

   @Test
   public void testCreateVolume() throws Exception
   {
      
      VolumeConfiguration vc = VolumeConfiguration.builder()
                                     .name("testVolume")
                                     .driver("test").driverOpts(withMap())
                                     .labels(withMap())
                                     .build();
      
      new Expectations() {{
         docker.createVolume((VolumeCreateConfig)any); result = "testVolume";
      }};
      
      assertThat(vc.getName(), is("testVolume"));
      String name = volumeService.createVolume(vc);
      
      assertThat(name, is("testVolume"));
   }
   
   @Test
   public void testRemoveVolume() throws Exception
   {
      VolumeConfiguration vc = VolumeConfiguration.builder()
            .name("testVolume")
            .driver("test").driverOpts(withMap())
            .labels(withMap())
            .build();

      String name = volumeService.createVolume(vc);
      
      volumeService.removeVolume(name);
      
      //TODO: add real verification here
      assertTrue(true);
   }
   
}
