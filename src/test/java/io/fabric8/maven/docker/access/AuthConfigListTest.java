package io.fabric8.maven.docker.access;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthConfigListTest {
    @Test
    void simpleConstructor() {
        AuthConfig config = getAuthConfig();
        AuthConfigList authConfigList = new AuthConfigList(config);

        assertEquals(config.toJson(), authConfigList.toJson());
    }

    @Test
    void emptyList() {
        AuthConfigList authConfigList = new AuthConfigList();

        assertEquals("{}", authConfigList.toJson());
    }

    @Test
    void addingToList() {
        AuthConfig config = getAuthConfig();

        AuthConfigList authConfigList = new AuthConfigList();
        authConfigList.addAuthConfig(config);

        assertEquals(config.toJson(), authConfigList.toJson());
    }

    @Test
    void multipleRegistries() {
        AuthConfig config1 = getAuthConfig();
        AuthConfig config2 = getAuthConfig();

        config1.setRegistry("registry-one.org");
        config2.setRegistry("registry-two.org");

        AuthConfigList authConfigList = new AuthConfigList();
        authConfigList.addAuthConfig(config1);
        authConfigList.addAuthConfig(config2);

        assertTrue(authConfigList.toJson().contains("registry-one.org"));
        assertTrue(authConfigList.toJson().contains("registry-two.org"));
    }

    private AuthConfig getAuthConfig() {
        Map<String,String> map = new HashMap<>();
        map.put(AuthConfig.AUTH_USERNAME,"username");
        map.put(AuthConfig.AUTH_PASSWORD,"#>secrets??");
        map.put(AuthConfig.AUTH_EMAIL,"username@email.org");
        return new AuthConfig(map);
    }
}