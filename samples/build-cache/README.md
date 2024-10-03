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
[INFO] DOCKER> Step 1/10 : FROM gcr.io/distroless/java-debian10
[INFO] DOCKER>
[INFO] DOCKER> ---> 444adf12984c
[INFO] DOCKER> Step 2/10 : USER nonroot
[INFO] DOCKER>
[INFO] DOCKER> ---> Using cache
[INFO] DOCKER> ---> 043b487914d1
[INFO] DOCKER> Step 3/10 : WORKDIR "/app"
[INFO] DOCKER>
[INFO] DOCKER> ---> Using cache
[INFO] DOCKER> ---> 08ef41fef30f
[INFO] DOCKER> Step 4/10 : CMD ["tini", "-e", "130", "-e", "143", "--", "java", "org.springframework.boot.loader.JarLauncher"]
[INFO] DOCKER>
[INFO] DOCKER> ---> Using cache
[INFO] DOCKER> ---> 2ac94352abe0
[INFO] DOCKER> Step 5/10 : ADD ["dependencies.tar", "/"]
[INFO] DOCKER>
[INFO] DOCKER> ---> Using cache
[INFO] DOCKER> ---> aafdf67c0508
[INFO] DOCKER> Step 6/10 : ADD ["spring-boot-loader.tar", "/"]
[INFO] DOCKER>
[INFO] DOCKER> ---> Using cache
[INFO] DOCKER> ---> f593e7c3c58d
[INFO] DOCKER> Step 7/10 : ADD ["snapshot-dependencies.tar", "/"]
[INFO] DOCKER>
[INFO] DOCKER> ---> Using cache
[INFO] DOCKER> ---> ef1f89b783b5
[INFO] DOCKER> Step 8/10 : ADD ["application.tar", "/"]
[INFO] DOCKER>
[INFO] DOCKER> ---> Using cache
[INFO] DOCKER> ---> 055ba36bb45f
...
```
