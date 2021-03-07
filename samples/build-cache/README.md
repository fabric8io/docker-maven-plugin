## d-m-p sample utilizing docker build cache

This example shows:

1. How to use docker-maven-plugin together with [Spring Boot Layered JAR](https://spring.io/blog/2020/08/14/creating-efficient-docker-images-with-spring-boot-2-3) feature to utilize Docker build cache.
1. How to add files into the image with predefined permissions without [multi-stage build](https://docs.docker.com/develop/develop-images/multistage-build/).

It is a simple "Hello world" Spring Boot REST controller in the root context.
 
The [Dockerfile](image/src/main/docker/Dockerfile) is located in `image/src/main/docker`.

This example follows [reproducible builds](https://maven.apache.org/guides/mini/guide-reproducible-builds.html) approach
to generate archives which are friendly to the Docker build cache.

All content which needs to be added from the host into the image is packaged into archives of TAR format.
Content is separated based on the frequency of updates, so that changes in application code -
in [app/src/main/java/io/fabric8/dmp/samples/buildcache/Application.java](app/src/main/java/io/fabric8/dmp/samples/buildcache/Application.java) -
don't lead to modification of all archives, but impact just image/target/application.tar.

Generated archives are extracted inside image with `ADD` Dockefile directive adding the host content into the image with
desired UNIX file permissions, owner, group, creation and modification timestamps.

Dockerfile directives are ordered based on the frequency of updates, e.g. archive with application dependencies
(image/target/dependencies.tar) is added before archive with application code (image/target/application.tar) to avoid
creation of new image layer containing application dependencies (i.e. to take that layer from the Docker build cache)
when only application code changes.

When building the project multiple times, note that subsequent builds log "Using cache" messages, like:

```
$ mvn clean package
...
[INFO] DOCKER> Step 1 : FROM gcr.io/distroless/java-debian10
[INFO] DOCKER> ---> f6a5dc137f9b
[INFO] DOCKER> Step 2 : USER nonroot
[INFO] DOCKER> ---> Using cache
[INFO] DOCKER> ---> 26d64b9907e3
[INFO] DOCKER> Step 3 : ENTRYPOINT
[INFO] DOCKER> ---> Using cache
[INFO] DOCKER> ---> 6eb12980cd38
[INFO] DOCKER> Step 4 : WORKDIR "/app"
[INFO] DOCKER> ---> Using cache
[INFO] DOCKER> ---> d6a408147d7c
[INFO] DOCKER> Step 5 : CMD tini -e 130 -e 143 -- java org.springframework.boot.loader.JarLauncher
[INFO] DOCKER> ---> Using cache
[INFO] DOCKER> ---> 461505b7ade9
[INFO] DOCKER> Step 6 : ADD dependencies.tar /
[INFO] DOCKER> ---> Using cache
[INFO] DOCKER> ---> df87a8d003f3
[INFO] DOCKER> Step 7 : ADD spring-boot-loader.tar /
[INFO] DOCKER> ---> Using cache
[INFO] DOCKER> ---> 2de1bf3ac9ad
```
