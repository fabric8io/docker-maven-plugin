package io.fabric8.maven.docker.config.handler.property;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class PropertyModeTest {

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidEmpty() {
        PropertyMode.parse("");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidParse() {
        PropertyMode.parse("propertiespom");
    }
    @Test
    public void testParse() {
        assertEquals(PropertyMode.Only, PropertyMode.parse(null));
        assertEquals(PropertyMode.Only, PropertyMode.parse("only"));
        assertEquals(PropertyMode.Fallback, PropertyMode.parse("fallback"));
    }
}