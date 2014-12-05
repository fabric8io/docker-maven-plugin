# docker-maven-plugin

[![endorse](http://api.coderwall.com/rhuss/endorsecount.png)](http://coderwall.com/rhuss)
[![Build Status](https://secure.travis-ci.org/rhuss/docker-maven-plugin.png)](http://travis-ci.org/rhuss/docker-maven-plugin)
[![Flattr](http://api.flattr.com/button/flattr-badge-large.png)](http://flattr.com/thing/73919/Jolokia-JMX-on-Capsaicin)

This is a Maven plugin for managing Docker images and containers for your builds.
The current version ist **0.10.5** and works with Maven 3.2.1 or later.

#### Goals

| Goal                                          | Description                          |
| --------------------------------------------- | ------------------------------------ |
| [`docker:start`](doc/manual.md#dockerstart)   | Create and start containers          |
| [`docker:stop`](doc/manual.md#dockerstop)     | Stop and destroy containers          |
| [`docker:build`](doc/manual.md#dockerbuild)   | Build images                         |
| [`docker:push`](doc/manual.md#dockerpush)     | Push images to a registry            |
| [`docker:remove`](doc/manual.md#dockerremove) | Remove images from local docker host |
| [`docker:logs`](doc/manual.md#dockerlogs)       | Show container logs                  |

#### Documentation

* The [Introduction](doc/intro.md) is a highlevel
  overview of this plugin's features and provides an usage example.
* The **[User Manual](doc/manual.md)** is a detailed reference for all
  provided goals and possible configuration parameters.
* [Examples](doc/examples.md) are below `samples/` and contain example
  setups which you can use as blueprints for your own projects.
* [Migration Guide](doc/migration-0.9.x.md) for migration from pre-0.10.x versions.
  The old documentation for versions 0.9.x and less can be found [here](doc/readme-0.9.x.md).
* [ChangeLog](doc/changelog.md) has the release history of this plugin.
* [Contributing](doc/contributing.md) explains how you can contribute to this project. Pull requests are highly appreciated!
  



