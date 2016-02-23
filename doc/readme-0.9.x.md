# docker-maven-plugin 0.9.x

[![endorse](http://api.coderwall.com/fabric8io/endorsecount.png)](http://coderwall.com/fabric8io)
[![Build Status](https://secure.travis-ci.org/fabric8io/docker-maven-plugin.png)](http://travis-ci.org/fabric8io/docker-maven-plugin)
[![Flattr](http://api.flattr.com/button/flattr-badge-large.png)](http://flattr.com/thing/73919/Jolokia-JMX-on-Capsaicin)

This is a Maven plugin for managing Docker images and containers from within Maven builds. 

> **This document describes version 0.9.x of this plugin. Starting
> with version 0.10.1 a new configuration syntax was introduced which
> as documented in the [README](../README.md). See the
> [CHANGELOG](changelog.md) for more details about the differences and
> this [blog post](http://ro14nd.de/Docker-Maven-Plugin-Rewrite/) for
> the motivation behind this restructuring. The 0.9.x version is
> deprecated and won't be updated in the futuer. For a migration to
> the new syntax please refer to this [guide](migration-0.9.x.md)**

With this plugin it is possible to run completely isolated integration tests so you don't need to take care of shared resources. Ports can be mapped dynamically and made available as Maven properties. 

Build artifacts and dependencies can be accessed from within 
running containers, so that a file based deployment is easily possible and there is no need to use dedicated deployment support from plugins like [Cargo](http://cargo.codehaus.org/).
 
This plugin's **highlights** are:

* Configurable port mapping
* Assigning dynamically selected host ports to Maven variables
* Pulling of images (with progress indicator) if not yet downloaded
* Optional waiting on a successful HTTP ping to the container
* On-the-fly creation of Docker data images and containers with Maven artifacts and dependencies linked or merged into the containers under test.
* Pushing data images to a registry
* Setting of environment variables when creating the container
* Support for SSL authentication (since Docker 1.3)
* Color output ;-)

This plugin is available from Maven central and can be connected to pre- and post-integration phase as seen below.
Please refer also to the examples provided in the `samples/` directory.

````xml
<plugin>
  <groupId>org.jolokia</groupId>
  <artifactId>docker-maven-plugin</artifactId>
  <version>0.9.11</version>

  <configuration>
     <!-- For possible options, see below -->
  </configuration>

  <!-- Connect start/stop to pre- and
       post-integration-test phase, respectively -->
  <executions>
    <execution>
       <id>start</id>
       <phase>pre-integration-test</phase>
       <goals>
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

## Maven Goals

### `docker:start`

Creates and starts a specified docker container with the additional possibility to link artifacts and dependencies to this 
  container, or, if `mergeData` is set to `true`, create a new image based on the given image and the assembly artifacts specified. 

#### Configuration

| Parameter    | Descriptions                                            | Property       | Default                 |
| ------------ | ------------------------------------------------------- | -------------- | ----------------------- |
| **url**      | URL to the docker daemon                                | `docker.url`   | `http://localhost:2375` |
| **image**    | Name of the docker image (e.g. `jolokia/tomcat:7.0.52`) | `docker.image` | none, required          |
| **ports**    | List of ports to be mapped statically or dynamically.   |                |                         |
| **env**      | Additional environment variables used when creating a container |        |                         | 
| **autoPull** | Set to `true` if an yet unloaded image should be automatically pulled | `docker.autoPull` | `true`      |
| **command**  | Command to execute in the docker container              |`docker.command`|                         |
| **assemblyDescriptor**  | Path to the data container assembly descriptor. See below for an explanation and example.              |                |                         |
| **assemblyDescriptorRef** | Predefined assemblies which can be directly used. For possible values, see below. | | |
| **mergeData** | If set to `true` create a new image based on the configured image and containing the assembly as described with `assemblyDescriptor` or `assemblyDescriptorRef` | `docker.mergeData` | `false` |
| **dataBaseImage** | Base for the data image (used only when `mergeData` is false) | `docker.baseImage` | `busybox:latest` |
| **dataImage** | Name to use for the created data image | `docker.dataImage` | `<group>/<artefact>:<version>` |
| **dataExportDir** | Name of the volume which gets exported | `docker.dataExportDir` | `/maven` |
| **authConfig** | Authentication configuration when autopulling images. See below for details. | | |
| **portPropertyFile** | Path to a file where dynamically mapped ports are written to |   |                         |
| **wait**     | Ramp up time in milliseconds                            | `docker.wait`  |                         |
| **waitHttp** | Wait until this URL is reachable with an HTTP HEAD request. Dynamic port variables can be given, too | `docker.waitHttp` | |
| **color**    | Set to `true` for colored output                        | `docker.color` | `true` if TTY connected  |
| **skip**     | If set to `true` skip the execution of this goal        | `docker.skip`  |                          |

### `docker:stop`

Stops and removes a docker container. 

#### Configuration

| Parameter  | Descriptions                     | Property       | Default                 |
| ---------- | -------------------------------- | -------------- | ----------------------- |
| **url**    | URL to the docker daemon         | `docker.url`   | `http://localhost:4243` |
| **image** | Which image to stop. All containers for this named image are stopped | `docker.image` |  |
| **keepContainer** | Set to `true` for not automatically removing the container after stopping it. | `docker.keepContainer` | |
| **keepRunning** | Set to `true` for not stopping the container even when this goals runs. | `docker.keepRunning` | `false` |
| **keepData**  | Keep the data container and image after the build if set to `true` | `docker.keepData` |  `false`                       |
| **color**  | Set to `true` for colored output | `docker.color` | `true` if TTY connected |
| **skip**     | If set to `true` skip the execution of this goal        | `docker.skip`  |                          |

### `docker:push`

Push a data image to the registry. The data image is the same created during the `start` goal. See below for more information about how the data image is created. The registry to push is by 
default `registry.hub.docker.io` but can be specified as part of the `dataImage` name the Docker way. E.g. `docker.test.org:5000/data:1.5` will push the repository `data` with tag `1.5` to 
the registry `docker.test.org` at port `5000`. Security information (i.e. user and password) can be specified in multiple ways as described in an extra section. 

#### Configuration

| Parameter    | Descriptions                                            | Property       | Default                 |
| ------------ | ------------------------------------------------------- | -------------- | ----------------------- |
| **url**      | URL to the docker daemon                                | `docker.url`   | `http://localhost:2375` |
| **image**    | Name of the docker base image (e.g. `consol/tomcat:7.0.52`) | `docker.image` | none         |
| **autoPull** | Set to `true` if an yet unloaded image should be automatically pulled | `docker.autoPull` | `true`      |
| **assemblyDescriptor**  | Path to the data container assembly descriptor. See below for an explanation and example               |                |                         |
| **assemblyDescriptorRef** | Predefined assemblies which can be directly used. Possible values are given below | | |
| **mergeData** | If set to `true` create a new image based on the configured image and containing the assembly as described with `assemblyDescriptor` or `assemblyDescriptorRef` | `docker.mergeData` | `false` |
| **dataBaseImage** | Base for the data image (used only when `mergeData` is false) | `docker.baseImage` | `busybox:latest` |
| **dataImage** | Name to use for the created data image | `docker.dataImage` | `<group>/<artefact>:<version>` |
| **dataExportDir** | Name of the volume which gets exported | `docker.dataExportDir` | `/maven` |
| **keepData**  | Keep the data image after the build if set to `true` | `docker.keepData` |  `true`                       |
| **authConfig** | Authentication configuration when pushing images. See below for details. | | |
| **color**    | Set to `true` for colored output                        | `docker.color` | `true` if TTY connected  |
| **skip**     | If set to `true` skip the execution of this goal        | `docker.skip`  |                          |

### `docker:build`

Build a data image without pushing. It works essentially the same as `docker:push` but does not push to a registry
and does not delete the image afterwards. 

#### Configuration

| Parameter    | Descriptions                                            | Property       | Default                 |
| ------------ | ------------------------------------------------------- | -------------- | ----------------------- |
| **url**      | URL to the docker daemon                                | `docker.url`   | `http://localhost:2375` |
| **image**    | Name of the docker base image (e.g. `consol/tomcat:7.0.52`) | `docker.image` | none         |
| **autoPull** | Set to `true` if an yet unloaded base image should be automatically pulled | `docker.autoPull` | `true`      |
| **assemblyDescriptor**  | Path to the data container assembly descriptor. See below for an explanation and example               |                |                         |
| **assemblyDescriptorRef** | Predefined assemblies which can be directly used. Possible values are given below | | |
| **mergeData** | If set to `true` create a new image based on the configured image and containing the assembly as described with `assemblyDescriptor` or `assemblyDescriptorRef` | `docker.mergeData` | `false` |
| **dataBaseImage** | Base for the data image (used only when `mergeData` is false) | `docker.baseImage` | `busybox:latest` |
| **dataImage** | Name to use for the created data image | `docker.dataImage` | `<group>/<artefact>:<version>` |
| **dataExportDir** | Name of the volume which gets exported | `docker.dataExportDir` | `/maven` |
| **ports**    | List of ports to be exposed                             |                |  | 
| **env**      | List of environment variables to use for building       |                |  | 
| **color**    | Set to `true` for colored output                        | `docker.color` | `true` if TTY connected  |
| **skip**     | If set to `true` skip the execution of this goal        | `docker.skip`  |                          |

## Dynamic Port mapping

For the `start` goal, container port mapping may be configured using a `ports` declaration.

```xml
<ports>
  <port>18080:8080</port>
  <port>host.port:80</port>
<ports>
```

A `port` stanza may take one of two forms:
* A tuple consisting of two numeric values separated by a `:`. This form will result in an explicit mapping between the docker host and the corresponding port inside the container. In the above example, port 18080 would be exposed on the docker host and mapped to port 8080 in the running container.
* A tuple consisting of a string and a numeric value separated by a `:`. In this form, the string portion of the tuple will correspond to a Maven property. If the property is undefined when the `start` task executes, a port will be dynamically selected by Docker in the range 49000 ... 49900 and assigned to the property which may then be used later in the same POM file. If the property exists and has a numeric value, that value will be used as the exposed port on the docker host as in the previous form. In the above example, the docker service will elect a new port and assign the value to the property `host.port` which may then later be used in a property expression similar to `<value>${host.port}</value>`. This can be used to pin a port from the outside when doing some initial testing similar to:

    mvn -Dhost.port=10080 docker:start

Another useful configuration option is `portPropertyFile` with which a file can be specified to which the real port
mapping is written after all dynamic ports has been resolved. The keys of this property file are the variable names,
the values are the dynamically assgined host ports. This property file might be useful together with other maven
plugins which already resolved their maven variables earlier in the lifecycle than this plugin so that the port variables
might not be available to them.

## Setting environment variables

When creating a container one or more environment variables can be set via configuration with the `env` parameter
 
```xml
<env>
  <JAVA_HOME>/opt/jdk8</JAVA_HOME>
  <CATALINA_OPTS>-Djava.security.egd=file:/dev/./urandom</CATALINA_OPTS>
</env>
```

If you put this configuration into profiles you can easily create various test variants with a single image (e.g. by 
switching the JDK or whatever).

## Getting your assembly into the container

With using the `assemblyDescriptor` or `assemblyDescriptorRef` option it is possible to bring local files, artifacts and dependencies into the running Docker container. This works as follows:

* `assemblyDescriptor` points to a file describing the data to assemble. It has the same format as for creating assemblies with the [maven-assembly-plugin](http://maven.apache.org/plugins/maven-assembly-plugin/) , with some restrictions (see below).
* Alternatively `assemblyDescriptorRef` can be used with the name of a predefined assembly descriptor. See below for possible values.
* This plugin will create the assembly and create a Docker image on the fly which exports the assembly below a directory `/maven`. Typically this will be an extra image, but if the configuration parameter `mergeData` is set then the image which was configured for the `start` goal is used as a base image so that the data and e.g. application server are contained in the same image. This is useful for distributing a complete image where artifacts and the server are baked together.
* From this image a (data) container is created and the 'real' container is started with a `volumesFrom` option pointing to this data container (if `mergeData` is not used).
* That way, the container started has access to all the data created from the directory `/maven/` within the container.
* The container command can check for the existence of this directory and deploy everything within this directory.

Let's have a look at an example. In this case, we are deploying a war-dependency into a Tomcat container. The assembly descriptor `src/main/docker-assembly.xml` option may look like

````xml
<assembly xmlns="http://maven.apache.org/plugins/maven-assembly-plugin/assembly/1.1.2"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://maven.apache.org/plugins/maven-assembly-plugin/assembly/1.1.2 
                        http://maven.apache.org/xsd/assembly-1.1.2.xsd">
  <dependencySets>
    <dependencySet>
      <includes>
        <include>org.jolokia:jolokia-war</include>
      </includes>
      <outputDirectory>.</outputDirectory>
      <outputFileNameMapping>jolokia.war</outputFileNameMapping>
    </dependencySet>
</assembly>
````

Then you will end up with a data container which contains with a file `/maven/jolokia.war` which is mirrored into the main container.

The plugin configuration could look like

````xml
<plugin>
    <groupId>org.jolokia</groupId>
    <artifactId>docker-maven-plugin</artifactId>
    ....
    <configuration>
      <image>jolokia/tomcat-7.0</image>
      <assemblyDescriptor>src/main/docker-assembly.xml</assemblyDescriptor>
      ...
    </configuration>
</plugin>
````

The image `jolokia/tomcat-7.0` is a [trusted build](https://github.com/fabric8io/jolokia-it/tree/master/docker/tomcat/7.0) available from the central docker registry which uses a command `deploy-and-run.sh` that looks like this:

````bash
#!/bin/sh

DIR=${DEPLOY_DIR:-/maven}
echo "Checking *.war in $DIR"
if [ -d $DIR ]; then
  for i in $DIR/*.war; do
     file=$(basename $i)
     echo "Linking $i --> /opt/tomcat/webapps/$file"
     ln -s $i /opt/tomcat/webapps/$file
  done
fi
/opt/tomcat/bin/catalina.sh run
````

Before starting tomcat, this script will link every .war file it finds in `/maven` to `/opt/tomcat/webapps` which effectively will deploy them. 

Alternatively, the parameter `mergeData` could have been set to `true` in the plugin configuration. In this case no separate data image is created but an image which is based on the specified image (`jolokia/tomcat-7.0` in this example) and the assembly are directly available from `/maven`. This has the advantage that only a single image needs to be pushed containing  both, the created artifact and application server.

It is really that easy to deploy your artifacts. And it's fast (less than 10s for starting, deploying, testing (1 test) and stopping the container on my 4years old MBP using boot2docker).

### Assembly Descriptor

The assembly descriptor has the same [format](http://maven.apache.org/plugins/maven-assembly-plugin/assembly.html) as the the maven-assembly-plugin with the following exceptions:

* `<formats>` are ignored, the assembly will allways use a directory when preparing the data container (i.e. the format is fixed to `dir`)
* The `<id>` is ignored since only a single assembly descriptor is used (no need to distinguish multiple descriptors)

This `docker-maven-plugin` comes with some predefined assembly descriptors which can be used with `assemblyDescritproRef`:

* **artifact-with-dependencies** will copy your project's artifact and all its dependencies
* **artifact** will copy only the project's artifact but no dependencies.
* **project** will copy over the whole Maven project but with out `target/` directory.
* **rootWar** will copy the artifact as `ROOT.war` to the exposed directory. I.e. Tomcat will then deploy the war under the root context.

## Cleanup

Various configuration parameters of this plugin are available for cleaning up after a build:

* `keepRunning` specifies that the container should not be stopped after the build. Obviously, the container and any data image created will be left alone as well. This option is especially useful when given as command line option `-Ddocker.keepRunning` for doing some debugging or developing integration tests.

* `keepContainer` tells the plugin to not remove the container created from the image after the build (the container is stopped, though). If a merged container was created via the option `mergeData` then this container will remain as well as the on-the-fly created image this container belongs to. This is useful for post-mortem analysis of the container by e.g. looking at the logs. This option can be switched on with `-Ddocker.keepContainer`. If a separate data container is used, this data container and its image will stay as well.

* `keepData` finally can be used to keep only the data container, but the other container should be be removed. This option has only an effect if `keepContainer` is `false`. That way, the created artifacts can be kept even after the build.

## Authentication

When pulling (via the `autoPull` mode of `docker:start` and `docker:push`) or pushing image, it might be necessary to authenticate against a Docker registry.  

There are three different ways for providing credentials:

* Using a `<authConfig>` section in the plugin configuration with `<username>` and `<password>` elements.
* Providing system properties `docker.username` and `docker.password` from the outside
* Using a `<server>` configuration in the the `~/.m2/settings.xml` settings

Using the username and password directly in the `pom.xml` is not recommended since this is widely visible. This is most easiest and transparent way, though. Using an `<authConfig>` is straight forward:

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

The system property provided credentials are a good compromise when using CI servers like Jenkins. You simply provide the credentials from the outside:

	mvn -Ddocker.username=jolokia -Ddocker.password=s!cr!t docker:push

The most secure and also the most *mavenish* way is to add a server to the Maven settings file `~/.m2/settings.xml`: 

````xml
<servers>
  <server>
    <id>registry.hub.docker.io</id>
    <username>jolokia</username>
    <password>s!cr!t</password>
  </server>
  ....
</servers>
````

The server id must specify the registry to push to/pull from, which by default is central index `registry.hub.docker.io`. Here you should add you docker.io account for your repositories.

### Password encryption

Regardless which mode you choose you can encrypt password as described in the [Maven documentation](http://maven.apache.org/guides/mini/guide-encryption.html). Assuming that you have setup a *master password* in `~/.m2/security-settings.xml` you can create easily encrypted passwords:

````bash
	$ mvn --encrypt-password
	Password:
	{QJ6wvuEfacMHklqsmrtrn1/ClOLqLm8hB7yUL23KOKo=}
````

This password then can be used in `authConfig`, `docker.password` and/or the `<server>` setting configuration. However, putting an encrypted password into `authConfig` in the `pom.xml` doesn't make much sense, since this password is encrypted with an individual master password.

## SSL with keys and certificates

The plugin can communicate with the Docker Host via SSL, too. This is the default now for Docker 1.3 (and Boot2Docker). 
SSL is switched on if the port used is `2376` which is the default, IANA registered SSL port of the Docker host 
(and plain HTTP for `2375`). The directory holding `ca.pem`, `key.pem` and `cert.pem` can be configured with the
configuration parameter `certPath`. Alternatively, the environment variable `DOCKER_CERT_PATH` is evaluated and finally 
`~/.docker` is used as the last fallback.

## Examples

This plugin comes with some commented examples in the `samples/` directory:

* [data-jolokia-demo](https://github.com/fabric8io/docker-maven-plugin/tree/master/samples/data-jolokia-demo) is a setup for testing the [Jolokia](http://www.jolokia.org) HTTP-JMX bridge in a tomcat. It uses a Docker data container which is linked into the Tomcat container and contains the WAR files to deply
* [cargo-jolokia-demo](https://github.com/fabric8io/docker-maven-plugin/tree/master/samples/cargo-jolokia-demo) is the same as above except that Jolokia gets deployed via [Cargo](http://cargo.codehaus.org/Maven2+plugin)

For a complete example please refer to `samples/data-jolokia-demo/pom.xml`.

In order to prove, that self contained builds are not a fiction, you might convince yourself by trying out this (on a UN*X like system):

````bash
# Move away your local maven repository for a moment
cd ~/.m2/
mv repository repository.bak

# Fetch docker-maven-plugin
cd /tmp/
git clone https://github.com/fabric8io/docker-maven-plugin.git
cd docker-maven-plugin/

# Install plugin
# (This is only needed until the plugin makes it to maven central)
mvn install

# Goto the sample
cd samples/data-jolokia-demo

# Run the integration test
mvn verify

# Use a 'merged' data image
mvn -Pmerge-data verify

# Push the data image
mvn docker:push
 
# Please note, that first it will take some time to fetch the image
# from docker.io. The next time running it will be much faster. 

# Restore back you .m2 repo
cd ~/.m2
mv repository /tmp/
mv repository.bak repository
```` 

## Misc

* [Script](https://gist.github.com/deinspanjer/9215467) for setting up NAT forwarding rules when using [boot2docker](https://github.com/boot2docker/boot2docker)
on OS X

* It is recommended to use the `maven-failsafe-plugin` for integration testing in order to
stop the docker container even when the tests are failing.

## Why another docker-maven-plugin ? 

Spring feelings in 2014 seems to be quite fertile for the Java crowd's
Docker awareness
;-). [Not only I](https://github.com/bibryam/docker-maven-plugin/issues/1)
counted ~~5~~ 10 [maven-docker-plugins](https://github.com/search?q=docker-maven-plugin)
on GitHub as of ~~April~~ July 2014, tendency increasing. It seems, that all
of them have a slightly different focus, but all of them can do the
most important tasks: Starting and stopping containers. 

So you might wonder, why I started this plugin if there were already
quite some out here ?

The reason is quite simple: I didn't knew them when I started and if
you look at the commit history you will see that they all started
their life roughly at the same time (March 2014).

I expect there will be some settling soon and even some merging of
efforts which I would highly appreciate and support.

For what it's worth, here are some of my motivations for this plugin
and what I want to achieve:

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
  decided to *not* use the
  Java Docker API [docker-java](https://github.com/docker-java/docker-java) which is
  external to docker and has a different lifecycle than Docker's
  [remote API](http://docs.docker.io/en/latest/reference/api/docker_remote_api/). 
  That is probably the biggest difference to the other
  docker-maven-plugins since AFAIK they all rely on this API. Since
  for this plugin I really need only a small subset of the whole API,
  I think it is ok to do the REST calls directly. That way I only have
  to deal with Docker peculiarities and not also with docker-java's
  one. As a side effect this plugin has less transitive dependencies.
  FYI: There is now yet another Docker Java client library out, which
  might be used for plugins like this, too:
  [fabric-docker-api](https://github.com/fabric8io/fabric8/tree/master/fabric/fabric-docker-api). (Just
  in case somebody wants to write yet another plugin ;-)

In the meantime, enjoy this plugin, and please use the
[issue tracker](https://github.com/fabric8io/docker-maven-plugin/issues) 
for anything what hurts.

