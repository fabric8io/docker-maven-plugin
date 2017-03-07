# docker-maven-plugin

[![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.fabric8/docker-maven-plugin/badge.svg?style=flat-square)](https://maven-badges.herokuapp.com/maven-central/io.fabric8/docker-maven-plugin/)
[![Travis](https://secure.travis-ci.org/fabric8io/docker-maven-plugin.png)](http://travis-ci.org/fabric8io/docker-maven-plugin)
[![Circle CI](https://circleci.com/gh/fabric8io/docker-maven-plugin/tree/master.svg?style=shield)](https://circleci.com/gh/fabric8io/docker-maven-plugin/tree/master)
[![Coverage](https://img.shields.io/sonar/https/sonarqube.com/io.fabric8:docker-maven-plugin/coverage.svg)](https://sonarqube.com/overview?id=io.fabric8%3Adocker-maven-plugin)
[![Technical Debt](https://img.shields.io/sonar/https/sonarqube.com/io.fabric8:docker-maven-plugin/tech_debt.svg)](https://sonarqube.com/overview?id=io.fabric8%3Adocker-maven-plugin)
[![Dependency Status](https://www.versioneye.com/java/io.fabric8:docker-maven-plugin/badge?style=flat)](https://www.versioneye.com/java/io.fabric8:docker-maven-plugin/)

This is a Maven plugin for building Docker images and managing containers for integration tests.
It works with Maven 3.0.5 and Docker 1.6.0 or later.

#### Goals

| Goal                                          | Description                           |
| --------------------------------------------- | ------------------------------------- |
| [`docker:start`](https://fabric8io.github.io/docker-maven-plugin/#docker:start)   | Create and start containers           |
| [`docker:stop`](https://fabric8io.github.io/docker-maven-plugin/#docker:stop)     | Stop and destroy containers           |
| [`docker:build`](https://fabric8io.github.io/docker-maven-plugin/#docker:build)   | Build images                          |
| [`docker:watch`](https://fabric8io.github.io/docker-maven-plugin/#docker:watch)   | Watch for doing rebuilds and restarts |
| [`docker:push`](https://fabric8io.github.io/docker-maven-plugin/#docker:push)     | Push images to a registry             |
| [`docker:remove`](https://fabric8io.github.io/docker-maven-plugin/#docker:remove) | Remove images from local docker host  |
| [`docker:logs`](https://fabric8io.github.io/docker-maven-plugin/#docker:logs)     | Show container logs                   |
| [`docker:source`](https://fabric8io.github.io/docker-maven-plugin/#docker:source) | Attach docker build archive to Maven project |
| [`docker:volume-create`](https://fabric8io.github.io/docker-maven-plugin/#docker:volume-create) | Create a volume to share data between containers |
| [`docker:volume-remove`](https://fabric8io.github.io/docker-maven-plugin/#docker:volume-remove) | Remove a created volume |

#### Documentation

* The **[User Manual](https://fabric8io.github.io/docker-maven-plugin)** [[PDF](https://fabric8io.github.io/docker-maven-plugin/docker-maven-plugin.pdf)] has a detailed reference for all and everything.
* The [Introduction](doc/intro.md) is a high level
  overview of this plugin's features and provides an usage example.
  provided goals and possible configuration parameters.
* [Examples](doc/examples.md) are below `samples/` and contain example
  setups which you can use as blueprints for your own projects.
* [ChangeLog](doc/changelog.md) has the release history of this plugin.
* [Contributing](CONTRIBUTING.md) explains how you can contribute to this project. Pull requests are highly appreciated!


#### Docker API Support

* Docker 1.6 (**v1.18**) is the minimal required version
* Docker 1.8.1 (**v1.20**) is required for `docker:watch`
* Docker 1.9 (**v1.21**) is required for using custom networks and build args.
