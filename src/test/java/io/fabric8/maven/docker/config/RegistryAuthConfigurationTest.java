package io.fabric8.maven.docker.config;

import java.lang.reflect.Field;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;


class RegistryAuthConfigurationTest {

    @Test
    void deprecatedAuthTokenTest() throws ReflectiveOperationException {
        RegistryAuthConfiguration config = new RegistryAuthConfiguration();
        setField(config, "authToken", "foo");
        Map map = config.toMap();
        Assertions.assertNull(map.get("authToken"));
        Assertions.assertEquals("foo", map.get("auth"));
    }

    @Test
    void invalidAuthTokenConfigTest() throws ReflectiveOperationException {
        RegistryAuthConfiguration config = new RegistryAuthConfiguration();
        setField(config, "authToken", "foo");
        setField(config, "auth", "bar");
        Assertions.assertThrows(IllegalStateException.class, config::toMap);
    }

        @Test
    void authTest() throws ReflectiveOperationException {
        RegistryAuthConfiguration config = new RegistryAuthConfiguration();
        setField(config, "auth", "bar");
        Map map = config.toMap();
        Assertions.assertNull(map.get("authToken"));
        Assertions.assertEquals("bar", map.get("auth"));
    }


    private void setField(Object obj, String name, Object value) throws ReflectiveOperationException {
        Field field  = obj.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(obj, value);
    }
}
