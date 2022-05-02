package io.fabric8.maven.docker.config.handler.property;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PropertyModeTest {

    @ParameterizedTest
    @ValueSource(strings = {"", "propertiespom"})
    void testInvalid(String invalid) {
        Assertions.assertThrows(IllegalArgumentException.class, () ->PropertyMode.parse(invalid));
    }

    @Test
    void testParse() {
        Assertions.assertEquals(PropertyMode.Only, PropertyMode.parse(null));
        Assertions.assertEquals(PropertyMode.Only, PropertyMode.parse("only"));
        Assertions.assertEquals(PropertyMode.Fallback, PropertyMode.parse("fallback"));
    }
}