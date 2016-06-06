# ChangeLog

* **0.15.4** (2016-06-03)
  - Update dependencies: Apache HttpClient 4.5.2, JMockit 1.23, ...
  - Fix read-only bindings ([#462](https://github.com/fabric8io/docker-maven-plugin/issues/462)) 
  - Add 'shmSize' as option to the build config ([#463](https://github.com/fabric8io/docker-maven-plugin/issues/463))
  - Fixed issue with `memory` and `
    
* **0.15.3** (2016-05-27)
  - Add duration information when pulling, building and pushing images ([#313](https://github.com/fabric8io/docker-maven-plugin/issues/313))
  - Fixed logging to always use format strings ([#457](https://github.com/fabric8io/docker-maven-plugin/issues/457))
  - Allow extended image names ([#459](https://github.com/fabric8io/docker-maven-plugin/issues/459))
  
* **0.15.2** (2016-05-19) 
  - More robust response stream parsing ([#436](https://github.com/fabric8io/docker-maven-plugin/issues/436))
  - Add `docker.dockerFileDir` and `docker.dockerFile` to the properties configuration provider. ([#438](https://github.com/fabric8io/docker-maven-plugin/issues/438))
  - Fix splitting of bind volumes for Windows pathes ([#443](https://github.com/fabric8io/docker-maven-plugin/issues/443))
  - Add new build config option `user` for switching the user at the end of the Dockerfile. `docker.user` can be used
    for the properties configuration provider ([#441](https://github.com/fabric8io/docker-maven-plugin/issues/441))
  - Include dot dirs when creating the build tar ([#446](https://github.com/fabric8io/docker-maven-plugin/issues/446))
  - Fix property handler with wait config but empty tcp wait connection ([#451](https://github.com/fabric8io/docker-maven-plugin/issues/451))
  
* **0.15.1** (2016-05-03)
  - Fix push / pull progress bar ([#91](https://github.com/fabric8io/docker-maven-plugin/issues/91))
  - Allow empty environment variable ([#434](https://github.com/fabric8io/docker-maven-plugin/issues/434))
  - Async log request get now their own HTTP client ([#344](https://github.com/fabric8io/docker-maven-plugin/issues/344)) ([#259](https://github.com/fabric8io/docker-maven-plugin/issues/259))
  
* **0.15.0** (2016-04-27)
  - Be more conservative when no "warnings" are returned on create ([#407](https://github.com/fabric8io/docker-maven-plugin/issues/407))
  - Fix parsing of timestamps with numeric timezone ([#410](https://github.com/fabric8io/docker-maven-plugin/issues/410))
  - Validate image names to fit Docker conventions ([#423](https://github.com/fabric8io/docker-maven-plugin/issues/423)) ([#419](https://github.com/fabric8io/docker-maven-plugin/issues/419))
  - Add support for builds args in external Dockerfiles ([#334](https://github.com/fabric8io/docker-maven-plugin/issues/334)) 
  - Move `dockerFileDir` to topLevel `<build>` and introduced `dockerFile` directive
   `build>assembly>dockerFileDir` is now deprecated and will be removed.
  - Add new packaging "docker" (build + run), "docker-build" (build only) and 
    "docker-tar" (creating source)  ([#433](https://github.com/fabric8io/docker-maven-plugin/issues/433))
  - Add `docker:run` as an alias to `docker:start`
  - Expose certain container properties also as Maven properties. By default
    the format is `docker.container.<alias>.ip` for the internal IP address of container with alias `<alias>`.
    ([#198](https://github.com/fabric8io/docker-maven-plugin/issues/198))
  
* **0.14.2**
  - Introduce a mode `try` for `<cleanup>` so that an image gets removed if not being still used. 
    This is the default now, which should be close enough to `true` (except that it won't fail the build
    when the image couldn't be removed) ([#401](https://github.com/fabric8io/docker-maven-plugin/issues/401))

* **0.14.1**
  - First (test) release performed with a fabric8 CD pipeline. No new features.
  
* **0.14.0**
  - Add support for Docker network and `host`, `bridge` and `container` network modes ([#335](https://github.com/fabric8io/docker-maven-plugin/issues/335))
  - Add support for older Maven versions, minimum required version is now 3.0.5 ([#290](https://github.com/fabric8io/docker-maven-plugin/issues/290))
  - Update to maven-assembly-plugin 2.6 which fixes issue with line endings on windows ([#127](https://github.com/fabric8io/docker-maven-plugin/issues/127))
  - Disabled color output on Windows because ANSI emulation can't be enabled in Maven's sl4j logger which 
    caches system out/err
  - Moved to to [fabric8io](https://github.com/orgs/fabric8io/dashboard) as GitHub organization which implies
    also changes in the maven coordinates (Maven group-id is now **io.fabric8**)
  - Fix wait section in samples ([#385](https://github.com/fabric8io/docker-maven-plugin/issues/385))
  - Add logging configuration to property handler
  - Add support for a logging driver ([#379](https://github.com/fabric8io/docker-maven-plugin/issues/379))
  
With version `0.14.0` this plugin moved to the [fabric8](http://fabric8.io) community in order to provide
even better services. This include a change in the Maven coordinates. I.e. the Maven group id is now **io.fabric8** 
(formerly: "org.jolokia"). Please adapt your pom files accordingly.

* **0.13.9**
  - Check also registry stored with an `https` prefix ([#367](https://github.com/fabric8io/docker-maven-plugin/issues/367))
  - Don't stop containers not started by the project during parallel reactor builds ([#372](https://github.com/fabric8io/docker-maven-plugin/issues/372))

* **0.13.8**
  - Add option `nocache` to build configuration ([#348](https://github.com/fabric8io/docker-maven-plugin/issues/348))
  - Add system property `docker.nocache` to disable build caching globally ([#349](https://github.com/fabric8io/docker-maven-plugin/issues/349))
  - Add support for '.maven-dockerignore' for excluding certain files in plain Dockerfile build ([#362](https://github.com/fabric8io/docker-maven-plugin/issues/362))
  - If naming strategy is "alias" stop only the container with the given alias with `docker:stop` ([#359](https://github.com/fabric8io/docker-maven-plugin/issues/359))
  - Fix that containers without d-m-p label where still stopped 
  - Add support for OpenShift login (use `-DuseOpenShiftAuth` for enabling this) ([#350](https://github.com/fabric8io/docker-maven-plugin/issues/350))
  - Add support for dedicated pull and push registry configuration respectively ([#351](https://github.com/fabric8io/docker-maven-plugin/issues/351))
  
* **0.13.7**
  - Fix default for "cleanup" in build configuration to `true` (as documented) ([#338](https://github.com/fabric8io/docker-maven-plugin/issues/338))
  - Fix dynamic host property update in port mapping ([#323](https://github.com/fabric8io/docker-maven-plugin/issues/323))  
  - New goal 'docker:source' for attaching a Docker tar archive to the Maven project with an classifier "docker-<alias>" ([#311](https://github.com/fabric8io/docker-maven-plugin/issues/311)) 
  - Be more careful with chowning the user when <user> is used in an assembly ([#336](https://github.com/fabric8io/docker-maven-plugin/issues/336))
  - Move VOLUME to the end of the Dockerfile to allow initialization via RUN commands ([#341](https://github.com/fabric8io/docker-maven-plugin/issues/341))
  - Allow multiple configurations with different Docker hosts again ([#320](https://github.com/fabric8io/docker-maven-plugin/issues/320)) 
  - `docker:start` blocks now only when system property docker.follow is given ([#249](https://github.com/fabric8io/docker-maven-plugin/issues/249)) 
  - `docker:stop` only stops containers started by this plugin by default ([#87](https://github.com/fabric8io/docker-maven-plugin/issues/87))
  - Lookup `~/.docker/config.json` for registry credentials as fallback ([#147](https://github.com/fabric8io/docker-maven-plugin/issues/147))

* **0.13.6**
  - Don't use user from image when pulling base images ([#147](https://github.com/fabric8io/docker-maven-plugin/issues/147))
  - Add a new assembly descriptor reference  `hawt-app` for using assemblies created by 
    [hawt-app](https://github.com/fabric8io/fabric8/tree/master/hawt-app-maven-plugin)
  
* **0.13.5**
  - Improvements for `docker:watch` ([#288](https://github.com/fabric8io/docker-maven-plugin/issues/288))
  - Add parameter `kill` to `<watch>` configuration for waiting before
    sending SIGKILL when stopping containers ([#293](https://github.com/fabric8io/docker-maven-plugin/issues/293))
  - Add `file` for `<log>` to store the logout put in a file. Use
    `docker.logStdout` to show logs nevertheless to stdout ([#287](https://github.com/fabric8io/docker-maven-plugin/issues/287))
  - Support `watchMode == copy` for copying changed assembly files
    into a running container ([#268](https://github.com/fabric8io/docker-maven-plugin/issues/268))
  - Add a `target/classpath` file to the assembly as `classpath` for 
    `artifact-with-dependencies` predefined assembly descriptor ([#283](https://github.com/fabric8io/docker-maven-plugin/issues/283))
  - Disable Apache HTTP Client retry in WaitUtil ([#297](https://github.com/fabric8io/docker-maven-plugin/issues/297))
  
* **0.13.4**
  - Support explicit exec arguments for `start.cmd` and
    `start.entrypoint`. ([#253](https://github.com/fabric8io/docker-maven-plugin/issues/253))
  - Fix processing of split chunked JSON responses
    ([#259](https://github.com/fabric8io/docker-maven-plugin/issues/259))
  - Fix for default registry handling. Again and
    again. ([#261](https://github.com/fabric8io/docker-maven-plugin/issues/261))
  - Allow `runCmds` to be compressed into a single command with the
    build config option
    `optimise`. ([#263](https://github.com/fabric8io/docker-maven-plugin/issues/263))
  - Proper error message when default timeout is hit while waiting
    ([#274](https://github.com/fabric8io/docker-maven-plugin/issues/274))
  - Add proper error message when docker host URL is malformed
    ([#277](https://github.com/fabric8io/docker-maven-plugin/issues/277))
  - If no wait condition is given in wait continue immediately
    ([#276](https://github.com/fabric8io/docker-maven-plugin/issues/276))
  - Add logic to specify exec commands during postStart and preStop
    ([#272](https://github.com/fabric8io/docker-maven-plugin/issues/272))
  - Fixed docker:watch bug when watching on plain files
  
* **0.13.3**
  - Allow dangling images to be cleaned up after build
    ([#20](https://github.com/fabric8io/docker-maven-plugin/issues/20))
  - Adapt order of WORKDIR and RUN when building images
    ([#222](https://github.com/fabric8io/docker-maven-plugin/issues/222))
  - Allow 'build' and/or 'run' configuration to be skipped
    ([#207](https://github.com/fabric8io/docker-maven-plugin/issues/207))
  - Refactored to use 'inspect' instead of 'list' for checking the
    existence of an image
    ([#230](https://github.com/fabric8io/docker-maven-plugin/issues/230))
  - Refactored ApacheHttpClientDelegate to avoid leaking connections
    ([#232](https://github.com/fabric8io/docker-maven-plugin/issues/232))
  - Allow empty `build` or `assembly` elements
    ([#214](https://github.com/fabric8io/docker-maven-plugin/issues/214))
    ([#236](https://github.com/fabric8io/docker-maven-plugin/issues/236))
  - Add new configuration parameter 'maxConnections' to allow to
    specify the number of parallel connections to the Docker
    Host. Default: 100
    ([#254](https://github.com/fabric8io/docker-maven-plugin/issues/254))
  - Allow multiple containers of the same image to be linked
    ([#182](https://github.com/fabric8io/docker-maven-plugin/issues/182))
  - HTTP method and status code can be specified when waiting on an
    HTTP URL
    ([#258](https://github.com/fabric8io/docker-maven-plugin/issues/258))
  - Introduced global `portPropertyFile` setting
    ([#90](https://github.com/fabric8io/docker-maven-plugin/issues/90))
  - Allow the container's host ip to be bound to a maven property and
    exported

* **0.13.2**
  - "run" directives can be added to the Dockerfile
    ([#191](https://github.com/fabric8io/docker-maven-plugin/issues/191))
  - Support user information in wait URL
    ([#211](https://github.com/fabric8io/docker-maven-plugin/issues/211))
  - Stop started container in case of an error during startup
    ([#217](https://github.com/fabric8io/docker-maven-plugin/issues/217))
  - Allow linking to external containers
    ([#195](https://github.com/fabric8io/docker-maven-plugin/issues/195))
  - Allow volume mounting from external containers
    ([#73](https://github.com/fabric8io/docker-maven-plugin/issues/73))
  
* **0.13.1**
  - Allow autoPull to be forced on docker:build and docker:start
    ([#96](https://github.com/fabric8io/docker-maven-plugin/issues/96))
  - Respect username when looking up credentials for a Docker registry
    ([#174](https://github.com/fabric8io/docker-maven-plugin/issues/174))
  - Add "force=1" to push for Fedora/CentOs images allowing to push to
    docker hub
  
Note that the default registry has been changed to `docker.io` as
docker hub doesn't use `registry.hub.docker.com` as the default
registry and refused to authenticate against this registry. For
backward compatibility reasons `registry.hub.docker.com`,
`index.docker.io` and `docker.io` can be used as a server id in
`~/.m2/settings.xml` for the default credentials for pushing without
registry to Docker hub.

* **0.13.0**
  - Add `docker:watch`
    ([#187](https://github.com/fabric8io/docker-maven-plugin/issues/187))
  - Allow `extraHosts` IPs to be resolved at runtime
    ([#196](https://github.com/fabric8io/docker-maven-plugin/issues/196))
  - Add `workDir` as configuration option to `<build>`
    ([#204](https://github.com/fabric8io/docker-maven-plugin/issues/204))
  - Fix problem with log output and wait
    ([#200](https://github.com/fabric8io/docker-maven-plugin/issues/200))
  - Don't verify SSL server certificates if `DOCKER_TLS_VERIFY` is not
    set
    ([#192](https://github.com/fabric8io/docker-maven-plugin/issues/192))
  - For bind path on Windows machines
    ([#188](https://github.com/fabric8io/docker-maven-plugin/issues/188))
  - No 'from' required when using a Dockerfile
    ([#201](https://github.com/fabric8io/docker-maven-plugin/issues/201))
  - Support for LABEL for build and run.

Note that since version 0.13.0 this plugin requires Docker API version v1.17 or later in order to support labels.  
 
The watch feature has changed: Instead of using paramters like
`docker.watch` or `docker.watch.interval` for `docker:start` a
dedicated `docker:watch` has been introduced. Also the
`<run><watch>...</watch></run>` configuration has been moved one level
up so that `<watch>` and `<run>` are on the same level. Please refer
to the [manual](manual.md#watching-for-image-changes) for an in depth
explanation of the much enhanced watch functionality.
  
* **0.12.0**
  - Allow CMD and ENTRYPOINT with shell and exec arguments
    ([#130](https://github.com/fabric8io/docker-maven-plugin/issues/130))
    ([#149](https://github.com/fabric8io/docker-maven-plugin/issues/149))
  - Unix Socket support
    ([#179](https://github.com/fabric8io/docker-maven-plugin/issues/179))
  - Add a new parameter 'skipTags' for avoiding configured tagging of
    images
    ([#145](https://github.com/fabric8io/docker-maven-plugin/issues/145))
  - Break build if log check or URL check runs into a timeout
    ([#173](https://github.com/fabric8io/docker-maven-plugin/issues/173))
  
Please note that for consistencies sake `<command>` has been renamed
to `<cmd>` which contains inner elements to match better the
equivalent Dockerfile argument. The update should be trivial and easy
to spot since a build will croak immediately.

The old format

````xml 
<build> 
  <command>java -jar /server.jar</command>
</build>
````

becomes now

````xml 
<build> 
  <cmd> 
    <exec> 
      <arg>java</arg> 
      <arg>-jar</arg>
      <arg>/server.jar</arg>
    </exec>         
  </cmd> 
</build> 
````
 
or

````xml 
<build>
  <cmd>
    <shell>java -jar /server.jar</shell>
  </cmd>
</build>
````

depending on whether you prefer the `exec` or `shell` form.

* **0.11.5**
  - Fix problem with http:// URLs when a CERT path is set
  - Fix warnings when parsing a pull response
  - Add a new parameter 'docker.follow' which makes a `docker:start`
    blocking until the CTRL-C is pressed
    ([#176](https://github.com/fabric8io/docker-maven-plugin/issues/176))
  - Add a `user` parameter to the assembly configuration so that the
    added files are created for this user
  - Fix problem when creating intermediate archive for collecting
    assembly files introduced with #139. The container can be now set
    with "mode" in the assembly configuration with the possible values
    `dir`, `tar`, `tgz` and `zip`
    ([#171](https://github.com/fabric8io/docker-maven-plugin/issues/171))
  - Workaround Docker problem when using an implicit registry
    `index.docker.io` when no registry is explicitly given.
  - Fixed references to docker hub in documentation
    ([#169](https://github.com/fabric8io/docker-maven-plugin/issues/169))
  - Fixed registry authentication lookup
    ([#146](https://github.com/fabric8io/docker-maven-plugin/issues/146))
    
* **0.11.4**
  - Fixed documentation for available properties
  - Changed property `docker.assembly.exportBase` to
    `docker.assembly.exportBaseDir`
    ([#164](https://github.com/fabric8io/docker-maven-plugin/issues/164))
  - Changed default behaviour of `exportBaseDir` (true if no base
    image used with `from`, false otherwise)
  - Fix log messages getting cut off in the build
    ([#163](https://github.com/fabric8io/docker-maven-plugin/issues/163))
  - Allow system properties to overwrite dynamic port mapping
    ([#161](https://github.com/fabric8io/docker-maven-plugin/issues/161))
  - Fix for empty authentication when pushing to registries
    ([#102](https://github.com/fabric8io/docker-maven-plugin/issues/102))
  - Added watch mode for images with `-Ddocker.watch`
    ([#141](https://github.com/fabric8io/docker-maven-plugin/issues/141))
  - Added support for inline assemblies (#157, #158)
  - Add support for variable substitution is environment declarations
    ([#137](https://github.com/fabric8io/docker-maven-plugin/issues/137))
  - Use Tar archive as intermediate container when creating image ([#139](https://github.com/fabric8io/docker-maven-plugin/issues/139))  
  - Better error handling for Docker errors wrapped in JSON response
    only
    ([#167](https://github.com/fabric8io/docker-maven-plugin/issues/167))
  
* **0.11.3**
  - Add support for removeVolumes in `docker:stop` configuration
    ([#120](https://github.com/fabric8io/docker-maven-plugin/issues/120))
  - Add support for setting a custom maintainer in images
    ([#117](https://github.com/fabric8io/docker-maven-plugin/issues/117))
  - Allow containers to be named using
    `<namingStrategy>alias</namingStrategy>` when started
    ([#48](https://github.com/fabric8io/docker-maven-plugin/issues/48))
  - Add new global property 'docker.verbose' for switching verbose
    image build output
    ([#36](https://github.com/fabric8io/docker-maven-plugin/issues/36))
  - Add support for environment variables specified in a property file
    ([#128](https://github.com/fabric8io/docker-maven-plugin/issues/128))
  - Documentation improvements (#107, #121)
  - Allow to use a dockerFileDir without any assembly
  
* **0.11.2**
  - Fix maven parse error when specifying restart policy
    ([#99](https://github.com/fabric8io/docker-maven-plugin/issues/99))
  - Allow host names to be used in port bindings
    ([#101](https://github.com/fabric8io/docker-maven-plugin/issues/101))
  - Add support for tagging at build and push time
    ([#104](https://github.com/fabric8io/docker-maven-plugin/issues/104))
  - Use correct output dir during multi-project builds
    ([#97](https://github.com/fabric8io/docker-maven-plugin/issues/97))
  - `descriptor` and `descriptorRef` in the assembly configuration are
    now optional
    ([#66](https://github.com/fabric8io/docker-maven-plugin/issues/66))
  - Fix NPE when filtering enabled during assembly creation
    ([#82](https://github.com/fabric8io/docker-maven-plugin/issues/82))
  - Allow `${project.build.finalName}` to be overridden when using a
    pre-packaged assembly descriptor for artifacts
    ([#111](https://github.com/fabric8io/docker-maven-plugin/issues/111))

* **0.11.1**
  - Add support for binding UDP ports
    ([#83](https://github.com/fabric8io/docker-maven-plugin/issues/83))
  - "Entrypoint" supports now arguments
    ([#84](https://github.com/fabric8io/docker-maven-plugin/issues/84))
  - Fix basedir for multi module projects
    ([#89](https://github.com/fabric8io/docker-maven-plugin/issues/89))
  - Pull base images before building when "autoPull" is switched on
    (#76, #77, #88)
  - Fix for stopping containers without tag
    ([#86](https://github.com/fabric8io/docker-maven-plugin/issues/86))
  
* **0.11.0**
  - Add support for binding/exporting containers during startup
    ([#55](https://github.com/fabric8io/docker-maven-plugin/issues/55))
  - Provide better control of the build assembly configuration. In
    addition, the plugin will now search for assembly descriptors in
    `src/main/docker`. This default can be overridden via the global
    configuration option `sourceDirectory`.
  - An external `Dockerfile` can now be specified to build an image.
  - When "creating" containers they get now all host configuration
    instead of during "start". This is the default behaviour since
    v1.15 while the older variant where the host configuration is fed
    into the "start" call is deprecated and will go away.
  - Allow selecting the API version with the configuration
    "apiVersion".  Default and minimum API version is now "v1.15"
  - A registry can be specified as system property `docker.registry`
    or environment variable `DOCKER_REGISTRY`
    ([#26](https://github.com/fabric8io/docker-maven-plugin/issues/26))
  - Add new wait parameter `shutdown` which allows to specify the
    amount of time to wait between stopping a container and removing
    it ([#54](https://github.com/fabric8io/docker-maven-plugin/issues/54))

Please note, that the syntax for binding volumes from another
container has changed slightly in 0.10.6.  See
"[Volume binding](manual.md#volume-binding)" for details but in short:

````xml 
<run>
  <volumes>
    <from>data</from>
    <from>fabric8/demo</from>
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
      <image>fabric8/demo</image>
    </from>
  </volumes>
....
</run>
````

The syntax for specifying the build assembly configuration has also
changed. See "[Build Assembly] (manual.md#build-assembly)" for details
but in short:

````xml
<build>
  ...
  <exportDir>/export</exportDir>
  <assemblyDescriptor>src/main/docker/assembly.xml</assemblyDescriptor>  
</build>  
````

becomes

````xml
<build>
  ...  
  <assembly>
    <basedir>/export</basedir>
    <descriptor>assembly.xml</descriptor> 
  </assembly>
</build>           
````

* **0.10.5**
  - Add hooks for external configurations
  - Add property based configuration for images
    ([#42](https://github.com/fabric8io/docker-maven-plugin/issues/42))
  - Add new goal `docker:logs` for showing logs of configured
    containers
    ([#49](https://github.com/fabric8io/docker-maven-plugin/issues/49))
  - Support for showing logs during `docker:start`
    ([#8](https://github.com/fabric8io/docker-maven-plugin/issues/8))
  - Use `COPY` instead of `ADD` when putting a Maven assembly into the
    container
    ([#53](https://github.com/fabric8io/docker-maven-plugin/issues/53))
  - If `exportDir` is `/` then do not actually export (since it
    doesn't make much sense) (see #62)

* **0.10.4**
  - Restructured and updated documentation
  - Fixed push issue when using a private registry
    ([#40](https://github.com/fabric8io/docker-maven-plugin/issues/40))
  - Add support for binding to an arbitrary host IP
    ([#39](https://github.com/fabric8io/docker-maven-plugin/issues/39))

* **0.10.3**
  - Added "remove" goal for cleaning up images
  - Allow "stop" also as standalone goal for stopping all managed
    builds

* **0.10.2**
  - Support for SSL Authentication with Docker 1.3. Plugin will
    respect `DOCKER_CERT_PATH` with fallback to `~/.docker/`.  The
    plugin configuration `certPath` can be used, too and has the
    highest priority.
  - Getting rid of UniRest, using
    [Apache HttpComponents](http://hc.apache.org/) exclusively for
    contacting the Docker host.
  - Support for linking of containers (see the configuration in the
    [shootout-docker-maven](https://github.com/fabric8io/shootout-docker-maven/blob/master/pom.xml)
    POM) Images can be specified in any order, the plugin takes care
    of the right startup order when running containers.
  - Support for waiting on a container's log output before continuing

## 0.9.x Series

Original configuration syntax (as described in the old
[README](readme-0.9.x.md))

* **0.9.12**
  - Fixed push issue when using a private registry
    ([#40](https://github.com/fabric8io/docker-maven-plugin/issues/40))

* **0.9.11**
  - Support for SSL Authentication with Docker 1.3. Plugin will
    respect `DOCKER_CERT_PATH` with fallback to `~/.docker/`.  The
    plugin configuration `certPath` can be used, too and has the
    highest priority.
