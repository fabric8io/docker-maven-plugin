# docker-maven-plugin

[![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.fabric8/docker-maven-plugin/badge.svg?style=flat-square)](https://maven-badges.herokuapp.com/maven-central/io.fabric8/docker-maven-plugin/)
[![Travis](https://secure.travis-ci.org/fabric8io/docker-maven-plugin.png)](http://travis-ci.org/fabric8io/docker-maven-plugin)
[![Circle CI](https://circleci.com/gh/fabric8io/docker-maven-plugin/tree/integration.svg?style=shield)](https://circleci.com/gh/fabric8io/docker-maven-plugin/tree/integration)

> The Maven group id changed from version 0.13.9 to 0.14.0. Please update the Maven group id in your
> `pom.xml` to **io.fabric8** (formerly: org.jolokia). This is because of moving this personal 
> project to a [fabric8](http://fabric8.io) community project.

This is a Maven plugin for managing Docker images and containers for your builds.
It works with Maven 3.0.5 and Docker 1.6.0 or later.

The current Docker API version used is `v1.18` (which is the minimal required API version). If you want to use the 
copy mode for `docker:watch` you need `v1.20` or greater (Docker 1.8.1). For using custom networks `v1.21` (Docker 1.9) is required. See the **[User Manual](https://fabric8io.github.io/docker-maven-plugin)** 
for details on how to override this value for new
versions of Docker. 

#### Goals

| Goal                                          | Description                           |
| --------------------------------------------- | ------------------------------------- |
| [`docker:start`](https://fabric8io.github.io/docker-maven-plugin/docker-start.html)   | Create and start containers           |
| [`docker:stop`](https://fabric8io.github.io/docker-maven-plugin/docker-stop.html)     | Stop and destroy containers           |
| [`docker:env`](https://fabric8io.github.io/docker-maven-plugin/docker-env.md)         | Set environment variables            |
| [`docker:build`](https://fabric8io.github.io/docker-maven-plugin/docker-build.html)   | Build images                          |
| [`docker:watch`](https://fabric8io.github.io/docker-maven-plugin/docker-watch.html)   | Watch for doing rebuilds and restarts |
| [`docker:push`](https://fabric8io.github.io/docker-maven-plugin/docker-push.html)     | Push images to a registry             |
| [`docker:remove`](https://fabric8io.github.io/docker-maven-plugin/docker-remove.html) | Remove images from local docker host  |
| [`docker:logs`](https://fabric8io.github.io/docker-maven-plugin/docker-logs.html)     | Show container logs                   |

#### Documentation

* The **[User Manual](https://fabric8io.github.io/docker-maven-plugin)** has a detailed reference for all and everything.
* The [Introduction](doc/intro.md) is a high level
  overview of this plugin's features and provides an usage example.
  provided goals and possible configuration parameters.
* [Examples](doc/examples.md) are below `samples/` and contain example
  setups which you can use as blueprints for your own projects.
* [ChangeLog](doc/changelog.md) has the release history of this plugin.
* [Contributing](doc/contributing.md) explains how you can contribute to this project. Pull requests are highly appreciated!
  



