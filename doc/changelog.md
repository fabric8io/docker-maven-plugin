# ChangeLog

## 0.10.x Series

New configuration syntax with support for multiple containers 

* **0.10.4**
  - Restructured and updated documentation
  - Fixed push issue when using a private registry (#40)
  - Add support for binding to an arbitrary host IP (#39)

* **0.10.3**
  - Added "remove" goal for cleaning up images
  - Allow "stop" also as standalone goal for stopping all managed builds

* **0.10.2**
  - Support for SSL Authentication with Docker 1.3. Plugin will respect `DOCKER_CERT_PATH` with fallback to `~/.docker/`. 
    The plugin configuration `certPath` can be used, too and has the highest priority.
  - Getting rid of UniRest, using [Apache HttpComponents](http://hc.apache.org/) exclusively for contacting the Docker host.
  - Support for linking of containers (see the configuration in the [shootout-docker-maven](https://github.com/rhuss/shootout-docker-maven/blob/master/pom.xml) POM)
    Images can be specified in any order, the plugin takes care of the right startup order when running containers.
  - Support for waiting on a container's log output before continuing 

## 0.9.x Series 

Original configuration syntax (as described in the old [README](readme-0.9.x.md))

* **0.9.12**
  - Fixed push issue when using a private registry (#40)

* **0.9.11**
  - Support for SSL Authentication with Docker 1.3. Plugin will respect `DOCKER_CERT_PATH` with fallback to `~/.docker/`. 
    The plugin configuration `certPath` can be used, too and has the highest priority.
