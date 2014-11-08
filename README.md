# docker-maven-plugin

[![endorse](http://api.coderwall.com/rhuss/endorsecount.png)](http://coderwall.com/rhuss)
[![Build Status](https://secure.travis-ci.org/rhuss/docker-maven-plugin.png)](http://travis-ci.org/rhuss/docker-maven-plugin)
[![Flattr](http://api.flattr.com/button/flattr-badge-large.png)](http://flattr.com/thing/73919/Jolokia-JMX-on-Capsaicin)

This is a Maven plugin for managing Docker images and containers from your builds.

> *This document describes the configuration syntax for version >=
> 0.10.0. For older version (i.e. 0.9.x) please refer to the old
> [documentation](doc/readme-0.9.x.md). Migration to the new syntax is not
> difficult and described [separately](doc/upgrade-from-0.9.x.md)*

This plugin provides the following goals:

| Goal                                          | Description                          |
| --------------------------------------------- | ------------------------------------ |
| [`docker:start`](doc/manual.md#dockerstart)   | Create and start containers          |
| [`docker:stop`](doc/manual.md#dockerstop)     | Stop and destroy containers          |
| [`docker:build`](doc/manual.md#dockerbuild)   | Build images                         |
| [`docker:push`](doc/manual.md#dockerpush)     | Push images to a registry            |
| [`docker:remove`](doc/manual.md#dockerremove) | Remove images from local docker host |

The only requirement is a remote access to a Docker daemon (no CLI
installation is required).

* [Introduction](doc/intro.md) 
  The introduction has a highlevel overview of the functionality of
  this plugin and gives an example of its usage. The motivation and
  design goals are given there, too
* [User Manual](doc/manual.md)
  The user manual is a detailed reference for all provided goals and
  the configuration parameters. 
* [Examples](doc/examples.md)
  The examples below `samples/` contain example setup which you can
  use as blueprints for your own projects.
* [Changes] (doc/changelog.md)
  The Changelog contains the history for this plugin.
  



