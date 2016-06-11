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

* **apiVersion** (`docker.apiVersion`) Use this parameter if you are
  using an older version of Docker not compatible with the current
  default used to communicate with the server.
* **dockerHost** (`docker.host`)
  Use this parameter to directly specify the URL of the Docker Daemon.
  If this configuration option is not given, then the **&lt;machine&gt;**
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
  - the `DOCKER_HOST` associated with the docker-machine named in **&lt;machine&gt;**.
  - the value of the environment variable `DOCKER_HOST`.
  - `unix:///var/run/docker.sock` if it a readable socket.
* **certPath** (`docker.certPath`)
  Use this parameter to directly specify the directory of SSL
  certificates to use when communicating with the Docker daemon.
  If this configuration option is not given, then the **&lt;machine&gt;**
  configuration section is consulted.  
  The keys in this directory are expected to have the standard names
  `ca.pem`, `cert.pem` and `key.pem`. Please refer to the
  [Docker documentation](https://docs.docker.com/articles/https/) for
  more information about SSL security with Docker.  
  The discovery sequence used by the docker-maven-plugin to determine
  the SSL certificate directory is:
   - value of **certPath** (`docker.certPath`)
   - the `DOCKER_CERT_PATH` associated with the docker-machine named in **&lt;machine&gt;**.
   - the value of the environment variable `DOCKER_CERT_PATH`.
   - user directory `~/.docker/`
* **machine** Use this configuration section with sub-elements to work
   with a docker-machine.  The docker-machine executable must be
   available in the PATH on the host running the maven build.  If the
   named docker-machine is not started, it will be started.
 * **name** (`docker.machine.name`)
  This parameter specifies the name of a docker-machine.  The
  docker-machine will be queried for the Docker Daemon URL and
  certificate path. If **name** is not set, the docker-machine named
  'default' will be queried.
 * **autoCreate** (`docker.machine.autoCreate`)
  Setting this parameter true will create the docker-machine if it
  does not exist.  If not provided, this parameter will be false.
 * **createOptions**
  This is the map of name to value pairs used when creating a docker-machine.
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
  If set to `true` the docker-maven-plugin won't add any tags to images that have been built.
* **registry** (`docker.registry`)
  Specify globally a registry to use for pulling and pushing
  images. See [Registry handling](registry-handling.md) for details. 
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
  [Authentication](authentication.md) for security.
* **logDate** (`docker.logDate`) specifies the date format which is used for printing out
  container logs. This configuration can be overwritten by individual
  run configurations and described below. The format is described in
  [Log configuration](docker-start.html#log-configuration).
* **logStdout** (`docker.logStdout`) if set, do all container logging to standard output, 
  regardless whether a `file` for log output is specified. See also
  [Log configuration](docker-start.html#log-configuration)
* **portPropertyFile** if given, specifies a global file into which the
  mapped properties should be written to. The format of this file and
  its purpose are also described in [Port Mapping](docker-start.html#port-mapping).
* **sourceDirectory** (`docker.source.dir`) specifies the default directory that contains
  the assembly descriptor(s) used by the plugin. The default value is `src/main/docker`. This
  option is only relevant for the `docker:build` goal.
* **outputDirectory** (`docker.target.dir`) specifies the default output directory to be
  used by docker-maven-plugin. The default value is `target/docker` and is only used for the goal `docker:build`.
* **maxConnections** (`docker.maxConnections`) specifies how many parallel connections are allowed to be opened
  to the Docker Host. For parsing log output, a connection needs to be kept open (as well for the wait features), 
  so don't put that number to low. Default is 100 which should be suitable for most of the cases.

Examples:

````xml
<!-- directly configure communication with daemon -->
<configuration>
   <dockerHost>https://localhost:2376</dockerHost>
   <certPath>src/main/dockerCerts</certPath>
   <useColor>true</useColor>
   .....
</configuration>
````


````xml
<!-- work with a docker-machine -->
<configuration>
  <machine>
    <autoCreate>true</autoCreate>
      <createOptions>
        <driver>virtualbox</driver>
        <virtualbox-cpu-count>2</virtualbox-cpu-count>
    </createOptions>
   </machine>
   .....
</configuration>
````

