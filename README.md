# docker-maven-plugin

[![endorse](http://api.coderwall.com/rhuss/endorsecount.png)](http://coderwall.com/rhuss)
[![Build Status](https://secure.travis-ci.org/rhuss/docker-maven-plugin.png)](http://travis-ci.org/rhuss/docker-maven-plugin)
[![Flattr](http://api.flattr.com/button/flattr-badge-large.png)](http://flattr.com/thing/73919/Jolokia-JMX-on-Capsaicin)

This is a Maven plugin for managing Docker images and containers from your builds.

> *This document describes the configuration syntax for version >=
> 0.10.0. For older version (i.e. 0.9.x) please refer to the old
> [documentation](README-0.9.x.md). Migration to the new syntax is not
> difficult and described [separately](UPGRADE-FROM-0.9.x.md)*

* [Introduction](#introduction)
* [User Manual](#user-manual)
  - [Installation](#installation)
  - [Global Configuration](#global-configuration)
  - [Image Configuration](#image-configuration)
  - [Maven Goals](#maven-goals)
    * [`docker:start`](#dockerstart)
    * [`docker:stop`](#dockerstop)
    * [`docker:build`](#dockerbuild)
    * [`docker:push`](#dockerpush)
    * [`docker:remove`](#dockerremove)
  - [Authentication](#authentication)
* [Examples](#examples)

## Introduction 

It focuses on two major aspects:

* **Building** and pushing Docker images which contains build artifacts
* Starting and stopping Docker container for **integration testing** and
  development

Docker *images* are the central entity which can be configured. 
Containers on the other hand are more or less volatil. They are
created and destroyed on the fly from the configured images and are
completely managed internally.

### Building docker images

One purpose of this plugin is to create docker images holding the
actual application. This can be done with the `docker:build` goal.  It
is easy to include build artifacts and their dependencies into an
image. Therefor this plugin uses the
[assembly descriptor format](http://maven.apache.org/plugins/maven-assembly-plugin/assembly.html)
from the
[maven-assembly-plugin](http://maven.apache.org/plugins/maven-assembly-plugin/)
for specifying the content which will be added below a directory in
the image (`/maven` by default). Image which are build with this
plugin can be also pushed to public or private registries with
`docker:push`.

### Running containers

With this plugin it is possible to run completely isolated integration
tests so you don't need to take care of shared resources. Ports can be
mapped dynamically and made available as Maven properties to you
integration test code. 

Multiple containers can be managed at once which can be linked
together or share data via volumes. Containers are created and started
with the `docker:start` goal and stopped and destroyed with the
`docker:stop` goal. For integrations tests both goals are typically
bound to the the `pre-integration-test` and `post-integration-test`,
respectively. It is recommended to use the `maven-failsafe-plugin` for
integration testing in order to stop the docker container even when
the tests are failing.

For proper isolation container exposed ports can be dynamically and
flexibly mapped to local host ports. It is easy to specify a Maven
property which will be filled in with a dynamically assigned port
after a container has been started and which can then be used as
parameter for integration tests to connect to the application.

### Configuration

You can use a single configuration for all goals (in fact, that's the
recommended way). The configuration contains a general part and a list
of image specific configuration, one for each image. 

The general part contains global configuration like the Docker URL or
the path to the SSL certificates for communication with the Docker Host.

Then, each image configuration has three parts:

* A general part containing the image's name and alias.
* A `<build>` configuration specifying how images are build.
* A `<run>` configuration telling how container should be created and started.

Either `<build>` or `<run>` can be also omitted.

Let's have a look at a plugin configuration example:

````xml
<configuration>
  <images>
    <image>
      <alias>service</alias>
      <name>jolokia/docker-demo:${project.version}</name>

      <build>
         <from>java:8</from>
         <assemblyDescriptor>docker-assembly.xml</assemblyDescriptor>
         <ports>
           <port>8080</port>
         </ports>
         <command>java -jar /maven/service.jar</command>
      </build>

      <run>
         <ports>
           <port>tomcat.port:8080</port>
         </ports>
         <wait>
           <url>http://localhost:${tomcat.port}/access</url>
           <time>10000</time>
         </wait>
         <links>
           <link>database:db</link>
         </links>
       </run>
    </image>

    <image>
      <alias>database</alias>
      <name>postgres:9</name>
      <run>
        <wait>
          <log>database system is ready to accept connections</log>
          <time>20000</time>
        </wait>
      </run>
    </image>
  </images>
</configuration>
````

Here two images are specified. One is the official PostgreSQL 9 image from
Docker Hub, which internally can be referenced as "*database*" (`<alias>`). It
only has a `<run>` section which declares that the startup should wait
until the given text pattern is matched in the log output. Next is a
"*service*" image, which is specified in its `<build>` section. It
creates an image which has artifacts and dependencies in the
`/maven` directory (and which are specified with an assembly
descriptor). Additionally it specifies the startup command for the
container which in this example fires up a microservices from a jar
file just copied over via the assmebly descriptor. Also it exposes
port 8080. In the `<run>` section this port is dynamically mapped to a
port out of the Docker range 49000 ... 49900 and then assigned to the
Maven property `${jolokia.port}`. This property could be used for an
integration test to access this micro service. An important part is
the `<links>` section which tells that the image aliased "*database*" is
linked into the "*service*" container, which can access the internal
ports in the usual Docker way (via environments variables prefixed
with `DB_`). 

Images can be specified in any order, the plugin will take care of the
proper startup order (and will bail out in case of circulara
dependencies). 

### Other highlights

Some other highlights in random order (and not complete):

* Auto pulling of images (with progress indicator)
* Waiting for a container to startup based on time, the reachability
  of an URL or a pattern in the log output
* Support for SSL authentication (since Docker 1.3)
* Specification of encrypted registry passwords for push and pull in
  `~/.m2/settings.xml` (i.e. outside the `pom.xml`)
* Color output ;-)

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
  <version>0.10.4</version>

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
* **useColor** (`docker.useColor`)
  If set to `true` the log output of this plugin will be colored. By
  default the output is colored if the build is running with a TTY,
  without color otherwise.
* **skip** (`docker.skip`)
  With this parameter the execution of this plugin can be skipped
  completely. 
* **autoPull** (`docker.autoPull`)
  By default external images (base image for building or images to
  start) are downloaded automatically. With this options this can be
  switched off by setting this value to `false`.
* **authConfig** holds the authencation information when pulling from
  or pushing to Docker registry. There is a dedicated
  [section](#authentication) for how doing security.

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
specified for each image within the `<images>` element of the
configuration with one `<image>` element per image to use. 

The `<image>` element can contain the following sub elements:

* **name** : Each `<image>` configuration has a mandatory, unique docker
  repository *name*. This can include registry and tag parts, too. For
  definition of the repository name please refer to the
  [Docker documentation]()
* **alias** is a shortcut name for an image which can be used for
  identifying the image within this configuration. This is used when
  linking images together or for specifying it with the global
  **image** configuration.
* **build** is a complex element which contains all the configuration
  aspects when doing a `docker:build` or `docker:push`. This element
  can be omitted if the image is only pulled from a registry e.g. as
  support for integration tests like database images.
* **run** contains subelement which describe how containers should be
  created and run when `docker:start` or `docker:stop` is called. If
  this image is only used a *data container* for exporting artefacts
  via volumes this section can be missing.

Either `<build>` or `<run>` must be present. They are explained in
detail in the corresponding goal sections.

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
| [`docker:start`](#dockerstart)   | Create and start containers          |
| [`docker:stop`](#dockerstop)     | Stop and destroy containers          |
| [`docker:build`](#dockerbuild)   | Build images                         |
| [`docker:push`](#dockerpush)     | Push images to a registry            |
| [`docker:remove`](#dockerremove) | Remove images from local docker host |

Note that all goals are orthogonal to each other. For example in order
to start a container for your application you typically have to build
its image before. `docker:start` does **not** imply building the image
so you should use it then in combination with `docker:build`.  

#### `docker:start`

Creates and starts docker containers. This goals evaluates
the configuration's `<run>` section of all given (and enabled images)

The `<run>` configuration knows the following sub elements:

* **command** is a command which should be executed at the end of the
  container's startup. If not given, the image's default command is
  used. 
* **env** can contain environment variables as subelements which are
  set during startup of the container. The are specified in the
  typical maven property format as described [below](#setting-environment-variables).
* **ports** declares how container exposed ports should be
  mapped. This is described below in an extra
  [section](#port-mapping). 
* **portPropertyFile**, if given, specifies a file into which the
  mapped properties should be written to. The format of this file and
  its purpose are also described [below](#port-mapping)
* **volumes** can contain a list mit `<from>` elements which specify
  image names or aliases from which volumes should be imported.
* **wait** specifies condition which must be fulfilled for the startup
  to complete. See [below](#wait-during-startup) which subelements are
  available and how they can be specified.

Example:

````xml
<run>
  <volumes>
    <from>jolokia/docker-demo</from>
  </volumes>
  <env>
    <CATALINA_OPTS>-Xmx32m</CATALINA_OPTS>
    <JOLOKIA_OFF/>
  </env>
  <ports>
    <port>jolokia.port:8080</port>
  </ports>
  <wait>
    <url>http://localhost:${jolokia.port}/jolokia</url>
    <time>10000</time>
  </wait>
  <command>java -jar /maven/docker-demo.jar</command>
</run>
````

##### Setting environment variables

When creating a container one or more environment variables can be set via configuration with the `env` parameter

```xml
<env>
  <JAVA_HOME>/opt/jdk8</JAVA_HOME>
  <CATALINA_OPTS>-Djava.security.egd=file:/dev/./urandom</CATALINA_OPTS>
</env>
```

If you put this configuration into profiles you can easily create various test variants with a single image (e.g. by
switching the JDK or whatever).

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
  range 49000 ... 49900 and assigned to the property which may then be
  used later in the same POM file. If the property exists and has a
  numeric value, that value will be used as the exposed port on the
  docker host as in the previous form. In the above example, the
  docker service will elect a new port and assign the value to the
  property `host.port` which may then later be used in a property
  expression similar to `<value>${host.port}</value>`. This can be
  used to pin a port from the outside when doing some initial testing
  similar to `mvn -Dhost.port=10080 docker:start`

Another useful configuration option is `portPropertyFile` with which a
file can be specified to which the real port mapping is written after
all dynamic ports has been resolved. The keys of this property file
are the variable names, the values are the dynamically assgined host
ports. This property file might be useful together with other maven
plugins which already resolved their maven variables earlier in the
lifecycle than this plugin so that the port variables might not be
available to them.

##### Wait during startup

While starting a container is it possible to block the execution until
some condition is met. These conditions can be specified within a
`<wait>` section which knows multiple sub-elements

* **url** is an URL which is polled periodically until it returns a
  HTTP 200 status code.
* **log** is a regular expression which is applied against the log
  output of an container and blocks until the pattern is matched.
* **time** is the time in milliseconds to block.

As soon as one condition is met the build continues. If you add a
`<time>` constraint this works more or less as a timeout for other
conditions. 

Example:

````xml
<wait>
  <url>http://localhost:${host.port}</url>
  <time>10000</time>
</wait>
```` 

This setup will wait for the given URL to be reachable but ten seconds
at most. You can use maven properties in each
condition, too. In the example, the `${host.port}` propertu is
probably set before within a port mapping section. 

### `docker:stop`

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

Example: 

    $ mvn -Ddocker.keepRuning clean install

### `docker:build`

This goal will build all images which have a `<build>` configuration
section, or, if the global configuration `image` is set, only those
images contained in this variable will be build. 

All build relevant configuration is contained in the `<build>` section
of an image configuration. The available subelements are

* **from** specifies the base image which should be used for this
  image. If not given this default to `busybox:latest` and is suitable
  for a pure data image.
* **exportDir** depicts the directory under which the files and
  artefacts contained in the assembly will be copied within the
  container. By default this is `/maven`.
* **assemblyDescriptor** is a reference to an assembly descriptor as
  described in the section [Docker Assembly](#docker-assembly) below. 
* **assemblyDescriptorRef** is an alias to a predefined assembly
  descriptor. The available aliases are also described in the
  [Docker Assembly](#docker-assembly) section.
* **ports** describes the exports ports. It contains a list of
  `<port>` elements, one for each port to expose.
* **env** hold environments as described in
  [Setting Environment Variables](#setting-environment-variables). 
* **command** is the command to execute by default (i.e. if no command
  is provided when a container for this image is started).

From this configuration this Plugin creates an in-memory Dockerfile,
copies over the assembled files and calls the Docker daemon via its
remote API. In a future version you will be able to specify
alternatively an own Dockerfile (possibly containing maven properties)
for better customization.

Here's an example:

````xml
<build>
  <from>java:8u40</from>
  <assemblyDescriptor>src/main/assembly.xml</assemblyDescriptor>
  <ports>
    <port>8080</port>
  </ports>
  <exportDir>/opt/demo</exportDir>
  <command>java /opt/demo/server.jar</command>
</build>
````

#### Docker Assembly

With using the `assemblyDescriptor` or `assemblyDescriptorRef` option
it is possible to bring local files, artifacts and dependencies into
the running Docker container. An `assemblyDescriptor` points to a file
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

Alternatively `assemblyDescriptorRef` can be used with the name of a
predefined assembly descriptor. The followign symbolic names can be
used for `assemblyDescritproRef`: 

* **artifact-with-dependencies** will copy your project's artifact and
  all its dependencies 
* **artifact** will copy only the project's artifact but no
  dependencies. 
* **project** will copy over the whole Maven project but with out
  `target/` directory. 
* **rootWar** will copy the artifact as `ROOT.war` to the exposed
  directory. I.e. Tomcat will then deploy the war under the root
  context. 

All declared files end up in the configured `exportDir` (or `/maven`
by default) in the created image.

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

### `docker:push`

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

### Authentication

When pulling (via the `autoPull` mode of `docker:start` and
`docker:push`) or pushing image, it might be necessary to authenticate
against a Docker registry. 

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

## Examples

This plugin comes with some commented examples in the `samples/` directory:

### Jolokia Demo

[data-jolokia-demo](https://github.com/rhuss/docker-maven-plugin/tree/master/samples/data-jolokia-demo)
is a setup for testing the [Jolokia](http://www.jolokia.org) HTTP-JMX
bridge in a tomcat. It uses a Docker data container which is linked
into the Tomcat container and contains the WAR files to deply. There
are two flavor of tests

* One with two image where a (almost naked) data container with the
  war file is created and then mounted into the server image during
  startup before the integration test.

* When using the profile `-Pmerge` then a single image with Tomcat and
  the dependent war files is created. During startup of a container
  from the created image, a deploy script will link over the war files
  into Tomat so that they are automatically deployed.
  
For running the tests call

```bash
mvn clean install
mvn -Pmerge clean install
```

The sever used is by default Tomcat 7. This server can easily be
changed with the system properties `server.name` and
`server.version`. The following variants are available:

* For `server.name=tomcat` the `server.version` can be 3.3, 4.0, 5.5, 6.0, 7.0
  or 8.0
* For `server.name=jetty` the `server.version` can be 4, 5, 6, 7, 8 or 9

Example:

```bash
mvn -Dserver.name=jetty -Dserver.version=9 clean install
```

### Cargo Demo

[cargo-jolokia-demo](https://github.com/rhuss/docker-maven-plugin/tree/master/samples/cargo-jolokia-demo)
will use Docker to start a Tomcat 7 server with dynamic port mapping,
which is used for remote deployment via
[Cargo](http://cargo.codehaus.org/Maven2+plugin) and running the
integration tests.

## Motivation

If you search it GitHub you will find a whole cosmos of Maven Docker
plugins (November 2014: 12 (!) plugins which 4 actively maintained
). On the one hand, variety is a good thing on the other hand for
users it is hard to decide which one to choose. So, you might wonder
why you should choose this one.

I setup a dedicated
[shootout project](https://github.com/rhuss/shootout-docker-maven)
which compares the four most active plugins. It contains a simple demo
project with a database and microservice image and an integration
test. Each plugin is configured to create images and run the
integration test (if possible). Although it might be a bit biased, I
think its a good decision help for finding out which plugin suites you
best.

But here is my motivation for writing this plugin. 

First of all, you might wonder, why I started this plugin if there
were already quite some out here ?

The reason is quite simple: I didn't knew them when I started and if
you look at the commit history you will see that they all started
their life roughly at the same time (March 2014).

My design goals where quite simple and here are my initial needs for
this plugin:

* I needed a flexible, **dynamic port mapping** from container to host
  ports so that truly isolated build can be achieved. This should
  work on indirect setups with VMs like
  [boot2docker](https://github.com/boot2docker/boot2docker) for
  running on OS X.

* It should be possible to **pull images** on the fly to get
  self-contained and repeatable builds with the only requirement to
  have docker installed.

* The configuration of the plugin should be **simple** since usually
  developers don't want to dive into specific Docker details only to
  start a container. So, only a handful options should be exposed
  which needs not necessarily map directly to docker config setup.

* The plugin should play nicely with
  [Cargo](http://cargo.codehaus.org/) so that deployments into
  containers can be easy.

* I want as **less dependencies** as possible for this plugin. So I
  decided to *not* use the Java Docker API
  [docker-java](https://github.com/docker-java/docker-java) which is
  external to docker and has a different lifecycle than Docker's
  [remote API](http://docs.docker.io/en/latest/reference/api/docker_remote_api/).
  That is probably the biggest difference to the other
  docker-maven-plugins since AFAIK they all rely on this API. Since
  for this plugin I really need only a small subset of the whole API,
  I think it is ok to do the REST calls directly. That way I only have
  to deal with Docker peculiarities and not also with docker-java's
  one. As a side effect this plugin has less transitive dependencies.
  FYI: There are now yet other Docker Java/Groovy client libraries out, which
  might be used for plugins like this, too:
  [fabric/fabric-docker-api](https://github.com/fabric8io/fabric8/tree/master/fabric/fabric-docker-api),
  [sprotify/docker-client](https://github.com/spotify/docker-client)
  or
  [gesellix-docker/docker-client](https://github.com/gesellix-docker/docker-client).
  Can you see the pattern ;-) ?
  
So, final words: Enjoy this plugin, and please use the
[issue tracker](https://github.com/rhuss/docker-maven-plugin/issues)
for anything what hurts or when you have a wish list. I'm quite
committed to this plugin and have quite some plans. Please stay tuned
...


