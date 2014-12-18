# ChangeLog

* **0.10.6**
  - Add support for binding/exporting containers during startup (#55)
  - When "creating" containers they get now all host configuration instead of during "start". This is
    the default behaviour since v1.15 while the older variant where the host configuration is feed into
    the "start" call is deprecated and will go away.
  - Allow selecting the API version with the configuration "apiVersion".
    Default and minimum API version is now "v1.15"
  - A registry can be specified as system property `docker.registry` or
    environment variable `DOCKER_REGISTRY` (#26)

Please note, that the syntax for binding volumes from another container has changed slightly in 0.10.6.
See "[Volume binding]"(manual.md#volume-binding) for details but in short:

````xml
<run>
  <volumes>
    <from>data</from>
    <from>jolokia/demo</from>
  </volumes>
  ....
</run>
````

becomes

````xml
<run>
  <volumes>
    <from>
      <image>data</image>
      <image>jolokia/demo</image>
    </from>
  </volumes>
  ....
</run>
````

* **0.10.5**
  - Add hooks for external configurations
  - Add property based configuration for images (#42)
  - Add new goal `docker:logs` for showing logs of configured containers (#49)
  - Support for showing logs during `docker:start` (#8)
  - Use `COPY` instead of `ADD` when putting a Maven assembly into the container (#53)
  - If `exportDir` is `/` then do not actually export (since it doesn't make much sense) (see #62)

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
