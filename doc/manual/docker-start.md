### docker:start

Creates and starts docker containers. This goals evaluates
the configuration's `<run>` section of all given (and enabled images). In order to switch on
globally the logs **showLogs** can be used as global configuration (i.e. outside of `<images>`).
If set it will print out all standard output and standard error messages for all containers started.
As value the images for which logs should be shown can be given as a comma separated list. This is probably most
useful when used from the command line as system property `docker.showLogs`.

Also you can specify `docker.follow` as system property so that the `docker:start` will never return but block until
CTRL-C is pressed. That similar to the option `-i` for `docker run`. This will automatically switch on `showLogs` so that
 you can see what is happening within the container. Also, after stopping with CTRL-C, the container is stopped (but
 not removed so that you can make postmortem analysis).

By default container specific properties are exposed as Maven properties. These properties have the format
`docker.container.`*alias*`.`*prop* where *alias* is the name of the container (see below) and *prop* is one of the following container properties:

* **ip** : The internal IP address of the container.
* **id** : The container id

For example the Maven property `docker.container.tomcat.ip` would hold the Docker internal IP for a container with
an alias "tomcat". You can set the global configuration **exposeContainerInfo** to an empty string to not expose container
information that way or to a string for an other prefix than `docker.container`.

#### <run> Configuration

The `<run>` configuration section knows the following sub elements:

* **capAdd** a list of `add` elements to specify kernel parameters to add to
  the container.
* **capDrop**  a list of `drop` elements to specify kernel parameters to remove
  from the container.
* **cmd** is a command which should be executed at the end of the
  container's startup. If not given, the image's default command is
  used. See [Startup Arguments](docker-build.html#startup-arguments) for details.
* **domainname**  domain name for the container
* **dns** list of `host` elements specifying dns servers for the container to use
* **dnsSearch** list of `host` elements specifying dns search domains
* **entrypoint** set the entry point for the container. See [Startup Arguments](docker-build.html#startup-arguments) for details.
* **env** can contain environment variables as subelements which are
  set during startup of the container. They are specified in the
  typical maven property format as described [Setting Environment Variables and Labels](#setting-environment-variables-and-labels).
* **labels** specifies one or more labels which should be attached to the container. They are specified in the
  typical maven property format as described in [Setting Environment Variables and Labels](#setting-environment-variables-and-labels).
* **envPropertyFile** can be a path to a property file holding environment variables. If given, the variables
  specified in this property file overrides the environment variables specified in the configuration.
* **extraHosts** list of `host` elements in the form `host:ip` to add to the container's `/etc/hosts` file.
  Additionally, you may specify a `host` element in the form `host:host` to have the right side host ip address resolved
  at container startup.
* **hostname** desired hostname for the container
* **links** declares how containers are linked together see
  description on [Container Linking](#container-linking).
* **log** specifies the log configuration for whether and how log
  messages from the running containers should be printed. This also can configure the
  [log driver](https://docs.docker.com/engine/admin/logging/overview/) to use. See
  [Log Configuration](#log-configuration) for a detailed description of this configuration
  section.
* **memory** memory limit in bytes
* **memorySwap** total memory usage (memory + swap); use -1 to disable swap.
* **shmSize** size of `/dev/shm` in bytes.
* **namingStrategy** sets the name of the container
  - `none` : uses randomly assigned names from docker (default)
  - `alias` : uses the `alias` specified in the `image` configuration. An error is thrown, if a container already exists
    with this name.
* **net** set the network mode for the container:
  - `bridge` : Bridged mode with the default Docker bridge (default)
  - `host` : Share the Docker host network interfaces
  - `container:<alias or name>` : Connect to the network of the specified container
  - `<custom network>` : Use the specified custom network which must be created before with `docker network create`.
    Available for Docker 1.9 and newer. For more about the networking options please refer to the [Docker documentation](https://docs.docker.com/engine/userguide/networking/work-with-networks/).
* **portPropertyFile**, if given, specifies a file into which the
  mapped properties should be written to. The format of this file and
  its purpose are also described in [Port mapping](#port-mapping)
* **ports** declares how container exposed ports should be
  mapped. This is described below in an extra
  section [Port mapping](#port-mapping).
* **privileged** give container full access to host (`true|false`)
* **restartPolicy** specifies the container restart policy, see
  [Container Restart Policy](#container-restart-policy)
* **user** user used inside the container
* **skip** disable creating and starting of the container. This option is best used together with a configuration option.
* **volumes** for bind configurtion of host directories and from other containers. See [Volume binding](#volume-binding)
  for details.
* **wait** specifies condition which must be fulfilled for the startup
  to complete. See [Wait during Startup and Shutdown](#wait-during-startup-and-shutdown) which subelements are
  available and how they can be specified.
* **workingDir** working dir for commands to run in

Example:

```xml
<run>
  <env>
    <CATALINA_OPTS>-Xmx32m</CATALINA_OPTS>
    <JOLOKIA_OFF/>
  </env>
  <labels>
    <environment>development</environment>
    <version>${project.version}</version>
  </labels>
  <ports>
    <port>jolokia.port:8080</port>
  </ports>
  <links>
    <link>db</db>
  </links>
  <wait>
    <http>
      <url>http://localhost:${jolokia.port}/jolokia</url>
    </http>
    <time>10000</time>
  </wait>
  <log>
    <prefix>DEMO</prefix>
    <date>ISO8601</date>
    <color>blue</color>
  </log>
  <cmd>java -jar /maven/docker-demo.jar</cmd>
</run>
```

##### Setting environment variables and labels

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

Labels can be set inline the same way as environment variables:

```xml
<labels>
   <com.example.label-with-value>foo</com.example.label-with-value>
   <version>${project.version}</version>
   <artifactId>${project.artifactId}</artifactId>
</labels>
```

##### Port Mapping

The `<ports>` configuration contains a list of port mappings. Each
mapping has multiple parts, each separate by a colon. This is
equivalent to the port mapping when using the Docker CLI with option
`-p`.


A `port` stanza may take one of the following forms:

* **18080:8080** : A tuple consisting of two numeric values separated by a `:`. This
  form will result in an explicit mapping between the docker host and the corresponding
  port inside the container. In the above example, port 18080 would be exposed on the
  docker host and mapped to port 8080 in the running container.
* ***host.port*:80** A tuple consisting of a string and a numeric value separated by a
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
* **bindTo:*host.port*:80** A tuple consisting of two strings and a numeric value separated
  by a `:`. In this form, `bindTo` is an ip address on the host the container should bind to.
  As a convenience, a hostname pointing to the docker host may also
  be specified. The container will fail to start if the hostname can not be
  resolved.
* **+host.ip:*host.port*:80** A tuple consisting of two strings and a numeric value separated
  by a `:`. In this form, the host ip of the container will be placed into a Maven property name `host.ip`.
  If docker reports that value to be `0.0.0.0`, the value of `docker.host.address` will
  be substituted instead. In the event you want to use this form and have the container bind
  to a specific hostname/ip address, you can declare a Maven property of the same name (`host.ip` in this example)
  containing the value to use. `host:port` works in the same way as described above.

The following are examples of valid configuration entries:

```xml
<properties>
  <bind.host.ip>1.2.3.4</bind.host.ip>
  <bind.host.name>some.host.pvt</bind.host.name>
<properties>

...

<ports>
  <port>18080:8080</port>
  <port>host.port:80</port>
  <port>127.0.0.1:80:80</port>
  <port>localhost:host.port:80</port>
  <port>+container.ip.property:host.port:5678</port>
  <port>+bind.host.ip:host.port:5678</port>
  <port>+bind.host.name:5678:5678</port>
<ports>
```

Another useful configuration option is `portPropertyFile` which can be used to
to write out the container's host ip and any dynamic ports that have been
resolved. The keys of this property file are the property names defined in the
port mapping configuration and their values those of the corresponding
docker attributes.

This property file might be useful with tests or with other maven plugins that will be unable
to use the resolved properties because they can only be updated after the container has started
and plugins resolve their properties in an earlier lifecycle phase.

If you don't need to write out such a property file and thus don't need to preserve the property names,
you can use normal maven properties as well. E.g. `${host.var}:${port.var}:8080` instead of
`+host.var:port.var:8080`.

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

If you wish to link to existing containers not managed by the plugin, you may do so by specifying the container name
obtained via `docker ps` in the configuration.

Please note that the link behaviour also depends on the network mode selected. Links as described
are referred to by Docker as *legacy links* and might vanish in the future. For custom networks no
environments variables are set and links create merely network aliases for the linked container.

For a more detailed documentation for the new link handling please refer to the [Docker network documentation](https://docs.docker.com/engine/userguide/networking/work-with-networks/#linking-containers-in-user-defined-networks)

##### Volume binding

A container can bind (or "mount") volumes from various source when starting up: Either from a directory of
the host system or from another container which exports one or more directories. The mount configuration is
specified within a `<volumes>` section of the run configuration. It can contain the following sub elements:

* **from** can contain a list of `<image>` elements which specify
  image names or aliases of containers whose volumes should be imported.
* **bind** can contain a list of `<volume>` specifications (or *host mounts*). Use `/path` to create and
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
should even work for boot2docker and docker-machine:

````xml
<volumes>
  <bind>
    <volume>${project.build.directory}/${project.artifactId}-${project.version}:/usr/local/tomcat/webapps/${project.name}</volume>
    <volume>${project.basedir}/data:/data</volume>
  </bind>
</volumes>
````

If you wish to mount volumes from an existing container not managed by the plugin, you may do by specifying the container name
obtained via `docker ps` in the configuration.

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

* **http** specifies an HTTP ping check which periodically polls an URL. It knows the following sub-elements:
  - **url** holds an URL and is mandatory
  - **method** Optional HTTP method to use.
  - **status** Status code which if returned is considered to be a successful ping. This code can be given either as
    a single number (200) or as a range (200..399). The default is `200..399`
* **log** is a regular expression which is applied against the log
  output of an container and blocks until the pattern is matched.
* **time** is the time in milliseconds to block.
* **kill** is the time in milliseconds between sending SIGTERM and SIGKILL when stopping a container. Since docker itself
  uses second granularity, you should use at least 1000 milliseconds.
* **shutdown** is the time to wait in milliseconds between stopping a container
  and removing it. This might be helpful in situation where a Docker croaks with an
  error when trying to remove a container to fast after it has been stopped.
* **exec** Specifies commands to execute during specified lifecycle of the container. It knows the following sub-elements:
  - **postStart** Command to run after the above wait criteria has been met
  - **preStop** Command to run before the container is stopped.
* **tcp** specifies TCP port check which periodically polls given tcp ports. It knows the following sub-elements:
  - **host** is the hostname or the IP address. It defaults to `${docker.host.address}`.
  - **ports** is a list of TCP ports to check.

As soon as one condition is met the build continues. If you add a
`<time>` constraint this works more or less as a timeout for other
conditions. The build will abort if you wait on an url or log output and reach the timeout.
If only a `<time>` is specified, the build will wait that amount of milliseconds and then continues.

Example:

````xml
<wait>
  <http>
    <url>http://localhost:${host.port}</url>
    <method>GET</method>
    <status>200..399</status>
  </http>
  <time>10000</time>
  <kill>1000</kill>
  <shutdown>500</shutdown>
  <exec>
     <postStart>/opt/init_db.sh</postStart>
     <preStop>/opt/notify_end.sh</preStop>
  </exec>
  <tcp>
     <host>192.168.99.100</host>
     <ports>
        <port>3306</port>
        <port>9999</port>
     </ports>
  </tcp>
</wait>
````

This setup will wait for the given URL to be reachable but ten seconds
at most. Additionally, it will be waited for the TCP ports 3306 and 9999.
Also, when stopping the container after an integration tests, the
build wait for 500 ms before it tries to remove the container (if not `keepContainer`
or `keepRunning` is used). You can use maven properties in each
condition, too. In the example, the `${host.port}` property is
probably set before within a port mapping section.

The property `${docker.host.address}` is set implicitly to the address of the Docker host. This host will
be taken from the `docker.host` configuration if HTTP or HTTPS is used. If a Unix socket is used for communication
with the docker daemon, then `localhost` is assumed. You can override this property always by setting this Maven
property explicitly.

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
* **file** Path to a file to which the log output is written. This file is overwritten
  for every run and colors are switched off.
* **driver** Section which can specify a dedicated log driver to use. A `<name>` tag within this section depicts
  the logging driver with the options specified in `<opts>`. See the example below for how to use this.

Example (values can be case insensitive, too) :

````xml
<log>
  <prefix>TC</prefix>
  <date>default</date>
  <color>cyan</color>
</log>
````

The following example switches on the `gelf` [logging driver](https://docs.docker.com/engine/admin/logging/overview/) .
This is equivalent to the options `--log-driver=gelf --log-opt gelf-address=udp://localhost:12201` when using `docker run`.

````xml
<log>
  ...
  <driver>
    <name>gelf</name>
    <opts>
   	  <gelf-address>udp://localhost:12201</gelf-address>
    </opts>
  </driver>
</log>
````
