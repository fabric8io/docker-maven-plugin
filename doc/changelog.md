# ChangeLog

* **0.31.0** (2019-08-10)
  - Fix test cases on Windows ([#1220](https://github.com/fabric8io/docker-maven-plugin/issues/1220))
  - ECR credentials from IAM Task role for ECS Fargate deployment ([#1233](https://github.com/fabric8io/docker-maven-plugin/issues/1233))
  - Fix bug in properties names extracted from docker config json file ([#1237](https://github.com/fabric8io/docker-maven-plugin/issues/1237))
  - Fix that portPropertyFile is not written anymore ([#1112](https://github.com/fabric8io/docker-maven-plugin/issues/1112))
  - Use identity token if found in Docker config.json ([#1249](https://github.com/fabric8io/docker-maven-plugin/issues/1249))
  - Allow also starting with an environment variable in `targetDir` of an aseembly config instead of insisting on only absolute path-names ([#1244](https://github.com/fabric8io/docker-maven-plugin/issues/1244))
  - Support for pattern matching in `docker:stop` and `docker:remove` ([#1215](https://github.com/fabric8io/docker-maven-plugin/issues/1215))
  - Increase interoperability with docker-java by accepting `registry.username` and `registry.password`, too ([#1245](https://github.com/fabric8io/docker-maven-plugin/issues/1245))

>>>>>>> preps for 0.31.0
* **0.30.0** (2019-04-21)
  - Restore ANSI color to Maven logging if disabled during plugin execution and enable color for Windows with Maven 3.5.0 or later. Color logging is enabled by default, but disabled if the Maven CLI disables color (e.g. in batch mode) ([#1108](https://github.com/fabric8io/docker-maven-plugin/issues/1108))
  - Fix NPE if docker:save is called with -Dfile=file-name-only.tar ([#1203](https://github.com/fabric8io/docker-maven-plugin/issues/1203))
  - Fix NPE in BuildImageConfiguration ([#1200](https://github.com/fabric8io/docker-maven-plugin/issues/1200))
  - Improve GZIP compression performance for docker:save ([#1205](https://github.com/fabric8io/docker-maven-plugin/issues/1205))
  - Allow docker:save to attach image archive as a project artifact ([#1210](https://github.com/fabric8io/docker-maven-plugin/pull/1210))
  - Use pattern to detect image name in archive loaded during build and tag with image name from the project configuration ([#1207](https://github.com/fabric8io/docker-maven-plugin/issues/1207))
  - Add 'cacheFrom' option to specify images to use as cache sources ([#1132](https://github.com/fabric8io/docker-maven-plugin/issues/1132))

Renamed "nocache" to "noCache" for consistencies reason. "nocache" is still supported but deprecated and will be removed in a future version. Same is true for the global system property "docker.nocache" which is renamed to "docker.noCache" 

* **0.29.0** (2019-04-08)
  - Avoid failing docker:save when no images with build configuration are present ([#1185](https://github.com/fabric8io/docker-maven-plugin/issues/1185))
  - Reintroduce minimal API-VERSION parameter in order to support docker versions below apiVersion 1.25
  - docs: Correct default image naming
  - Proxy settings are being ignored ([#1148](https://github.com/fabric8io/docker-maven-plugin/issues/1148))
  - close api version http connection ([#1152](https://github.com/fabric8io/docker-maven-plugin/issues/1152))
  - Log more information when verbose=true  ([#917](https://github.com/fabric8io/docker-maven-plugin/issues/917))
  - Obtain container ip address from custom network for tcp/http wait
  - Fix http (SSL) ping with 'allowAllHosts' flag enabled
  - Update to jnr-unixsocket 0.22
  - Enhance @sha256 digest for tags in FROM (image_name:image_tag@sha256<digest>) ([#541](https://github.com/fabric8io/docker-maven-plugin/issues/541))
  - Support docker SHELL setting for runCmds ([#1157](https://github.com/fabric8io/docker-maven-plugin/issues/1157))
  - Added 'autoRemove' option for running containers ([#1179](https://github.com/fabric8io/docker-maven-plugin/issues/1179))
  - Added support for AWS EC2 instance roles when pushing to AWS ECR ([#1186](https://github.com/fabric8io/docker-maven-plugin/issues/1186))
  - Introduce `contextDir` configuration option which would be used to specify docker build context ([#1189](https://github.com/fabric8io/docker-maven-plugin/issues/1189))
  - Add support for auto-pulling multiple base image for multi stage builds ([#1057](https://github.com/fabric8io/docker-maven-plugin/issues/1057))
  - Fix usage of credential helper that do not support 'version' command ([#1159](https://github.com/fabric8io/docker-maven-plugin/issues/1159))

Please note that `dockerFileDir` is now deprecated in favor of `contextDir` which also allows absolute paths to Dockerfile with 
`dockerFile` and it will be removed in 1.0.0. It's still supported in this release but users are suggested to migrate to 
`contextDir` instead.
  
* **0.28.0** (2018-12-13)
  - Update to JMockit 1.43
  - Compiles with Java 11
  - Update to jnr-unixsocket version to 0.21 ([#1089](https://github.com/fabric8io/docker-maven-plugin/issues/1089))
  - Add 'readOnly' option for docker:run cto mount container's root fs read-only ([#1125](https://github.com/fabric8io/docker-maven-plugin/issues/1125))
  - Provide container properties to the wait configuration execution ([#1111](https://github.com/fabric8io/docker-maven-plugin/issues/1111))
  - Allow @sha256 digest for tags in FROM ([#541](https://github.com/fabric8io/docker-maven-plugin/issues/541))

* **0.27.2** (2018-10-05)
  - Fix NPE regression related to volumes (again) ([#1091](https://github.com/fabric8io/docker-maven-plugin/issues/1091))
  - Fix NPE when stopping containers with autoCreateCustomNetworks ([#1097](https://github.com/fabric8io/docker-maven-plugin/issues/1097))
  - Smarter API version handling ([#1060](https://github.com/fabric8io/docker-maven-plugin/issues/1060))
  - Fix regression when calling the credential helper for authentication, leading to an exception because of the usage of an already shutdown executor service ([#1098](https://github.com/fabric8io/docker-maven-plugin/issues/1098))
  - Add support for CPU configurations with compose ([#1102](https://github.com/fabric8io/docker-maven-plugin/issues/1102))

* **0.27.1** (2018-09-28)
  - Fix NPE when no volume configuration is present ([#1091](https://github.com/fabric8io/docker-maven-plugin/issues/1091))
  - Allow credentialhelper look up the registry without scheme prefix ([#1068](https://github.com/fabric8io/docker-maven-plugin/issues/1068))

* **0.27.0** (2018-09-26)
  - Jump to Java 8 as minimal Java version
  - Fix NPE in docker:remove-volumes when no volume configuration is given ([#1086](https://github.com/fabric8io/docker-maven-plugin/issues/1086))
  - Fix NPE when no networks are configured ([#1055](https://github.com/fabric8io/docker-maven-plugin/issues/1055))
  - Fix Base64 encoding for X-Registry-Auth used for Docker authentication ([#1084](https://github.com/fabric8io/docker-maven-plugin/issues/1084))
  - Fix property configuration based's build detection ([#1078](https://github.com/fabric8io/docker-maven-plugin/issues/1078))
  - Introduce container name patterns for naming containers ([#931](https://github.com/fabric8io/docker-maven-plugin/issues/931))
  - Respect environment variables DOCKER_CONFIG, KUBECONFIG for looking up credentials ([#1083](https://github.com/fabric8io/docker-maven-plugin/issues/1083))
  - Change from org.json with Gson for less restrictive licensing ([#1016](https://github.com/fabric8io/docker-maven-plugin/issues/1016)) ([#1064](https://github.com/fabric8io/docker-maven-plugin/issues/1064))
  - Fix missing actions in a watch restart ([#1070](https://github.com/fabric8io/docker-maven-plugin/issues/1070))
  - Fix for creating volumes with proper configuration during "docker:start" ([#986](https://github.com/fabric8io/docker-maven-plugin/issues/986))
  - Fix logging failure on Windows ([#873](https://github.com/fabric8io/docker-maven-plugin/issues/873))

* **0.26.1** (2018-07-20)
  - Simple Dockerfile triggered also when only a single run section is given
  - Sample added for how to use run-java-sh in simple dockerfile mode
  - Allow both cred helpers and auth in Docker config ([#1041](https://github.com/fabric8io/docker-maven-plugin/issues/1041))

* **0.26.0** (2018-05-16)
  - Always create missing target directory for docker:save ([#1013](https://github.com/fabric8io/docker-maven-plugin/issues/1013))
  - d-m-p plugins for adding extra files introduced. See documentation for more information.
  - Update assembly plugin to 3.1.0 ([#1021](https://github.com/fabric8io/docker-maven-plugin/issues/1021))
  - Add option for regenerating certificates after starting Docker Machine ([#1019](https://github.com/fabric8io/docker-maven-plugin/issues/1019))
  - Add `startPeriod` to `healthCheck` ([#961](https://github.com/fabric8io/docker-maven-plugin/issues/961))
  - Unbreak setting of entrypoint in `exec` form when property mode is enabled ([#1020](https://github.com/fabric8io/docker-maven-plugin/issues/1020))
  - Fix enabling of log configuration ([#1010](https://github.com/fabric8io/docker-maven-plugin/issues/1010))
  - Add possibility to use `docker.imagePropertyConfiguration` with multiple images ([#1001](https://github.com/fabric8io/docker-maven-plugin/issues/1001))
  - Fix network aliases management for docker-compose mode ([#1000](https://github.com/fabric8io/docker-maven-plugin/issues/1000))

* **0.25.2** (2018-04-14)
  - Fix for docker login issue with index.docker.io using a credential helper ([#946](https://github.com/fabric8io/docker-maven-plugin/issues/946))

* **0.25.1** (2018-04-12)
  - Fix regression which broke labels and env with space ([#988](https://github.com/fabric8io/docker-maven-plugin/issues/988))
  - Fix and enhanced zero-config Dockerfile mode

* **0.25.0** (2018-04-04)
  - Fix possible NPE when logging to a file and the parent directory does not exist yet ([#911](https://github.com/fabric8io/docker-maven-plugin/issues/911)) ([#940](https://github.com/fabric8io/docker-maven-plugin/issues/940))
  - Change content type to "application/json" when talking to the Docker daemon ([#945](https://github.com/fabric8io/docker-maven-plugin/issues/945))
  - PostStart exec breakOnError now fails fast ([#970](https://github.com/fabric8io/docker-maven-plugin/issues/970))
  - Use docker.skip.tag property on push and remove ([#954](https://github.com/fabric8io/docker-maven-plugin/issues/954)) ([#869](https://github.com/fabric8io/docker-maven-plugin/issues/869))
  - Property placeholders are not interpolated when they are the only thing in the XML element value ([#960](https://github.com/fabric8io/docker-maven-plugin/issues/960))
  - Fix deadlock waiting on docker log pattern to match ([#767](https://github.com/fabric8io/docker-maven-plugin/issues/767)) ([#981](https://github.com/fabric8io/docker-maven-plugin/issues/981)) ([#947](https://github.com/fabric8io/docker-maven-plugin/issues/947))
  - Support multiline labels and empty labels ([#968](https://github.com/fabric8io/docker-maven-plugin/issues/968))
  - Handle multi line credential helper responses ([#930](https://github.com/fabric8io/docker-maven-plugin/issues/930))
  - Add support for merging external properties with XML configuration ([#938](https://github.com/fabric8io/docker-maven-plugin/issues/938)) ([#948](https://github.com/fabric8io/docker-maven-plugin/issues/948))
  - Allow to specify different environment variables for run and build via properties ([#386](https://github.com/fabric8io/docker-maven-plugin/issues/386))
  - Add simplified configuration which picks up a plain Dockerfile automatically from `src/main/docker` ([#957](https://github.com/fabric8io/docker-maven-plugin/issues/957))

* **0.24.0** (2018-02-07)
  - Respect system properties for ECR authentication ([#897](https://github.com/fabric8io/docker-maven-plugin/issues/897))
  - Simplified auto pull handling and moved to `imagePullPolicy` instead.
  - Initialize shutdown hook early to allow killing of containers when waiting for a condition ([#921](https://github.com/fabric8io/docker-maven-plugin/issues/921))
  - Fix for including in assembly in archive mode when using a Dockerfile ([#916](https://github.com/fabric8io/docker-maven-plugin/issues/916))
  - Fix for hanging wait on log ([#904](https://github.com/fabric8io/docker-maven-plugin/issues/904))
  - Fix for credential helper which do not return a version ([#896](https://github.com/fabric8io/docker-maven-plugin/issues/896))
  - Also remove tagged images when calling `docker:remove` ([#193](https://github.com/fabric8io/docker-maven-plugin/issues/193))
  - Introduced a `removeMode` for selecting the images to remove
  - Introduced a `breakOnError` for the `postStart` and `preStop` hooks in the
    wait configuration ([#914](https://github.com/fabric8io/docker-maven-plugin/issues/914))

Please note that `autoPullMode` is deprecated now and the behaviour of the `autoPullMode == always` has been changed slightly so that now, it really always pulls the image from the registry. Also `removeAll` for `docker:remove` is deprecated in favor of `removeMode` (and the default mode has changed slightly). Please refer to the documentation for more information.

* **0.23.0** (2017-11-04)
  - Support relative paths when binding volumes in `docker-compose.yml` ([#846](https://github.com/fabric8io/docker-maven-plugin/issues/846))
  - Allow  the session token for AWS authentication to be included in order to allow temporary security credentials provided by the AWS Security Token Service (AWS STS) to sign requests ([#883](https://github.com/fabric8io/docker-maven-plugin/issues/883))
  - Add support for credential helper to authenticate against a registry ([#821](https://github.com/fabric8io/docker-maven-plugin/issues/821))
  - Fix registry auth config in plugin configuration ([#858](https://github.com/fabric8io/docker-maven-plugin/issues/858))
  - Preserve leading whitespace in logs ([#875](https://github.com/fabric8io/docker-maven-plugin/issues/875))
  - Maven property interpolation in Dockerfiles ([#877](https://github.com/fabric8io/docker-maven-plugin/issues/877))
  - Allow parameters for the log prefix ([#890](https://github.com/fabric8io/docker-maven-plugin/issues/890))
  - When removing a volume don't error if the volume does not exist ([#788](https://github.com/fabric8io/docker-maven-plugin/issues/788))
  - Fix warning when COPY and/or ADD with parameters are used ([#884](https://github.com/fabric8io/docker-maven-plugin/issues/884))

* **0.22.1** (2017-08-28)
  - Allow Docker compose version "2", too ([#829](https://github.com/fabric8io/docker-maven-plugin/issues/829))
  - Allow a registry to be set programmatically ([#853](https://github.com/fabric8io/docker-maven-plugin/issues/853))

* **0.22.0** (2017-08-24)
  - Fix NPE when detecting cert paths ([#764](https://github.com/fabric8io/docker-maven-plugin/issues/764))
  - Fix `skipDockerMachine` ([#759](https://github.com/fabric8io/docker-maven-plugin/issues/759))
  - Fix property config handler to work also with dockerFile and dockerFileDir ([#790](https://github.com/fabric8io/docker-maven-plugin/issues/790))
  - Fix `dockerFile` option when pointing to another Dockerfile name ([#784](https://github.com/fabric8io/docker-maven-plugin/issues/784))
  - Allow comma separated list of container names in dependsOn elements ([#810](https://github.com/fabric8io/docker-maven-plugin/issues/810))
  - Trim whitespace and ignore empty elements in build configuration ports, runCmds, tags, volumes ([#816](https://github.com/fabric8io/docker-maven-plugin/issues/816))
  - Trim whitespace and ignore empty elements in run configuration ports ([#816](https://github.com/fabric8io/docker-maven-plugin/issues/816))
  - Fix "useAllReactorProjects" in assembly ([#812](https://github.com/fabric8io/docker-maven-plugin/issues/812))
  - Add ECDSA support ([#824](https://github.com/fabric8io/docker-maven-plugin/issues/824))
  - Fix test failures when build under Windows ([#834](https://github.com/fabric8io/docker-maven-plugin/issues/834))
  - Update dependencies to latest versions where possible

* **0.21.0** (2017-05-16)
  - Add wait checker for checking the exit code of a container ([#498](https://github.com/fabric8io/docker-maven-plugin/issues/498))
  - Check for exited container when doing wait checks ([#757](https://github.com/fabric8io/docker-maven-plugin/issues/757))
  - New assembly configuration "name" for specifying the directory which holds the assembly files ([#634](https://github.com/fabric8io/docker-maven-plugin/issues/634))
  - Add support for property replacement in external Dockerfiles ([#777](https://github.com/fabric8io/docker-maven-plugin/issues/777))

Please note that now filtering in an external Dockerfiles is switched on by default. This might interfere with Docker build args, so should switch filtering off with `<filter>false</filter>` in the `<build>` configuration if you have issues with this. See also the Documentation about [Filtering](https://dmp.fabric8.io/#build-filtering) for more Details.

* **0.20.1** (2017-03-29)
  - Tune log output for image names ([#737](https://github.com/fabric8io/docker-maven-plugin/issues/737))
  - Allow image with multiple path segments ([#694](https://github.com/fabric8io/docker-maven-plugin/issues/694))
  - Add support for PKCS#8 private keys in pem.key file. ([#730](https://github.com/fabric8io/docker-maven-plugin/issues/730))
  - Improve resource management for certificates and keys. ([#730](https://github.com/fabric8io/docker-maven-plugin/issues/730))
  - When using properties for configuration only build when `from` or `fromExt` is set ([#736](https://github.com/fabric8io/docker-maven-plugin/issues/736))
  - Add new mojo "docker:save" for saving the image to a file ([#687](https://github.com/fabric8io/docker-maven-plugin/issues/687))
  - Check whether a temporary tag could be removed and throw an error if not ([#725](https://github.com/fabric8io/docker-maven-plugin/issues/725))
  - Allow multi line matches in log output ([#628](https://github.com/fabric8io/docker-maven-plugin/issues/628))
  - Add a wait condition on a healthcheck when starting up containers ([#719](https://github.com/fabric8io/docker-maven-plugin/issues/719))
  - Don't use authentication from config when no "auth" is set ([#731](https://github.com/fabric8io/docker-maven-plugin/issues/731))

* **0.20.0** (2017-02-17)
  - Removed `build-nofork` and `source-nofork` in favor for a more direct solution which prevents forking of the lifecycle. Please refer the documentation, chapter "Assembly" for more information about this.

The experimental goals `build-nofork` and `source-nofork` have been removed again. Please use `build` and `source` directly when binding to execution phases.

* **0.19.1** (2017-02-09)

  - Fix handling of `run` commands from properties ([#684](https://github.com/fabric8io/docker-maven-plugin/issues/684))
  - Fix empty `<link>` causing `NullPointerException` ([#693](https://github.com/fabric8io/docker-maven-plugin/issues/693))

* **0.19.0** (2017-01-03)
  - Better log message when waiting for URL ([#640](https://github.com/fabric8io/docker-maven-plugin/issues/640))
  - Extended authentication for AWS ECR ([#663](https://github.com/fabric8io/docker-maven-plugin/issues/663))
  - Add two new goals: "volume-create" and "volume-remove" for volume handling independent of images.
  - Support for loading from an tar archive (option `<build><dockerArchive>`) ([#645](https://github.com/fabric8io/docker-maven-plugin/issues/645))
  - Support when both `dockerFileDir` and `dockerFile` are set and `dockerFile` is a relative path ([#624](https://github.com/fabric8io/docker-maven-plugin/issues/624))
  - Fix concurrency issue when writing into log files ([#652](https://github.com/fabric8io/docker-maven-plugin/issues/652))
  - Support any Docker build options ([#666](https://github.com/fabric8io/docker-maven-plugin/issues/666))

* **0.18.1** (2016-11-17)
  - Renamed `basedir` and `exportBasedir` in an `<assembly>` configuration to `targetDir` and `exportTargetDir` since this better reflects the purpose, i.e. the target in the Docker image to which the assembly is copied. The old name is still recognized but deprecated.
  - Fix issue with log statements which use a single argument form
  - Fix bug in HTTP wait configuration when using an external property handler ([#613](https://github.com/fabric8io/docker-maven-plugin/issues/613))
  - Fix NPE for "docker:log" when the container to log has already been stopped ([#612](https://github.com/fabric8io/docker-maven-plugin/issues/612))
  - Allow a protocol (tcp/udp) for the specification of a port ([#610](https://github.com/fabric8io/docker-maven-plugin/issues/610))

The following variables in the assembly configuration has been renamed for consistencies sake:

 * `basedir` --> `targetDir`
 * `exportBasedir` --> `exportTargetDir`

The old variable names are still accepted but will be removed for release 1.0

* **0.17.2** (2016-11-3)
  - Fix issues with an empty Docker config file

* **0.17.1** (2016-10-28)
  - Add initial [Docker compose](https://dmp.fabric8.io/#docker-compose) support ([#384](https://github.com/fabric8io/docker-maven-plugin/issues/384))
  - Made `docker:run` running in the foreground
  - Add lifecycle fork to package for `docker:build` and `docker:source` for ease of use. Introduced `docker:build-nofork` and `docker:source-nofork`
  - Removed lifecycle forks for all other Mojos ([#567](https://github.com/fabric8io/docker-maven-plugin/issues/567)) ([#599](https://github.com/fabric8io/docker-maven-plugin/issues/599))
  - Add new option `tarLongFileMode` for the assembly configuration to avoid warning for too long files ([#591](https://github.com/fabric8io/docker-maven-plugin/issues/591))
  - Add new option `tmpfs` for `<run>` to add mount pathes for temorary file systems ([#455](https://github.com/fabric8io/docker-maven-plugin/issues/455))
  - Changed `docker.image` to `docker.filter` and `<image>` to `<filter>`.

For 0.17 the lifecycle handling of the plugins has changed slightly. All forks to the _initialize_ phase have been removed since they collide with certain setups. Instead a fork to the _package_ phase has been introduced for `docker:build` and `docker:source` to make it easier for them to be consumed on the commandline (because otherwise at least `package` has to be added as goal so that the assembly could be constructed from the artifacts built). If you have these goals bound to an `<execution>` please use `build-nofork` and `source-nofork` instead, otherwise the package phase will be called twice.

Also the treatment of the Maven property `docker.image` has changed. This was supposed to be used as a filter which caused a lot of confusion if people accidentally put their Docker image names into this property. Now the property has no special meaning anymore, and you can use `docker.filter` now for filtering out a specific images to build. For the same reason the top-level configuration element `<image>` has been renamed to `<filter>`.

* **0.16.9** (2016-10-23)
  - Removed (undocumented) property `docker.image.name` which could be used to be inserted as a `%a` specifier part in an image name.
  - Fixed exposing of all property and port mappings ([#583](https://github.com/fabric8io/docker-maven-plugin/issues/583))
  - Fix concurrency issue on log wait ([#596](https://github.com/fabric8io/docker-maven-plugin/issues/596))
  - Add Dockerfile HEALTHCHECK support ([#594](https://github.com/fabric8io/docker-maven-plugin/issues/594))
  - Fix writing empty property files ([#592](https://github.com/fabric8io/docker-maven-plugin/issues/592))

* **0.16.8** (2016-10-14)
  - Allow multiple network links per `<link>` element ([#558](https://github.com/fabric8io/docker-maven-plugin/issues/558))
  - Fix startup of dependent containers when using links with specific container ids ([#586](https://github.com/fabric8io/docker-maven-plugin/issues/586))

* **0.16.7** (2016-10-07)
  - Even better logging

* **0.16.6** (2016-10-07)
  - Fix concurrency issues when doing a watch on logs ([#574](https://github.com/fabric8io/docker-maven-plugin/issues/574))
  - Break push with dedicated registry if temporary image tag already exists ([#575](https://github.com/fabric8io/docker-maven-plugin/issues/575))
  - Reduce log output for the non color case when pulling images ([#568](https://github.com/fabric8io/docker-maven-plugin/issues/568))
  - Add possibility to change colors in log messages
  - Don't print a progressbar when in batch mode (mvn -B) ([#564](https://github.com/fabric8io/docker-maven-plugin/issues/564))
  - Add `exposedProperty` key to change the alias part of the exposed container properties ([#557](https://github.com/fabric8io/docker-maven-plugin/issues/557))

* **0.16.5** (2016-09-27)
  - Refactored Docker connection parameter detection
  - Added a <fromExt> for extended definition of base images ([#572](https://github.com/fabric8io/docker-maven-plugin/issues/572))

* **0.16.4** (2016-09-26)
  - Fix issue with DOCKER_HOST coming from Docker Machine
  - Don't pull a 'scratch' base image ([#565](https://github.com/fabric8io/docker-maven-plugin/issues/565))
  - Fix handling when looking up non-existing containers ([#566](https://github.com/fabric8io/docker-maven-plugin/issues/566))

* **0.16.3** (2016-09-22)
  - Add 'allowAllHosts' to ping wait checker ([#559](https://github.com/fabric8io/docker-maven-plugin/issues/559))
  - Allow 'stopAllContainers' also as Maven properties ([#536](https://github.com/fabric8io/docker-maven-plugin/issues/536))
  - Use alias for stopping containers when naming strategy "alias" is used ([#536](https://github.com/fabric8io/docker-maven-plugin/issues/536))
  - New option 'startParallel' for docker:start to speedup execution ([#531](https://github.com/fabric8io/docker-maven-plugin/issues/531))
  - Tuned detection of docker host connection parameters to be more extensible

* **0.16.2** (2016-09-15)
  - Fixed naming of 'buildArgs' for `docker:build` (was `args` formerly)
  - Experimental Support for 'Docker for Windows' ([#523](https://github.com/fabric8io/docker-maven-plugin/issues/523))
  - Remove versions from custom lifecycle deps ([#539](https://github.com/fabric8io/docker-maven-plugin/issues/539))
  - Fix extra new line in logoutput ([#538](https://github.com/fabric8io/docker-maven-plugin/issues/538))

* **0.15.16** (2016-08-03)
  - Run 'stopContainer' in a Future to short circuit extra waiting ([#518](https://github.com/fabric8io/docker-maven-plugin/issues/518))
  - Don't pass `docker.buildArg` values that are empty ([#529](https://github.com/fabric8io/docker-maven-plugin/issues/529))
  - Add new implicit generated properties `docker.container.<alias>.net.<name>.ip` when custom networks are used ([#533](https://github.com/fabric8io/docker-maven-plugin/issues/533))

* **0.15.14** (2016-07-29)
  - Pattern match fix for multiline log output. Related to ([#259](https://github.com/fabric8io/docker-maven-plugin/issues/259))

* **0.15.13** (2016-07-29)
  - Add <securityOpts> for running containers in special security contexts ([#524](https://github.com/fabric8io/docker-maven-plugin/issues/524))
  - Add support for multiples network aliases ([#466](https://github.com/fabric8io/docker-maven-plugin/issues/466))

* **0.15.12** (2016-07-25)
  - API and documentation updates

* **0.15.11** (2016-07-20)
  - Invoke the `initialize` phase before docker goals ([#315](https://github.com/fabric8io/docker-maven-plugin/issues/315))
  - Allow images to only be pulled once per build (useful for reactor projects) ([#504](https://github.com/fabric8io/docker-maven-plugin/issues/504))
  - Allow retry of pushing a docker image in case of a 500 error ([#508](https://github.com/fabric8io/docker-maven-plugin/issues/508))
  - Add "ulimits" to run-configuration ([#484](https://github.com/fabric8io/docker-maven-plugin/issues/484))

* **0.15.10** (2016-07-19)
  - Don't do redirect when waiting on an HTTP port ([#499](https://github.com/fabric8io/docker-maven-plugin/issues/499))
  - Removed the container fetch limit of 100 and optimized getting containers by name and image ([#513](https://github.com/fabric8io/docker-maven-plugin/issues/513))

* **0.15.9** (2016-06-28)
  - Fixed issue when target directory does not exist yet ([#497](https://github.com/fabric8io/docker-maven-plugin/issues/497))

* **0.15.8** (2016-06-27)
  - Removed image configuration caching ([#495](https://github.com/fabric8io/docker-maven-plugin/issues/495))
  - Fix for tcp wait when used with Docker for Mac ([#430](https://github.com/fabric8io/docker-maven-plugin/issues/430))
  - Add warning when assembly is empty when watching a Docker image ([#490](https://github.com/fabric8io/docker-maven-plugin/issues/490))
  - Add `docker.skip.build`, `docker.skip.run`, `docker.skip.push` properties and
    renamed `docker.skipTags` to `docker.skip.tag` ([#483](https://github.com/fabric8io/docker-maven-plugin/issues/483))
  - Reverted jansi back to version 1.11 because of [this issue](https://github.com/fusesource/jansi/issues/58)
  - Add new assembly config options `permissions` for fine tuning permissions in the docker.tar ([#477](https://github.com/fabric8io/docker-maven-plugin/issues/477)). Deprecated `ignorePermissions`
    in favor of a `<permissions>ignore</permissions>`
  - Add auto creation of custom networks if the option `autoCreateCustomNetwork` is set ([#482](https://github.com/fabric8io/docker-maven-plugin/issues/482))
  - Support for docker machine added ([#481](https://github.com/fabric8io/docker-maven-plugin/issues/481))

* **0.15.7** (2016-06-09)
  - Add support for '.maven-dockerinclude' for including certain files in plain Dockerfile build ([#471](https://github.com/fabric8io/docker-maven-plugin/issues/471))
  - Add support for placeholders in image names.
  - Expose container id as Maven property `docker.container.<alias>.id` ([#412](https://github.com/fabric8io/docker-maven-plugin/issues/412))
  - Fix broken link in documentation ([#468](https://github.com/fabric8io/docker-maven-plugin/issues/468))

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
  - Moved to [fabric8io](https://github.com/orgs/fabric8io/dashboard) as GitHub organization which implies
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
