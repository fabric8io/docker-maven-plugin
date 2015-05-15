* [Installation](#installation)
* [Global Configuration](#global-configuration)
* [Image Configuration](#image-configuration)
* [Maven Goals](#maven-goals)
  - [`docker:build`](#dockerbuild)
  - [`docker:start`](#dockerstart)
  - [`docker:stop`](#dockerstop)
  - [`docker:push`](#dockerpush)
  - [`docker:remove`](#dockerremove)
  - [`docker:logs`](#dockerlogs)
* [External Configuration](#external-configuration)
* [Registry Handling](#registry-handling)
* [Authentication](#authentication)

## User Manual

The following sections describe the installation of this plugin, the
available goals and its configuration options. 

### Installation

This plugin is available from Maven central and can be connected to
pre- and post-integration phase as seen below. The configuration and
available goals are described below. 

````xml
<plugin>
  <groupId>org.jolokia</groupId>
  <artifactId>docker-maven-plugin</artifactId>
  <version>0.11.3</version>

  <configuration>
     ....
     <images>
        <!-- A single's image configuration -->
        <image>
           ....
        </image>
        ....
     </images>
  </configuration>

  <!-- Connect start/stop to pre- and
       post-integration-test phase, respectively if you want to start
       your docker containers during integration tests -->
  <executions>
    <execution>
       <id>start</id>
       <phase>pre-integration-test</phase>
       <goals>
         <!-- "build" should be used to create the images with the
              artefacts --> 
         <goal>build</goal>
         <goal>start</goal>
       </goals>
    </execution>
    <execution>
       <id>stop</id>
       <phase>post-integration-test</phase>
       <goals>
         <goal>stop</goal>
      </goals>
    </execution>
  </executions>
</plugin>
````

### Global configuration

Global configuration parameters specify overall behavior like the
connection to the Docker host. The corresponding system properties
which can be used to set it from the outside are given in
parentheses. 

* **dockerHost** (`docker.host`) Use this variable to specify the URL
  to on your Docker Daemon is listening. This plugin requires the
  usage of the Docker remote API so this must be enabled. If this
  configuration option is not given, the environment variable
  `DOCKER_HOST` is evaluated. If this is also not set the plugin will
  stop with an error. The scheme of this URL can be either given
  directly as `http` or `https` depending on whether plain HTTP
  communication is enabled (< 1.3.0) or SSL should be used (>=
  1.3.0). Or the scheme could be `tcp` in which case the protocol is
  determined via the IANA assigned port: 2375 for `http` and 2376 for
  `https`. 
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
* **registry** (`docker.registry`)
  Specify globally a registry to use for pulling and pushing
  images. See [Registry handling](#registry-handling) for details. 
* **autoPull** (`docker.autoPull`)
  By default external images (base image for building or images to
  start) are downloaded automatically. With this options this can be
  switched off by setting this value to `false`.
* **authConfig** holds the authentication information when pulling from
  or pushing to Docker registry. There is a dedicated
  [section](#authentication) for how doing security.
* **logDate** (`docker.logDate`) specifies the date format which is used for printing out
  container logs. This configuration can be overwritten by individual
  run configurations and described below. The format is described in
  the [section](#log-configuration) below. 
* **sourceDirectory** (`docker.source.dir`) specifies the default directory that contains
  the assembly descriptor(s) used by the plugin. The default value is `src/main/docker`. This
  option is only relevant for the `docker:build` goal.
* **outputDirectory** (`docker.target.dir`) specifies the default output directory to be
  used by the plugin. The default value is `target/docker` and is only used for the goal `docker:build`.


Example:

````xml
<configuration>
   <dockerHost>https://localhost:2376</dockerHost>
   <certPath>src/main/dockerCerts</certPath>
   <useColor>true</userColor>
   .....
</configuration>
````

### Image configuration

The plugin's configuration is centered around *images*. These are
lockspecified for each image within the `<images>` element of the
configuration with one `<image>` element per image to use. 

The `<image>` element can contain the following sub elements:

* **name** : Each `<image>` configuration has a mandatory, unique docker
  repository *name*. This can include registry and tag parts, too. For
  definition of the repository name please refer to the
  Docker documentation
* **alias** is a shortcut name for an image which can be used for
  identifying the image within this configuration. This is used when
  linking images together or for specifying it with the global
  **image** configuration.
* **registry** is a registry to use for this image. If the `name`
  already contains a registry this takes precedence. See
  [Registry handling](#registry-handling) for more details.
* **build** is a complex element which contains all the configuration
  aspects when doing a `docker:build` or `docker:push`. This element
  can be omitted if the image is only pulled from a registry e.g. as
  support for integration tests like database images.
* **run** contains subelements which describe how containers should be
  created and run when `docker:start` or `docker:stop` is called. If
  this image is only used a *data container* for exporting artifacts
  via volumes this section can be missing.
* **external** can be used to fetch the configuration through other
  means than the intrinsic configuration with `run` and `build`. It
  contains a `<type>` element specifying the handler for getting the
  configuration. See [External configuration](#external-configuration)
  for details.

Either `<build>` or `<run>` must be present. They are explained in
details in the corresponding goal sections.

Example:

````xml
<configuration>
  ....
  <images>
    <image>
      <name>jolokia/docker-demo:0.1</name>
      <alias>service</alias>
      <run>....</run>
      <build>....</build>      
    </image>  
  </images>
</configuration>
````

### Maven Goals

This plugin supports the following goals which are explained in detail
in the following sections.

| Goal                             | Description                          |
| -------------------------------- | ------------------------------------ |
| [`docker:build`](#dockerbuild)   | Build images                         |
| [`docker:start`](#dockerstart)   | Create and start containers          |
| [`docker:stop`](#dockerstop)     | Stop and destroy containers          |
| [`docker:push`](#dockerpush)     | Push images to a registry            |
| [`docker:remove`](#dockerremove) | Remove images from local docker host |
| [`docker:logs`](#dockerlogs)     | Show container logs                  |

Note that all goals are orthogonal to each other. For example in order
to start a container for your application you typically have to build
its image before. `docker:start` does **not** imply building the image
so you should use it then in combination with `docker:build`.  

#### `docker:build`

This goal will build all images which have a `<build>` configuration
section, or, if the global configuration `image` is set, only those
images contained in this variable will be build. 

All build relevant configuration is contained in the `<build>` section
of an image configuration. The available subelements are

* **assembly** specifies the assembly configuration as described in
  [Build Assembly](#build-assembly)
* **command** is the command to execute by default (i.e. if no command
  is provided when a container for this image is started).
* **env** hold environments as described in
  [Setting Environment Variables](#setting-environment-variables). 
* **from** specifies the base image which should be used for this
  image. If not given this default to `busybox:latest` and is suitable
  for a pure data image.
* **ports** describes the exports ports. It contains a list of
  `<port>` elements, one for each port to expose.
* **volumes** contains a list of `volume` elements to create a container
  volume.
* **tags** contains a list of additional `tag` elements with which an
  image is to be tagged after the build.
* **maintainer** specifies the author (MAINTAINER) field for the generated image

From this configuration this Plugin creates an in-memory Dockerfile,
copies over the assembled files and calls the Docker daemon via its
remote API. 

Here's an example:

````xml
<build>
  <from>java:8u40</from>
  <maintainer>john.doe@example.com</maintainer>
  <tags>
    <tag>latest</tag>
    <tag>${project.version}</tag>
  </tags>
  <ports>
    <port>8080</port>
  </ports>
  <volumes>
    <volume>/path/to/expose</volume>
  </volumes>
  <command>java /opt/demo/server.jar</command>
  <assembly>
    <basedir>/opt/demo</basedir>
    <descriptor>assembly.xml</descriptor>
  </assembly>
</build>
````

##### Build Assembly

* **basedir** depicts the directory under which the files and
  artifacts contained in the assembly will be copied within the
  container. The default value for this is `/maven`.
* **inline** inlined assembly descriptor as
  described in the section [Docker Assembly](#docker-assembly) below. 
* **descriptor** is a reference to an assembly descriptor as
  described in the section [Docker Assembly](#docker-assembly) below. 
* **descriptorRef** is an alias to a predefined assembly
  descriptor. The available aliases are also described in the
  [Docker Assembly](#docker-assembly) section.
* **dockerFileDir** specifies a directory containing an external Dockerfile
  that will be used to create the image. Any additional files located in this
  directory will also be added to the image. Usage of this directive will take
  precedence over any configuration specified in the `build` element. In addition to
  the files specified within the assembly also all files contained in this directory
  are added to the docker build directory. If this path is not an absolute path it 
  is resolved relatively to `src/main/docker`. You can make easily an absolute path by 
  using `${project.baseDir}` as prefix for your path
* **exportBasedir** indicates if the `basedir` should be exported as a volume.
  This value is `true` by default except in the case the `basedir` is set to 
  the container root (`/`). It is also `false` by default when a base image is used with `from` 
  since exporting makes no sense in this case and will waste disk space unnecessarily.    
* **ignorePermissions** indicates if existing file permissions should be ignored
  when creating the assembly archive. This value is `false` by default.

In the event you do not need to include any artifacts with the image, you may
safely omit this element from the configuration. 

##### Docker Assembly

With using the `inline`, `descriptor` or `descriptorRef` option
it is possible to bring local files, artifacts and dependencies into
the running Docker container. A `descriptor` points to a file
describing the data to put into an image to build. It has the same
[format](http://maven.apache.org/plugins/maven-assembly-plugin/assembly.html)
as for creating assemblies with the
[maven-assembly-plugin](http://maven.apache.org/plugins/maven-assembly-plugin/)
with following exceptions:

* `<formats>` are ignored, the assembly will allways use a directory
  when preparing the data container (i.e. the format is fixed to
  `dir`) 
* The `<id>` is ignored since only a single assembly descriptor is
  used (no need to distinguish multiple descriptors) 

Also you can inline the assembly description with a `inline` description 
directly into the pom file. Adding the proper namespace even allows for 
IDE autocompletion. As an example, refer to the profile `inline` in 
the `data-jolokia-demo`'s pom.xml. 

Alternatively `descriptorRef` can be used with the name of a
predefined assembly descriptor. The following symbolic names can be
used for `assemblyDescriptorRef`:

* **artifact-with-dependencies** will copy your project's artifact and
  all its dependencies 
* **artifact** will copy only the project's artifact but no
  dependencies. 
* **project** will copy over the whole Maven project but with out
  `target/` directory. 
* **rootWar** will copy the artifact as `ROOT.war` to the exposed
  directory. I.e. Tomcat will then deploy the war under the root
  context. 

All declared files end up in the configured `basedir` (or `/maven`
by default) in the created image.
 
If the assembly references the artifact to build with this pom, it is 
required that the `package` phase is included in the run. This happens 
either automatically when the `docker:build` target is called as part 
of a binding (e.g. is `docker:build` is bound to the `pre-integration-test` 
phase) or it must be ensured when called on the command line:

````bash
mvn package docker:build
````

This is a general restriction of the Maven lifecycle which applies also 
for the `maven-assembly-plugin` itself.

In the following example a dependency from the pom.xml is included and
mapped to the name `jolokia.war`. With this configuration you will end
up with an image, based on `busybox` which has a directory `/maven`
containing a single file `jolokia.war`. This volume is also exported
automatically. 

```xml
<assembly>
  <dependencySets>
    <dependencySet>
      <includes>
        <include>org.jolokia:jolokia-war</include>
      </includes>
      <outputDirectory>.</outputDirectory>
      <outputFileNameMapping>jolokia.war</outputFileNameMapping>
    </dependencySet>
  </dependencySets>
</assembly>
```

Another container can now connect to the volume an 'mount' the 
`/maven` directory. A container  from `consol/tomcat-7.0` will look
into `/maven` and copy over everything to `/opt/tomcat/webapps` before
starting Tomcat.

If you are using the `artifact` or `artifact-with-dependencies` descriptor, it is
possible to change the name of the final build artifact with the following:

```xml
<build>
  <finalName>your-desired-final-name</build>
  ...
</build>
```

Please note, based upon the following documentation listed [here](http://maven.apache.org/pom.html#BaseBuild_Element),
there is no guarantee the plugin creating your artifact will honor it in which case you will need to use a custom
descriptor like above to achieve the desired naming.

Currently the `jar` and `war` plugins properly honor the usage of `finalName`.



#### `docker:start`

Creates and starts docker containers. This goals evaluates
the configuration's `<run>` section of all given (and enabled images). In order to switch on 
globally the logs **showLogs** can be used as global configuration (i.e. outside of `<images>`).
If set it will print out all standard output and standard error messages for all containers started. 
As value the images for which logs should be shown can be given as a comma separated list. This is probably most 
useful when used from the command line as system property `docker.showLogs`.   

The `<run>` configuration knows the following sub elements:

* **capAdd** (*v1.14*) a list of `add` elements to specify kernel parameters to add to
  the container.
* **capDrop** (*v1.14*) a list of `drop` elements to specify kernel parameters to remove
  from the container.
* **command** is a command which should be executed at the end of the
  container's startup. If not given, the image's default command is
  used. 
* **domainname** (*v1.12*) domain name for the container
* **dns** (*v1.11*) list of `host` elements specifying dns servers for the container to use
* **dnsSearch** (*v1.15*) list of `host` elements specying dns search domains 
* **entrypoint** (*v1.15*) set the entry point for the container
* **env** can contain environment variables as subelements which are
  set during startup of the container. They are specified in the
  typical maven property format as described [below](#setting-environment-variables).
* **envPropertyFile** can be a path to a property file holding environment variables. If given, the variables
  specified in this property file overrides the environment variables specified in the configuration.
* **extraHosts** (*v1.15*) list of `host` elements in the form `host:ip` to add to the
  container's `/etc/hosts` file.
* **hostname** (*v1.11*) desired hostname for the container
* **links** declares how containers are linked together see
  description on [container linking](#container-linking). 
* **log** specifies the log configuration for whether and how log
  messages from the running containers should be printed. See
  [below](#log-configuration) for a detailed description of this configuration
  section. 
* **memory** (*v1.11*) memory limit in bytes
* **memorySwap** (*v1.11*) total memory usage (memory + swap); use -1 to disable swap.
* **namingStrategy** sets the name of the container
  - `none` : uses randomly assigned names from docker (default)
  - `alias` : uses the `alias` specified in the `image` configuration. An error is thrown, if a container already exists
    with this name.
* **portPropertyFile**, if given, specifies a file into which the
  mapped properties should be written to. The format of this file and
  its purpose are also described [below](#port-mapping)
* **ports** declares how container exposed ports should be
  mapped. This is described below in an extra
  [section](#port-mapping).  
* **privileged** (*v1.11*) give container full access to host (`true|false`)   
* **restartPolicy** (*v1.15*) specifies the container restart policy, see 
  [below](#container-restart-policy)
* **user** (*v1.11*) user used inside the container
* **volumes** for bind configurtion of host directories and from other containers. See "[Volume binding]
 (#volume-binding)" for details.
* **wait** specifies condition which must be fulfilled for the startup
  to complete. See [below](#wait-during-startup-and-shutdown) which subelements are
  available and how they can be specified.
* **watch** enables the image watch See [below](#watching-for-image-changes) which subelements are
  available and how they can be specified.
* **workingDir** (*v1.11*) working dir for commands to run in

Example:

````xml
<run>
  <env>
    <CATALINA_OPTS>-Xmx32m</CATALINA_OPTS>
    <JOLOKIA_OFF/>
  </env>
  <ports>
    <port>jolokia.port:8080</port>
  </ports>
  <links>
    <link>db</db>
  </links>
  <wait>
    <url>http://localhost:${jolokia.port}/jolokia</url>
    <time>10000</time>
  </wait>
  <log>
    <prefix>DEMO</prefix>
    <date>ISO8601</date>
    <color>blue</color>
  </log>
  <command>java -jar /maven/docker-demo.jar</command>
</run>
````

##### Setting environment variables

When creating a container one or more environment variables can be set
via configuration with the `env` parameter 

```xml
<env>
  <JAVA_HOME>/opt/jdk8</JAVA_HOME>
  <CATALINA_OPTS>-Djava.security.egd=file:/dev/./urandom</CATALINA_OPTS>
</env>
```

If you put this configuration into profiles you can easily create
various test variants with a single image (e.g. by
switching the JDK or whatever).

It is also possible to set the environment variables from the outside of the plugin's 
configuration with the parameter `envPropertyFile`. If given, this property file
is used to set the environment variables where the keys and values specify the environment variable. 
Environment variables specified in this file override any environment variables specified in the configuration.

##### Port Mapping

The `<ports>` configuration contains a list of port mappings. Each
mapping has multiple parts, each separate by a colon. This is
equivalent to the port mapping when using the Docker CLI with option
`-p`. 

```xml
<ports>
  <port>18080:8080</port> 
  <port>host.port:80</port> 
<ports>
```

A `port` stanza may take one of two forms:

* A tuple consisting of two numeric values separated by a `:`. This
  form will result in an explicit mapping between the docker host and
  the corresponding port inside the container. In the above example,
  port 18080 would be exposed on the docker host and mapped to port
  8080 in the running container. 
* A tuple consisting of a string and a numeric value separated by a
  `:`. In this form, the string portion of the tuple will correspond
  to a Maven property. If the property is undefined when the `start`
  task executes, a port will be dynamically selected by Docker in the
  ephemeral port range and assigned to the property which may then be
  used later in the same POM file. The ephemeral port range is configured
  by the `/proc/sys/net/ipv4/ip_local_port_range` kernel parameter, which
  typically ranges from 32678 to 61000.  If the property exists and has a
  numeric value, that value will be used as the exposed port on the
  docker host as in the previous form. In the above example, the
  docker service will elect a new port and assign the value to the
  property `host.port` which may then later be used in a property
  expression similar to `<value>${host.port}</value>`. This can be
  used to pin a port from the outside when doing some initial testing
  similar to `mvn -Dhost.port=10080 docker:start`

Both forms of the `port` stanza also support binding to a specific ip 
address on the docker host.

```xml
<ports>
  <port>1.2.3.4:80:80</port>
  <port>1.2.3.4:host.port:80</port>
</ports>
```

As a convenience, a hostname pointing to the docker host may also
be specified. The container will fail to start if the hostname resolves
to an ip address of something other then the docker host itself.

```
<ports>
  <port>docker.example.com:80:80</port>
</ports>
```

Another useful configuration option is `portPropertyFile` with which a
file can be specified to which the real port mapping is written after
all dynamic ports has been resolved. The keys of this property file
are the variable names, the values are the dynamically assigned host
ports. This property file might be useful together with other maven
plugins which already resolved their maven variables earlier in the
lifecycle than this plugin so that the port variables might not be
available to them.

##### Container linking

The `<links>` configuration contains a list of containers that should
be linked to this container according to
[Docker Links](https://docs.docker.com/userguide/dockerlinks/). Each
link can have two parts where the optional right side is separated by
a `:` and will be used as the name in the environment variables and
the left side refers to the name of the container linking to. This is
equivalent to the linking when using the Docker CLI `--link` option.

Example for linking to a container with name or alias *postgres* :

```xml
<links>
  <link>postgres:db</link>
</links>
```

This will create the following environment variables, given that the
postgres image exposes TCP port 5432: 

```
DB_NAME=/web2/db
DB_PORT=tcp://172.17.0.5:5432
DB_PORT_5432_TCP=tcp://172.17.0.5:5432
DB_PORT_5432_TCP_PROTO=tcp
DB_PORT_5432_TCP_PORT=5432
DB_PORT_5432_TCP_ADDR=172.17.0.5
```

##### Volume binding

A container can bind (or "mount") volumes from various source when starting up: Either from a directory of
the host system or from another container which exports one or more directories. The mount configuration is
specified within a `<volumes>` section of the run configuration. It can contain the following sub elements:

* **from** can contain a list of `<image>` elements which specify
  image names or aliases of containers whose volumes should be imported.
* **bind** can contain a list of `<volume>` specifications (or 'host mounts). Use `/path` to create and
  expose a new volume in the container, `/host_path:/container_path` to mount a host path into the
  container and `/host_path:/container_path:ro` to bind it read-only.

````xml
<volumes>
  <bind>
    <volume>/logs</volume>
    <volume>/opt/host_export:/opt/container_import</volume>
  </bind>
  <from>
    <image>jolokia/docker-demo</image>
  </from>
</volumes>
````

In this example the container creates a new volume named  `/logs` on the container and mounts `/opt/host_export` from
the host as `/opt/container_import` on the container. In addition all exported volumes from the container which has
been created from the image `jolokia/docker-demo` are mounted directly into the container (with the same name as
the exporting container exposes these directories). The image must be also configured for this plugin. Instead of
the full image name, an alias name like *service* can be used, too.

Please note, that no relative paths are allowed. However, you can use Maven variables in the path specifications. This
should even work for boot2docker:

````xml
<volumes>
  <bind>
    <volume>${project.build.directory}/${project.artifactId}-${project.version}:/usr/local/tomcat/webapps/${project.name}</volume> 
    <volume>${project.basedir}/data:/data</volume>
  </bind>
</volumes>
````xml

##### Container restart policy

Specify the behavior to apply when the container exits. These values can be
specified withing a `<restartPolicy>` section with the following sub-elements:

* **name** restart policy name, choose from:
  * `always` (*v1.15*) always restart
  * `on-failure` (*v1.15*) restart on container non-exit code of zero
* **retry** if `on-failure` is used, controls max number of attempts to 
  restart before giving up.

The behavior to apply when the container exits. The value is an object with a Name 
property of either "always" to always restart or "on-failure" to restart only when the 
container exit code is non-zero. If on-failure is used, MaximumRetryCount controls the
 number of times to retry before giving up. The default is not to restart. (optional)


##### Wait during startup and shutdown

While starting a container is it possible to block the execution until
some condition is met. These conditions can be specified within a
`<wait>` section which the following sub-elements:

* **url** is an URL which is polled periodically until it returns a
  HTTP 200 status code.
* **log** is a regular expression which is applied against the log
  output of an container and blocks until the pattern is matched.
* **time** is the time in milliseconds to block.
* **shutdown** is the time to wait in milliseconds between stopping a container
  and removing it. This might be helpful in situation where a Docker croaks with an
  error when trying to remove a container to fast after it has been stopped.

As soon as one condition is met the build continues. If you add a
`<time>` constraint this works more or less as a timeout for other
conditions. 

Example:

````xml
<wait>
  <url>http://localhost:${host.port}</url>
  <time>10000</time>
  <shutdown>500</shutdown>
</wait>
```` 

This setup will wait for the given URL to be reachable but ten seconds
at most. Also, when stopping the container after an integration tests, the
build wait for 500 ms before it tries to remove the container (if not `keepContainer`
or `keepRunning` is used). You can use maven properties in each
condition, too. In the example, the `${host.port}` property is
probably set before within a port mapping section. 
 
The property `${docker.host.address}` is set implicitly to the address of the Docker host.

##### Log configuration

When running containers the standard output and standard error of the
container can be printed out. Several options are available for
configuring the log output:

* **enabled** If given and set to `false` log output is disabled. This
  is useful if you want to disable log output by default but want to
  use the other configuration options when log output is switched on
  on the command line with `-Ddocker.showLogs`. Logging is enabled by
  default if a `<log>` section is given.
* **prefix** Prefix to use for the log output in order to identify the
  container. By default the image `alias` is used or alternatively the
  container `id`. 
* **date** Dateformat to use for log timestamps. If `<date>` is not
  given no timestamp will be shown. The date specification can be
  either a constant or a date format. The recognized constants are:
  * `NONE` Switch off timestamp output. Useful on the command line
    (`-Ddocker.logDate=NONE`) for switching off otherwise enabled
    logging. 
  * `DEFAULT` A default format in the form `HH:mm:ss.SSS`
  * `MEDIUM` Joda medium date time format
  * `SHORT` Joda short date time format
  * `LONG` Joda long date time format
  * `ISO8601` Full ISO-8601 formatted date time with milli seconds
  As an alternative a date-time format string as recognized by
  [JodaTime](http://joda-time.sourceforge.net/apidocs/org/joda/time/format/DateTimeFormat.html)
  is possible. In order to set a consistent date format 
  the global configuration parameter `logDate` can be used.
* **color** Color used for coloring the prefix when coloring is enabeld
  (i.e. if running in a console and `useColor` is set). The available
  colors are `YELLOW`, `CYAN`, `MAGENTA`, `GREEN`, `RED`, `BLUE`. If
  coloring is enabled and now color is provided a color is picked for
  you. 
  
Example (values can be case insensitive, too) :

````xml
<log>
  <prefix>TC</prefix>
  <date>default</date>
  <color>cyan</color>
</log>
````

##### Watching for image changes

When developing docker images, building the image and manually starting 
stopping containers, can become a real burden. To slightly increase the user
experience, you can enable image watching on `<docker:start>` in which case the image
will polled for changes, and if any change is spotted, the 
container will get recreated and restarted, using the given image.

Image watch can be configured as part of the run configuration like:

````xml
<run>
  <watch>
    <interval>5000</interval>
  </watch>
</run>
````

In order to start watching for image updates, the property `docker.watch` must be 
set to true:

    mvn docker:start -Ddocker.watch=true -Ddocker.watch.interval=5000

As you can see in this example, the watch interval can be provided on the command line, too.
Please note that the Maven process will the run forever until it is killed. The containers will then be still running 
 and must be stopped with `docker:stop`, though.

When a watched image is removed, error message will be print out periodically while watching.
So don't do that ;-)

If containers are linked together network or volume wise, and you update a container which other containers dependent on, 
the dependant containers are not restarted. E.g. when you have a "service" container accessing a "db" container and the
"db" container is updated, then you "service" container will faill until it is restarted, too. A future version of this 
plugin will take care of restarting these containers, too (in the right order), but for now you would have to do this 
manually.

#### `docker:stop`

Stops and removes a docker container. This goals starts every
container started with `<docker:stop>` either during the same build
(e.g. when bound to lifecycle phases when doing integration tests) or
for containers created by a previous call to `<docker:start>`

If called within the same build run it will exactly stop and destroy
all containers started by this plugin. If called in a separate run it
will stop (and destroy) all containers which were created from images
which are configured for this goal. This might be dangerous, but of
course you can always stop your containers with the Docker CLI, too.

For tuning what should happen when stopping there are two global
parameters which are typically used as system properties:

* **keepContainer** (`docker.keepContainer`) If given will not destroy
  container after they have been stopped. 
* **keepRunning** (`docker.keepRunning`) actually won't stop the
  container. This apparently makes only sense when used on the command
  line when doing integration testing (i.e. calling `docker:stop`
  during a lifecycle binding) so that the container are still running
  after an integration test. This is useful for analysis of the
  containers (e.g. by entering it with `docker exec`). 
* **removeVolumes** (`docker.removeVolumes`) If given will remove any
  volumes associated to the container as well. This option will be ignored
  if either `keepContainer` or `keepRunning` are true.

Example: 

    $ mvn -Ddocker.keepRunning clean install

#### `docker:push`

This goals uploads images to the registry which have a `<build>`
configuration section. The images to push can be restricted with with
the global option `image` (see
[Global Configuration](#global-configuration) for details). The
registry to push is by default `registry.hub.docker.io` but can be
specified as part of the images's `name` name the Docker
way. E.g. `docker.test.org:5000/data:1.5` will push the image `data`
with tag `1.5` to the registry `docker.test.org` at port
`5000`. Security information (i.e. user and password) can be specified
in multiple ways as described in an extra [section](#authentication).

#### `docker:remove`

This goal can be used to clean up images and containers. By default
all so called *data images* are removed with its containers. A data
image is an image without a run configuration. This can be tuned by
providing the properties `removeAll` which indicates to remove all
images managed by this build. As with the other goals, the
configuration `image` can be used to tune the images to remove. All
containers belonging to the images are removed as well.

Considering three images 'db','tomcat' and 'data' where 'data' is the
only data images this example demonstrates the effect of this goal: 

* `mvn docker:remove` will remove 'data'
* `mvn -Ddocker.removeAll docker:remove` will remove all three images
* `mvn -Ddocker.image=data,tomcat docker:remove` will remove 'data'
* `mvn -Ddocker.image=data,tomcat -Ddocker.removeAll docker:remove`
  will remove 'data' and 'tomcat' 

#### `docker:logs`

With this goal it is possible to print out the logs of containers
started from images configured in this plugin. By default only the
latest container started is printed, but this can be changed with a
property. The format of the log output is influenced by run
configuration of the configured images. The following system
properties can the behaviour of this goal:

* **docker.logAll** if set to `true` the logs of all containers
  created from images configured for this plugin are printed. The
  container id is then prefixed before every log line. These images
  can contain many containers which are already stopped. It is
  probably a better idea to use `docker logs` diretly from the command
  line. 
* **docker.follow** if given will wait for subsequent log output until
  CRTL-C is pressed. This is similar to the behaviour of `docker logs
  -f` (or `tail -f`).
* **docker.image** can be used to restrict the set of images for which
  log should be fetched. This can be a comma separated list of image
  or alias names. 
* **docker.logDate** specifies the log date to use. See
  "[Log configuration](#log-configuration)" above for the available
  formats. 

Example:

````
$ mvn docker:logs -Ddocker.follow -Ddocker.logDate=DEFAULT
````
### External Configuration

For special configuration needs there is the possibility to get the
runtime and build configuration from places outside the plugin's
configuration. This is done with the help of `<external>`
configuration sections which at least has a `<type>` subelement. This
`<type>` element selects a specific so called "handler" which is
responsible for creating the full image configuration. A handler can
decided to use the `<run>` and `<build>` configuration which could
be provided in addition to this `<external>` section or it can decide
to completely ignore any extra configuration option. 

A handler can also decide to expand this single image configuration to
a list of image configurations. The image configurations resulting
from such a external configuration are added to the *regular*
`<image>` configurations without an `<external>` section.

The available handlers are described in the following. 

#### Property based Configuration

For simple needs the image configuration can be completely defined via
Maven properties which are defined outside of this plugin's
configuration. Such a property based configuration can be selected
with an `<type>` of `props`. As extra configuration a prefix for the
properties can be defined which by default is `docker`.

Example:

```xml
<image>
  <external>
     <type>props</type>
     <prefix>docker</prefix> <!-- this is the default -->
  </external>
</image>
```

Given this example configuration a single image configuration is build
up from the following properties, which correspond to corresponding
values in the `<build>` and `<run>` sections.

* **docker.alias** Alias name
* **docker.assembly.baseDir** Directory name for the exported artifacts as
  described in an assembly (which is `/maven` by default).
* **docker.assembly.descriptor** Path to the assembly descriptor when
  building an image
* **docker.assembly.descriptorRef** Name of a predefined assembly to
  use. 
* **docker.assembly.exportBaseDir** If `true` export base directory
* **docker.assembly.ignorePermissions** If set to `true` existing file permissions are ignored
  when creating the assembly archive
* **docker.assembly.dockerFileDir** specifies a directory containing an external Dockerfile
  that will be used to create the image
* **docker.bind.idx** Sets a list of paths to bind/expose in the container
* **docker.capAdd.idx** List of kernel capabilities to add to the container
* **docker.capDrop.idx** List of kernel capabilities to remove from the container
* **docker.command** Command to execute. This is used both when
  running a container and as default command when creating an image.
* **docker.domainname** Container domain name
* **docker.dns.idx** List of dns servers to use
* **docker.dnsSearch.idx** List of dns search domains
* **docker.entrypoint** Container entry point
* **docker.env.VARIABLE** Sets an environment
  variable. E.g. `<docker.env.JAVA_OPTS>-Xmx512m</docker.env.JAVA_OPTS>`
  sets the environment variable `JAVA_OPTS`. Multiple such entries can
  be provided. This environment is used both for building images and
  running containers. The value cannot be empty but can contain Maven property names which are
  resolved before the Dockerfile is created
* **docker.envPropertyFile** specifies the path to a property file whose properties are 
  used as environment variables. The environment variables takes precedence over any other environment
  variables specified.
* **docker.extraHosts.idx** List of `host:ip` to add to `/etc/hosts`
* **docker.from** Base image for building an image
* **docker.hostname** Container hostname
* **docker.links.idx** defines a list of links to other containers when
  starting a container. *idx* can be any suffix which is not use
  except when *idx* is numeric it specifies the order within the
  list (i.e. the list contains first a entries with numeric
  indexes sorted and the all non-numeric indexes in arbitrary order).
  For example `<docker.links.1>db</docker.links.1>` specifies a link
  to the image with alias 'db'.
* **docker.memory** Container memory (in bytes)
* **docker.memorySwap** Total memory (swap + memory) `-1` to disable swap
* **docker.name** Image name
* **docker.namingStrategy** Container naming (either `none` or `alias`)
* **docker.portPropertyFile** specifies a path to a port mapping used
  when starting a container.
* **docker.ports.idx** Sets a port mapping. For example
  `<docker.ports.1>jolokia.ports:8080<docker.ports.1>` maps
  the container port 8080 dynamically to a host port and assigns this
  host port to the Maven property `${jolokia.port}`. See
  [Port mapping](#port-mapping) for possible mapping options. When creating images images only
  the right most port is used for exposing the port. For providing multiple port mappings,
  the index should be count up. 
* **docker.registry** Registry to use for pushing images.
* **docker.restartPolicy.name** Container restart policy
* **docker.restartPolicy.retry** Max restrart retries if `on-failure` used
* **docker.user** Container user
* **docker.volumes.idx** defines a list of volumes to expose when building an image
* **docker.tags.idx** defines a list of tags to apply to a built image
* **docker.maintainer** defines the maintainer's email as used when building an image
* **docker.volumesFrom.idx** defines a list of image aliases from which
  the volumes should be mounted of the container. The list semantics
  is the same as for links (see above). For examples
  `<docker.volumesFrom.1>data</docker.volumesFrom.1>` will mount all
  volumes exported by the `data` image.
* **docker.wait.url** URL to wait for during startup of a container
* **docker.wait.time** Amount of time to wait during startup of a
    container (in ms)
* **docker.wait.log** Wait for a log output to appear.
* **docker.wait.shutdown** Time in milliseconds to wait between stopping a container and removing it.
* **docker.workingDir** Working dir for commands to run in

Any other `<run>` or `<build>` sections are ignored when this handler
is used. Multiple property configuration handlers can be used if they
use different prefixes. As stated above the environment and ports
configuration are both used for running container and building
images. If you need a separate configuration you should use explicit
run and build configuration sections.

Example:

```xml
<properties>
  <docker.name>jolokia/demo</docker.name>
  <docker.alias>service</docker.alias>
  <docker.from>consol/tomcat:7.0</docker.from>
  <docker.assembly.descriptor>src/main/docker-assembly.xml</docker.assembly.descriptor>
  <docker.env.CATALINA_OPTS>-Xmx32m</docker.env.CATALINA_OPTS>
  <docker.ports.jolokia.port>8080</docker.ports.jolokia.port>
  <docker.wait.url>http://localhost:${jolokia.port}/jolokia</docker.wait.url>
</properties>

<build>
  <plugins>
    <plugin>
      <groupId>org.jolokia</groupId>
      <artifactId>docker-maven-plugin</artifactId>
      <configuration>
        <images>
          <image>
            <external>
              <type>props</type>
              <prefix>docker</prefix>
            </external>
          </image>
        </images>
      </configuration>
    </plugin>
  </plugins>
</build>
```

### Registry handling

Docker uses registries to store images. The registry is typically
specified as part of the name. I.e. if the first part (everything
before the first `/`) contains a dot (`.`) or colon (`:`) this part is
interpreted as an address (an optionally port) of a remote
registry. This registry (or the default `index.docker.io` if no
registry is given) is used during push and pull operations. This
plugin follows the same semantics, so if an image name is specified
with a registry part, this registry is contacted. Authentication is
explained in the next [section](#authentication). 

There are some situations however where you want to have more
flexibility for specifying a remote registry. This might be, because
you do not want to hard code a registry within the `pom.xml` but
provide it from the outside with an environment variable or a system
property. 

This plugin supports various ways for specifying a registry:

* If the image name contains a registry part, this registry is used
  unconditionally and can not be overwritten from the outside.
* If an image name doesn't contain a registry, then by default the
  default Docker registry `index.docker.io` is used for push and pull
  operations. But this can be overwritten through various means:
  - If the `<image>` configuration contains a `<registry>` subelement
    this registry is used.
  - Otherwise, a global configuration element `<registry>` is
    evaluated which can be also provided as system property via
    `-Ddocker.registry`. 
  - Finally a environment variable `DOCKER_REGISTRY` is looked up for
    detecting a registry.
    
Example:

```xml
<configuration>
  <registry>docker.jolokia.org:443</registry>
  <images>
    <image>
      <!-- Without an explicit registry ... -->
      <name>jolokia/jolokia-java</name>
      <!-- ... hence use this registry -->
      <registry>docker.consol.de</registry>
      ....
    <image>
    <image>
      <name>postgresql</name>
      <!-- No registry in the name, hence use the globally 
           configured docker.jolokia.org:443 as registry -->
      ....
    </image>
    <image>
      <!-- Explicitely specified always wins -->
      <name>docker.example.com:5000/another/server</name>
    </image>
  </images>
</configuration>
```

There is some special behaviour when using an externally provided
registry like described above:

* When *pulling*, the image pulled will be also tagged with a repository
  name **without** registry. The reasoning behind this is that this
  image then can be referenced also by the configuration when the
  registry is not specified anymore explicitely.
* When *pushing* a local image, temporarily an tag including the
  registry is added and removed after the push. This is required
  because Docker an only push registry-named images.

### Authentication

When pulling (via the `autoPull` mode of `docker:start`) or pushing image, it
might be necessary to authenticate against a Docker registry.

There are three different ways for providing credentials:
 
* Using a `<authConfig>` section in the plugin configuration with
  `<username>` and `<password>` elements. 
* Providing system properties `docker.username` and `docker.password`
  from the outside 
* Using a `<server>` configuration in the the `~/.m2/settings.xml`
  settings 

Using the username and password directly in the `pom.xml` is not
recommended since this is widely visible. This is most easiest and
transparent way, though. Using an `<authConfig>` is straight forward:

````xml
<plugin>
  <configuration>
     <image>consol/tomcat-7.0</image>
     ...
     <authConfig>
         <username>jolokia</username>
         <password>s!cr!t</password>
     </authConfig>
  </configuration>
</plugin>
````

The system property provided credentials are a good compromise when
using CI servers like Jenkins. You simply provide the credentials from
the outside:

	mvn -Ddocker.username=jolokia -Ddocker.password=s!cr!t docker:push

The most secure and also the most *mavenish* way is to add a server to
the Maven settings file `~/.m2/settings.xml`:

```xml
<servers>
  <server>
    <id>registry.hub.docker.io</id>
    <username>jolokia</username>
    <password>s!cr!t</password>
  </server>
  ....
</servers>
```

The server id must specify the registry to push to/pull from, which by
default is central index `registry.hub.docker.io`. Here you should add
you docker.io account for your repositories.

#### Password encryption

Regardless which mode you choose you can encrypt password as described
in the
[Maven documentation](http://maven.apache.org/guides/mini/guide-encryption.html). Assuming
that you have setup a *master password* in
`~/.m2/security-settings.xml` you can create easily encrypted
passwords:

```bash
$ mvn --encrypt-password
Password:
{QJ6wvuEfacMHklqsmrtrn1/ClOLqLm8hB7yUL23KOKo=}
```

This password then can be used in `authConfig`, `docker.password`
and/or the `<server>` setting configuration. However, putting an
encrypted password into `authConfig` in the `pom.xml` doesn't make
much sense, since this password is encrypted with an individual master
password.
