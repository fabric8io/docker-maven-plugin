## Global configuration

Global configuration parameters specify overall behavior like the
connection to the Docker host. The corresponding system properties
which can be used to set it from the outside are given in
parentheses.

The docker-maven-plugin uses the Docker remote API so the URL of your
Docker Daemon must somehow be specified. The URL can be specified by
the dockerHost or machine configuration, or by the `DOCKER_HOST`
environment variable.

Since 1.3.0, the Docker remote API supports communication via SSL and
authentication with certificates.  The path to the certificates can
be specified by the certPath or machine configuration, or by the
`DOCKER_CERT_PATH` environment variable.

* **apiVersion** (`docker.apiVersion`) Use this variable if you are using
  an older version of docker not compatible with the current default
  use to communicate with the server.
* **authConfig** holds the authentication information when pulling from
  or pushing to Docker registry. There is a dedicated section
  [Authentication](authentication.md) for how doing security.
* **autoCreateCustomNetworks** (`docker.autoCreateCustomNetworks`) If set to it will create
  Docker networks during `docker:start` and remove it during `docker:stop` if you provide
  a custom network in the run configuration of an image. The default is `false`.
* **autoPull** (`docker.autoPull`) Valid values: `on|off|always|once`. By default external images
  (base image for building or images to start) are downloaded automatically if they don't exist locally.
  This feature can be turned off by setting this value to `off`. If you want to check for a newer version
  of an image and download it if it exists, set this value to `always`. If you are running a `reactor` build,
  you may set this value to `once` so the image is only checkedn and pulled once for the entire build.
* **certPath** (`docker.certPath`) Since 1.3.0 Docker remote API requires
  communication via SSL and authentication with certificates when used
  with boot2docker or docker-machine. These
  certificates are normally stored
  in `~/.docker/`. With this configuration the path can be set
  explicitly. If not set, the fallback is first taken from the
  environment variable `DOCKER_CERT_PATH` and then as last resort
  `~/.docker/`. The keys in this are expected with it standard names
  `ca.pem`, `cert.pem` and `key.pem`. Please refer to the
  [Docker documentation](https://docs.docker.com/articles/https/) for
  more information about SSL security with Docker.
* **dockerHost** (`docker.host`)
  Use this parameter to directly specify the URL of the Docker Daemon.
  If this configuration option is not given, then the optional `<machine>`
  configuration section is consulted.
  The scheme of the URL can be either given directly as `http` or `https`
  depending on whether plain HTTP communication is enabled or SSL should
  be used. Alternatively the scheme could be `tcp` in which case the
  protocol is determined via the IANA assigned port: 2375 for `http`
  and 2376 for `https`. Finally, Unix sockets are supported by using
  the scheme `unix` together with the filesystem path to the unix socket.
  The discovery sequence used by the docker-maven-plugin to determine
  the URL is:
  - value of **dockerHost** (`docker.host`)
  - the `DOCKER_HOST` associated with the docker-machine named in `<machine>`. See below for details.
  - the value of the environment variable `DOCKER_HOST`.
  - `unix:///var/run/docker.sock` if it is a readable socket.
* **image** (`docker.image`) In order to temporarily restrict the
  operation of plugin goals this configuration option can be
  used. Typically this will be set via the system property
  `docker.image` when Maven is called. The value can be a single image
  name (either its alias or full name) or it can be a comma separated
  list with multiple image names. Any name which doesn't refer an
  image in the configuration will be ignored.
* **logDate** (`docker.logDate`) specifies the date format which is used for printing out
  container logs. This configuration can be overwritten by individual
  run configurations and described below. The format is described in
  [Log configuration](docker-start.html##log-configuration) below.
* **logStdout** (`docker.logStdout`) if set, do all container logging to standard output,
  regardless whether a `file` for log output is specified. See also [Log configuration](docker-start.html##log-configuration)
* **maxConnections** (`docker.maxConnections`) specifies how many parallel connections are allowed to be opened
  to the Docker Host. For parsing log output, a connection needs to be kept open (as well for the wait features),
  so don't put that number to low. Default is 100 which should be suitable for most of the cases.
* **outputDirectory** (`docker.target.dir`) specifies the default output directory to be
  used by this plugin. The default value is `target/docker` and is only used for the goal `docker:build`.
* **portPropertyFile** if given, specifies a global file into which the
  mapped properties should be written to. The format of this file and
  its purpose are also described in [Port Mapping](docker-start.html#port-mapping).
* **registry** (`docker.registry`)
  Specify globally a registry to use for pulling and pushing
  images. See [Registry handling](registry-handling.md) for details.
* **skip** (`docker.skip`)
  With this parameter the execution of this plugin can be skipped
  completely.
* **skipBuild** (`docker.skip.build`)
  If set not images will be build (which implies also *skip.tag*) with `docker:build`
* **skipPush** (`docker.skip.push`)
  If set dont push any images even when `docker:push` is called.
* **skipRun** (`docker.skip.run`)
  If set dont create and start any containers with `docker:start` or `docker:run`
* **skipTag** (`docker.skip.tag`)
  If set to `true` this plugin won't add any tags to images that have been built with `docker:build`
* **skipMachine** (`docker.skip.machine`)
  Skip using docker machine in any case
* **sourceDirectory** (`docker.source.dir`) specifies the default directory that contains
  the assembly descriptor(s) used by the plugin. The default value is `src/main/docker`. This
  option is only relevant for the `docker:build` goal.
* **outputDirectory** (`docker.target.dir`) specifies the default output directory to be
  used by the plugin. The default value is `target/docker` and is only used for the goal `docker:build`.
* **maxConnections** (`docker.maxConnections`) specifies how many parallel connections are allowed to be opened
  to the Docker Host. For parsing log output, a connection needs to be kept open (as well for the wait features),
  so don't put that number to low. Default is 100 which should be suitable for most of the cases.

Example:

````xml
<configuration>
   <dockerHost>https://localhost:2376</dockerHost>
   <certPath>src/main/dockerCerts</certPath>
   <useColor>true</useColor>
   .....
</configuration>
````

docker-maven-plugin supports also Docker machine (which must be installed locally, of course).
A Docker machine configuration can be provided with a top-level `<machine>` configuration section.
This configuration section knows the following options:

* **name** for the Docker machine's name. Default is `default`
* **autoCreate** if set to `true` then a Docker machine will automatically created. Default is `false`.
* **createOptions** is a map with options for Docker machine when auto-creating a machine. See the docker machine
  documentation for possible options.

When no Docker host is configured or available as environment variable, then the configured Docker machine
is used. If the machine exists but is not running, it is started automatically. If it does not exists but `autoCreate`
is true, then the machine is created and started. Otherwise an error is printed. Please note, that a machine
which has been created because of `autoCreate` gets never deleted by docker-maven-plugin. This needs to be done manually
if required.

In absent of a `<machine>` configuration section the Maven property `docker.machine.name` can be used to provide
the name of a Docker machine. Similarly the property `docker.machine.autoCreate` can be set to true for creating
a Docker machine, too.

You can use the property `docker.skip.machine` if you want to override the internal detection mechanism to always
disable docker machine support.

Example:

````xml
<!-- Work with a docker-machine -->
<configuration>
  <machine>
    <name>maven</name>
    <autoCreate>true</autoCreate>
    <createOptions>
      <driver>virtualbox</driver>
      <virtualbox-cpu-count>2</virtualbox-cpu-count>
    </createOptions>
  </machine>
   .....
</configuration>
````
