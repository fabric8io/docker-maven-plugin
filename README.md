# docker-maven-plugin

[![endorse](http://api.coderwall.com/rhuss/endorsecount.png)](http://coderwall.com/rhuss)
[![Travis](https://secure.travis-ci.org/rhuss/docker-maven-plugin.png)](http://travis-ci.org/rhuss/docker-maven-plugin)
[![Circle CI](https://circleci.com/gh/rhuss/docker-maven-plugin/tree/integration.svg?style=shield)](https://circleci.com/gh/rhuss/docker-maven-plugin/tree/integration)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/org.jolokia/docker-maven-plugin/badge.svg?style=flat-square)](https://maven-badges.herokuapp.com/maven-central/org.jolokia/docker-maven-plugin/)
 
This is a Maven plugin for managing Docker images and containers for your builds.
It works with Maven 3.2.1 and Docker 1.6.0 or later.

The current Docker API version used is `v1.18` (which is the minimal required API version). If you want to use the 
copy mode for `docker:watch` you need `v1.20` or greater (Docker 1.8.1). See the **[User Manual](https://rhuss.github.io/docker-maven-plugin)** 
for details on how to override this value for new
versions of Docker. For older Docker version please use **0.12.0** with support for `v1.15` 
(Docker 1.3.0) or **0.10.5** which supports `v1.10` as minimal API version.

#### Goals

| Goal                                          | Description                           |
| --------------------------------------------- | ------------------------------------- |
| [`docker:start`](https://rhuss.github.io/docker-maven-plugin/docker-start.html)   | Create and start containers           |
| [`docker:stop`](https://rhuss.github.io/docker-maven-plugin/docker-stop.html)     | Stop and destroy containers           |
| [`docker:build`](https://rhuss.github.io/docker-maven-plugin/docker-build.html)   | Build images                          |
| [`docker:watch`](https://rhuss.github.io/docker-maven-plugin/docker-watch.html)   | Watch for doing rebuilds and restarts |
| [`docker:push`](https://rhuss.github.io/docker-maven-plugin/docker-push.html)     | Push images to a registry             |
| [`docker:remove`](https://rhuss.github.io/docker-maven-plugin/docker-remove.html) | Remove images from local docker host  |
| [`docker:logs`](https://rhuss.github.io/docker-maven-plugin/docker-logs.html)     | Show container logs                   |

#### Documentation

* The **[User Manual](https://rhuss.github.io/docker-maven-plugin)** has a detailed reference for all and everything.
* The [Introduction](doc/intro.md) is a high level
  overview of this plugin's features and provides an usage example.
  provided goals and possible configuration parameters.
* [Examples](doc/examples.md) are below `samples/` and contain example
  setups which you can use as blueprints for your own projects.
* [ChangeLog](doc/changelog.md) has the release history of this plugin.
* [Contributing](doc/contributing.md) explains how you can contribute to this project. Pull requests are highly appreciated!
  



