package io.fabric8.maven.docker.model;


import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.hamcrest.Matchers.*;

public class VolumeDetailsTest 
{

    private VolumeDetails volume;

    private JSONObject json;

    @Before
    public void setup() { json = new JSONObject(); }

    @Test
    public void testVolumeWithLabels() {
        givenAVolumeWithLabels();
        whenCreateVolume();
        thenLabelsSizeIs(2);
        thenLabelsContains("key1", "value1");
        thenLabelsContains("key2", "value2");
    }
    
    @Test
    public void testVolumeWithStatus() 
    {
        givenAVolumeWithStatus();
        whenCreateVolume();
        assertThat(volume.getStatus().size(), is(2));
        thenStatusContains("key1", "value1");
        thenStatusContains("key2", "value2");
    }

    @Test
    public void testCreateVolume() throws Exception 
    {
        givenVolumeData();
        whenCreateVolume();
        thenValidateVolume();
    }
    
    private void thenLabelsContains(String key, String value)
    {
       assertThat(volume.getLabels(),          hasKey(key));
       assertThat(volume.getLabels().get(key), is(value));
    } 
    
    private void thenStatusContains(String key, String value) 
    {
       assertThat(volume.getStatus(),          hasKey(key));
       assertThat(volume.getStatus().get(key), is(value));
    }

    private void givenAVolumeWithLabels() {
        JSONObject labels = new JSONObject();
        labels.put("key1", "value1");
        labels.put("key2", "value2");
        
        json.put(VolumeDetails.LABELS, labels);
    }
    
    private void givenAVolumeWithStatus() 
    {
       JSONObject status = new JSONObject();
       status.put("key1", "value1");
       status.put("key2", "value2");
       
       json.put(VolumeDetails.STATUS, status);
   }

    private void givenVolumeData() 
    {
        json.put(VolumeDetails.NAME,       "/milkman-kindness");
        json.put(VolumeDetails.DRIVER,     "test-driver");
        json.put(VolumeDetails.MOUNTPOINT, "/ver/lib/docker/testVolume");

        json.put(VolumeDetails.SCOPE, VolumeDetails.SCOPE_LOCAL);
    }

    private void thenLabelsSizeIs(int size) 
    { assertThat(volume.getLabels().size(), is(2)); }

    private void thenValidateVolume() 
    {
        assertThat(volume.getName(),       is("milkman-kindness"));
        assertThat(volume.getDriver(),     is("test-driver"));
        assertThat(volume.getMountpoint(), is("/ver/lib/docker/testVolume"));
        assertThat(volume.getScope(),      is(VolumeDetails.SCOPE_LOCAL));
        
        try { assertThat(volume.getLabels(), is(nullValue())); }
        catch(JSONException je)
        { assertThat(je.getMessage(), is("JSONObject[\"Labels\"] not found.")); }
        
        try { assertThat(volume.getStatus(), is(nullValue())); }
        catch(JSONException je)
        { assertThat(je.getMessage(), is("JSONObject[\"Status\"] not found.")); }  
    }

    private void whenCreateVolume() { volume = new VolumeDetails(json); }

}
