package io.fabric8.maven.docker.config;

import java.lang.reflect.Field;
import java.util.Map;

import org.junit.Test;
import static org.junit.Assert.*;

public class RegistryAuthConfigurationTest {

    @Test
    public void deprecatedAuthTokenTest() throws ReflectiveOperationException {
        RegistryAuthConfiguration config = new RegistryAuthConfiguration();
        setField(config, "authToken", "foo");
        Map map = config.toMap();
        assertNull(map.get("authToken"));
        assertEquals("foo", map.get("auth"));
    }

    @Test
    public void deprecatedAuthTokenOverrideTest() throws ReflectiveOperationException {
        RegistryAuthConfiguration config = new RegistryAuthConfiguration();
        setField(config, "authToken", "foo");
        setField(config, "auth", "bar");
        Map map = config.toMap();
        assertNull(map.get("authToken"));
        assertEquals("bar", map.get("auth"));
    }

        @Test
    public void authTest() throws ReflectiveOperationException {
        RegistryAuthConfiguration config = new RegistryAuthConfiguration();
        setField(config, "auth", "bar");
        Map map = config.toMap();
        assertNull(map.get("authToken"));
        assertEquals("bar", map.get("auth"));
    }


    private void setField(Object obj, String name, Object value) throws ReflectiveOperationException {
        Field field  = obj.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(obj, value);
    }
}
