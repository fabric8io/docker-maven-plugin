package io.fabric8.maven.docker.model;

public interface Network {

    String getName();

    String getId();

    String getScope();

    String getDriver();

}
