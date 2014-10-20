# ChangeLog

## 0.10.x Series

New configuration syntax with support for multiple containers 

* **0.10.2**
  - Support for SSL Authentication with Docker 1.3. Plugin will respect `DOCKER_CERT_PATH` with fallback to `~/.docker/`. 
    The plugin configuration `certPath` can be used, too and has the highest priority.
  - Getting rid of UniRest, using [Apache HttpComponents](http://hc.apache.org/) exclusively for contacting the Docker host.
  - Support for linking of containers (see the configuration in the [shootout-docker-maven](https://github.com/rhuss/shootout-docker-maven/blob/master/pom.xml) POM)
    Images can be specified in any order, the plugin takes care of the right startup order when running containers.
  - Support for waiting on a container's log output before continuing 

Still missing: documentation (coming soon ..)

## 0.9.x Series 

Original configuration syntax (as described in the [README](README.md))

* **0.9.11**
  - Support for SSL Authentication with Docker 1.3. Plugin will respect `DOCKER_CERT_PATH` with fallback to `~/.docker/`. 
    The plugin configuration `certPath` can be used, too and has the highest priority.