
# docker-maven-plugin

Maven plugin for managing Docker images and containers.

This project is currently not much more than a prototype. The ultimative target will be
to have a maven plugin which plays nicely with Cargo for easy setup of various containers
used for integrtion testing.

Please stay tuned ....

## `docker:start`

Creates and starts a docker container

### Configuration

 * **url** URL to the docker daemon (default: `http://localhost:4242`)
 * **image** Name of the docker image to use (e.g. `jolokia/tomcat:7.0.52`). Required
 * **ports** List of ports to be mapped statically or dynamically. See below for an example.

 * **autoPull** Set to `true` if an unknown image should be automatically pulled
 * **command** Command to execute in the docker container
 * **portPropertyFile** Path to a file where dynamically mapped ports are written to
 * **wait** Ramp up time in milliseconds
 * **color** Set to `true` for colored output

## `docker:stop`

Stops a docker container,

### Configuration

 * **url** URL to the docker daemon (default: `http://localhost:4242`)
 * **color** Set to `true` for colored output
 * **keepContainer** Set to `true` for not automatically removing the container after stopping it.

# Links

* [Script](https://gist.github.com/deinspanjer/9215467) for setting up NAT forwarding rules when using [boot2docker](https://github.com/boot2docker/boot2docker)
on OS X

* Use `maven-failsafe-plugin` for integration tests in order to stop the docker container even when the tests are failing.