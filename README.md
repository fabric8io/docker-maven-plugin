# docker-maven-plugin

[![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.fabric8/docker-maven-plugin/badge.svg?style=flat-square)](https://maven-badges.herokuapp.com/maven-central/io.fabric8/docker-maven-plugin/)
[![Travis](https://secure.travis-ci.org/fabric8io/docker-maven-plugin.png)](http://travis-ci.org/fabric8io/docker-maven-plugin)
[![Circle CI](https://circleci.com/gh/fabric8io/docker-maven-plugin/tree/integration.svg?style=shield)](https://circleci.com/gh/fabric8io/docker-maven-plugin/tree/integration)
[![Coverage](https://img.shields.io/sonar/https/nemo.sonarqube.org/io.fabric8:docker-maven-plugin/coverage.svg)](https://nemo.sonarqube.org/overview?id=io.fabric8%3Adocker-maven-plugin)
[![Coverage](https://img.shields.io/sonar/https/nemo.sonarqube.org/io.fabric8:docker-maven-plugin/tech_debt.svg)](https://nemo.sonarqube.org/overview?id=io.fabric8%3Adocker-maven-plugin)

This is a Maven plugin for building Docker images and managing containers for integration tests.
It works with Maven 3.0.5 and Docker 1.6.0 or later.

#### Goals

| Goal                                          | Description                           |
| --------------------------------------------- | ------------------------------------- |
| [`docker:start`](https://fabric8io.github.io/docker-maven-plugin/docker-start.html)   | Create and start containers           |
| [`docker:stop`](https://fabric8io.github.io/docker-maven-plugin/docker-stop.html)     | Stop and destroy containers           |
| [`docker:build`](https://fabric8io.github.io/docker-maven-plugin/docker-build.html)   | Build images                          |
| [`docker:watch`](https://fabric8io.github.io/docker-maven-plugin/docker-watch.html)   | Watch for doing rebuilds and restarts |
| [`docker:push`](https://fabric8io.github.io/docker-maven-plugin/docker-push.html)     | Push images to a registry             |
| [`docker:remove`](https://fabric8io.github.io/docker-maven-plugin/docker-remove.html) | Remove images from local docker host  |
| [`docker:logs`](https://fabric8io.github.io/docker-maven-plugin/docker-logs.html)     | Show container logs                   |
| [`docker:source`](https://fabric8io.github.io/docker-maven-plugin/docker-source.html)   | Attach docker build archive to Maven project |

#### Documentation

* The **[User Manual](https://fabric8io.github.io/docker-maven-plugin)** has a detailed reference for all and everything.
* The [Introduction](doc/intro.md) is a high level
  overview of this plugin's features and provides an usage example.
  provided goals and possible configuration parameters.
* [Examples](doc/examples.md) are below `samples/` and contain example
  setups which you can use as blueprints for your own projects.
* [ChangeLog](doc/changelog.md) has the release history of this plugin.
* [Contributing](doc/contributing.md) explains how you can contribute to this project. Pull requests are highly appreciated!


#### Docker API Support

* Docker 1.6 (**v1.18**) is the minimal required version
* Docker 1.8.1 (**v1.20**) is required for `docker:watch`
* Docker 1.9 (**v1.21**) is required of using custom networks and build args.
