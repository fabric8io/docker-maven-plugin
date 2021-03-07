package io.fabric8.maven.docker.config.handler.property;

/**
 * Identifies how the {@link PropertyConfigHandler} should treat properties vs configuration
 * from POM file in the {@link ValueProvider}.
 *
 * @author Johan Ström
 */
public enum PropertyMode {
    Only,
    Override,
    Fallback,
    Skip;

    /**
     * Given String, parse to a valid property mode.
     *
     * If null, the default Only is given.
     *
     * @param name null or a valid name
     * @return PropertyMode
     * @throws IllegalArgumentException if empty or invalid names
     */
    static PropertyMode parse(String name) {
        if(name == null) {
            return PropertyMode.Only;
        }

        name = name.toLowerCase();
        for (PropertyMode e : PropertyMode.values()) {
            if (e.name().toLowerCase().equals(name)) {
                return e;
            }
        }
        throw new IllegalArgumentException("PropertyMode: invalid mode "+name+". Valid: "+values());
    }
}
