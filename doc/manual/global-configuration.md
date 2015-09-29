## Global configuration

Global configuration parameters specify overall behavior like the
connection to the Docker host. The corresponding system properties
which can be used to set it from the outside are given in
parentheses. 

* **dockerHost** (`docker.host`) Use this variable to specify the URL
  to on your Docker Daemon is listening. This plugin requires the
  usage of the Docker remote API so this must be enabled. If this
  configuration option is not given, the environment variable
  `DOCKER_HOST` is evaluated. If this is also not set the plugin will use `unix:///var/run/docker.sock`
  as a default. The scheme of this URL can be either given
  directly as `http` or `https` depending on whether plain HTTP
  communication is enabled or SSL should be used (default since Docker
  1.3.0). Alternatively the scheme could be `tcp` in which case the protocol is
  determined via the IANA assigned port: 2375 for `http` and 2376 for
  `https`. Finally Unix sockets are supported with when a scheme `unix` is used together with the 
  filesystem path to the unix socket.
* **apiVersion** (`docker.apiVersion`) Use this variable if you are using
  an older version of docker not compatible with the current default 
  use to communicate with the server.
* **certPath** (`docker.certPath`) Since 1.3.0 Docker remote API requires
  communication via SSL and authentication with certificates when used
  with boot2docker. These
  certificates are normally stored
  in `~/.docker/`. With this configuration the path can be set
  explicitly. If not set, the fallback is first taken from the
  environment variable `DOCKER_CERT_PATH` and then as last resort
  `~/.docker/`. The keys in this are expected with it standard names
  `ca.pem`, `cert.pem` and `key.pem`. Please refer to the
  [Docker documentation](https://docs.docker.com/articles/https/) for
  more information about SSL security with Docker. 
* **image** (`docker.image`) In order to temporarily restrict the
  operation of plugin goals this configuration option can be
  used. Typically this will be set via the system property
  `docker.image` when Maven is called. The value can be a single image
  name (either its alias or full name) or it can be a comma separated
  list with multiple image names. Any name which doesn't refer an
  image in the configuration will be ignored. 
* **verbose** (`docker.verbose`) Switch on the verbose mode, which e.g. will 
  print single build steps when creating an image. Switched off by default.
* **useColor** (`docker.useColor`)
  If set to `true` the log output of this plugin will be colored. By
  default the output is colored if the build is running with a TTY,
  without color otherwise.
* **skip** (`docker.skip`)
  With this parameter the execution of this plugin can be skipped
  completely. 
* **skipTags** (`docker.skipTags`)
  If set to `true` the plugin won't add any tags to images that have been built.
* **registry** (`docker.registry`)
  Specify globally a registry to use for pulling and pushing
  images. See [Registry handling](#registry-handling) for details. 
* **autoPull** (`docker.autoPull`)
  By default external images (base image for building or images to
  start) are downloaded automatically if they don't exist locally.
  With this options this can be switched off by setting this value to `off`.
  Checking for a newer version of an image and downloading it if it
  exists can be forced by setting this value to `always`. This will force an image 
  to be always pulled. This is true for any base image during build and for any image 
  during run which has no `<build>` section. Valid values are `on|off|always`.
* **authConfig** holds the authentication information when pulling from
  or pushing to Docker registry. There is a dedicated section 
  [Authentication](#authentication) for how doing security.
* **logDate** (`docker.logDate`) specifies the date format which is used for printing out
  container logs. This configuration can be overwritten by individual
  run configurations and described below. The format is described in
  [Log configuration](#log-configuration) below. 
* **logStdout** (`docker.logStdout`) if set, do all container logging to standard output, 
  regardless whether a `file` for log output is specified. See also [Log configuration](#log-configuration)
* **portPropertyFile** if given, specifies a global file into which the
  mapped properties should be written to. The format of this file and
  its purpose are also described in [Port Mapping](#port-mapping).
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
   <useColor>true</userColor>
   .....
</configuration>
````

