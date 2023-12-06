## d-m-p sample using a Dockerfile

This example shows how to use docker-maven-plugin together with a Dockerfile. 
It is a simple `HelloWorld` servlet running on top of Jetty at the root context.
 
The [Dockerfile](src/main/docker/Dockerfile) is located is `src/main/docker`. 
Please note how the assembly is added using the directory `maven` which will be created on the fly by this plugin when an `<assembly>` is specified. 
 
To build and start a Jetty container with a mapped port at 8080 on the Docker host use

```
mvn package docker:build docker:run
```