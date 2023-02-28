# docker-maven-plugin

[![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.fabric8/docker-maven-plugin/badge.svg?style=flat)](https://maven-badges.herokuapp.com/maven-central/io.fabric8/docker-maven-plugin/)
[![Circle CI](https://circleci.com/gh/fabric8io/docker-maven-plugin/tree/master.svg?style=shield)](https://circleci.com/gh/fabric8io/docker-maven-plugin/tree/master)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=fabric8io_docker-maven-plugin&metric=coverage)](https://sonarcloud.io/summary/new_code?id=fabric8io_docker-maven-plugin)
[![Technical Debt](https://sonarcloud.io/api/project_badges/measure?project=fabric8io_docker-maven-plugin&metric=sqale_index)](https://sonarcloud.io/summary/new_code?id=fabric8io_docker-maven-plugin)

This is a Maven plugin for building Docker images and managing containers for integration tests.
It works with Maven 3.0.5 and Docker 1.6.0 or later.

#### Goals

| Goal                                                                                            | Description                                      | Default Lifecycle Phase |
| ----------------------------------------------------------------------------------------------- | ------------------------------------------------ | ----------------------- |
| [`docker:start`](https://fabric8io.github.io/docker-maven-plugin/#docker:start)                 | Create and start containers                      | pre-integration-test    |
| [`docker:stop`](https://fabric8io.github.io/docker-maven-plugin/#docker:stop)                   | Stop and destroy containers                      | post-integration-test   |
| [`docker:build`](https://fabric8io.github.io/docker-maven-plugin/#docker:build)                 | Build images                                     | install                 |
| [`docker:watch`](https://fabric8io.github.io/docker-maven-plugin/#docker:watch)                 | Watch for doing rebuilds and restarts            |                         |
| [`docker:push`](https://fabric8io.github.io/docker-maven-plugin/#docker:push)                   | Push images to a registry                        | deploy                  |
| [`docker:remove`](https://fabric8io.github.io/docker-maven-plugin/#docker:remove)               | Remove images from local docker host             | post-integration-test   |
| [`docker:logs`](https://fabric8io.github.io/docker-maven-plugin/#docker:logs)                   | Show container logs                              |                         |
| [`docker:source`](https://fabric8io.github.io/docker-maven-plugin/#docker:source)               | Attach docker build archive to Maven project     | package                 |
| [`docker:save`](https://fabric8io.github.io/docker-maven-plugin/#docker:save)                   | Save image to a file                             |                         |
| [`docker:volume-create`](https://fabric8io.github.io/docker-maven-plugin/#docker:volume-create) | Create a volume to share data between containers | pre-integration-test    |
| [`docker:volume-remove`](https://fabric8io.github.io/docker-maven-plugin/#docker:volume-remove) | Remove a created volume                          | post-integration-test   |
| [`docker:copy`](https://fabric8io.github.io/docker-maven-plugin/#docker:copy)                   | Copy files and directories from a container      | post-integration-test   |

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
