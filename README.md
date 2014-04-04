# docker-maven-plugin

This is Maven plugin for managing Docker images and containers which
is especially useful during integration tests. It plays nicely with
[Cargo](http://cargo.codehaus.org/)'s remote deployment model, which
is available for most of the supported containers. 

With this plugin it is possible to run completely isolated integration
tests so you don't need to take care of shared resources. Ports can be
mapped dynamically and made available as Maven properties. 

This plugin is still in a very early stage of development, but the
**highlights** are:

* Configurable port mapping
* Assigning dynamically selected host ports to Maven variables
* Pulling of images (with progress indicator) if not yet downloaded
* Color output ;-)

## Maven Goals

### `docker:start`

Creates and starts a docker container.

#### Configuration

| Parameter    | Descriptions                                            | System Property| Default                 |
| ------------ | ------------------------------------------------------- | -------------- | ----------------------- |
| **url**      | URL to the docker daemon                                | `docker.url`   | `http://localhost:4243` |
| **image**    | Name of the docker image (e.g. `jolokia/tomcat:7.0.52`) | `docker.image` | none, required          |
| **ports**    | List of ports to be mapped statically or dynamically.   |                |                         |
| **autoPull** | Set to `true` if an unknown image should be automatically pulled | `docker.autoPull` | `true`      |
| **command**  | Command to execute in the docker container              |`docker.command`|                         |
| **portPropertyFile** | Path to a file where dynamically mapped ports are written to |   |                         |
| **wait**     | Ramp up time in milliseconds                            | `docker.wait`  |                         |
| **color**    | Set to `true` for colored output                        | `docker.color` | `false`                 |

### `docker:stop`

Stops and removes a docker container. 

#### Configuration

| Parameter  | Descriptions                     | Default                 |
| ---------- | -------------------------------- | ----------------------- |
| **url**    | URL to the docker daemon         | `http://localhost:4243` |
| **image** | Which image to stop. All containers for this named image are stopped | `false` |
| **containerId** | ID of the container to stop | `false` |
| **keepContainer** | Set to `true` for not automatically removing the container after stopping it. | `false` |
| **keepRunning** | Set to `true` for not stopping the container even when this goals runs. | `false` |
| **color**  | Set to `true` for colored output | `false`                 |


## Misc

* [Script](https://gist.github.com/deinspanjer/9215467) for setting up NAT forwarding rules when using [boot2docker](https://github.com/boot2docker/boot2docker)
on OS X

* Use `maven-failsafe-plugin` for integration tests in order to stop the docker container even when the tests are failing.

## Example

Here's an example, which uses a Docker image `jolokia/tomcat:7.0.52`
(not yet pushed) and Cargo for deploying artifacts to it. Integration
tests (not shown here) can then access the deployed artifact via an
URL which is unique for this particular test run.

````xml
<plugin>
  <groupId>org.jolokia</groupId>
  <artifactId>docker-maven-plugin</artifactId>
  <version>1.0-SNAPSHOT</version>
  <configuration>
    <!-- Docker Image -->
    <image>jolokia/tomcat:7.0.52</image>

    <!-- Wait 2 seconds after the container has been started to
         warm up tomcat -->
    <wait>2000</wait>

    <!-- Map ports -->
    <ports>
      <!-- Port 8080 within the container is mapped to an (arbitrary) free port
           between 49000 and 49900 by Docker. The chosen port is stored in
           the Maven property "jolokia.port" which can be used later on -->
      <port>jolokia.port:8080</port>
    </ports>
  </configuration>

  <executions>
    <execution>
      <id>start</id>
      <!-- Start before the integration test ... -->
      <phase>pre-integration-test</phase>
      <goals>
        <goal>start</goal>
      </goals>
    </execution>
    <execution>
      <id>stop</id>
      <!-- ... and stop afterwards. -->
      <phase>post-integration-test</phase>
      <goals>
        <goal>stop</goal>
      </goals>
    </execution>
  </executions>
</plugin>

<plugin>
  <groupId>org.codehaus.cargo</groupId>
  <artifactId>cargo-maven2-plugin</artifactId>
  <configuration>

    <!-- Use Tomcat 7 as server -->
    <container>
       <containerId>tomcat7x</containerId>
       <type>remote</type>
    </container>

    <!-- Server specific configuration -->
    <configuration>
      <type>runtime</type>
      <properties>
        <cargo.hostname>localhost</cargo.hostname>

        <!-- This is the port chosen by Docker -->
        <cargo.servlet.port>${jolokia.port}</cargo.servlet.port>

        <!-- User as configured in the Docker image -->
        <cargo.remote.username>admin</cargo.remote.username>
        <cargo.remote.password>admin</cargo.remote.password>
      </properties>
    </configuration>

    <deployables>
      <!-- Deploy a Jolokia agent -->
      <deployable>
        <groupId>org.jolokia</groupId>
        <artifactId>jolokia-agent-war</artifactId>
        <type>war</type>
        <properties>
          <context>/jolokia</context>
        </properties>
      </deployable>
    </deployables>
  </configuration>
  <executions>
    <execution>
      <id>start-server</id>
      <phase>pre-integration-test</phase>
      <goals>
        <goal>deploy</goal>
      </goals>
    </execution>
  </executions>
</plugin>
```


## Maven Repository

Snapshots of this plugin can be directly obtained from [labs.consol.de](http://labs.consol.de/maven/snapshots-repository):

```xml

<pluginRepositories>
  <pluginRepository>
    <id>labs-consol-snapshot</id>
    <name>ConSol* Labs Repository (Snapshots)</name>
    <url>http://labs.consol.de/maven/snapshots-repository</url>
    <snapshots>
      <enabled>true</enabled>
    </snapshots>
    <releases>
      <enabled>false</enabled>
    </releases>
  </pluginRepository>
</pluginRepositories>
```  

## Why another docker-maven-plugin ? 

Spring feelings in 2014 seems to be quite fertile for the Java crowd's
Docker awareness
;-). [Not only I](https://github.com/bibryam/docker-maven-plugin/issues/1)
counted 5 [maven-docker-plugins](https://github.com/search?q=docker-maven-plugin)
on GitHub as of April 2014, tendency increasing. It seems, that all
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
  Java Docker API [docker-java](https://github.com/kpelykh/docker-java) which is
  external to docker and has a different lifecycle than Docker's
  [remote API](http://docs.docker.io/en/latest/reference/api/docker_remote_api/). 
  (currently v1.8 (docker-java) vs. v1.10 (docker) API version)
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

For this plugin I still have some ideas to implement (e.g. to bring
generated artifacts into the container's FS, even when using VMs like
for boot2docker), but otherwise this is not my main project (it is the
result of an internal research project here at ConSol*). 
So I would be happy to contribute to other projects, too, when the
dust has been settled a bit.  

In the meantime, enjoy this plugin, and please use the
[issue tracker](https://github.com/rhuss/docker-maven-plugin/issues) 
for anything what hurts.

