## Introduction 

It focuses on two major aspects:

* **Building** and **pushing** Docker images which contain build artifacts
* **Starting** and **stopping** Docker containers for integration
  testing and development 

Docker *images* are the central entity which can be configured. 
Containers, on the other hand, are more or less volatile. They are
created and destroyed on the fly from the configured images and are
completely managed internally.

### Building docker images

One purpose of this plugin is to create docker images holding the
actual application. This is done with the `docker:build` goal.  It
is easy to include build artifacts and their dependencies into an
image. Therefore, this plugin uses the
[assembly descriptor format](http://maven.apache.org/plugins/maven-assembly-plugin/assembly.html)
from the
[maven-assembly-plugin](http://maven.apache.org/plugins/maven-assembly-plugin/)
to specify the content which will be added from a sub-directory in
the image (`/maven` by default). Images that are built with this
plugin can be pushed to public or private Docker registries with
`docker:push`.

### Running containers

With this plugin it is possible to run completely isolated integration
tests so you don't need to take care of shared resources. Ports can be
mapped dynamically and made available as Maven properties to your
integration test code. 

Multiple containers can be managed at once, which can be linked
together or share data via volumes. Containers are created and started
with the `docker:start` goal and stopped and destroyed with the
`docker:stop` goal. For integration tests both goals are typically
bound to the the `pre-integration-test` and `post-integration-test`,
respectively. It is recommended to use the [`maven-failsafe-plugin`](http://maven.apache.org/surefire/maven-failsafe-plugin/) for
integration testing in order to stop the docker container even when
the tests fail.

For proper isolation, container exposed ports can be dynamically and
flexibly mapped to local host ports. It is easy to specify a Maven
property which will be filled in with a dynamically assigned port
after a container has been started, which can then be used as
parameter for integration tests to connect to the application.

### Configuration

You can use a single configuration for all goals (in fact, that's the
recommended way). The configuration contains a general part and a list
of image-specific configurations, one for each image. 

The general part contains global configuration like the Docker URL or
the path to the SSL certificates for communication with the Docker Host.

Then, each image configuration has three parts:

* A general part containing the image's name and alias.
* A `<build>` configuration specifying how images are built.
* A `<run>` configuration describing how containers should be created and started.

The `<build>` and `<run>` parts are optional and can be omitted.

Let's look at a plugin configuration example:

````xml
<configuration>
  <images>
    <image>
      <alias>service</alias>
      <name>jolokia/docker-demo:${project.version}</name>

      <build>
         <from>java:8</from>
         <assembly>
           <descriptor>docker-assembly.xml</descriptor>
         </assembly>
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

Here, two images are specified. One is the official PostgreSQL 9 image from
Docker Hub, which internally is referenced as "*database*" (`<alias>`). It
only has a `<run>` section which declares that the startup should wait
until the given text pattern is matched in the log output. Next is a
"*service*" image, which is specified in the `<build>` section. It
creates an image which has artifacts and dependencies in the
`/maven` directory (and which are specified with an assembly
descriptor). Additionally it specifies the startup command for the
container, which in this example fires up a microservice from a jar
file copied over via the assembly descriptor. It also exposes
port 8080. In the `<run>` section this port is dynamically mapped to a
port from the Docker range of 49000 ... 49900, and is then assigned to the
Maven property `${jolokia.port}`. This property could be used, for example,
by an integration test to access this microservice. An important part is
the `<links>` section which indicates that the image with the alias of
"*database*" is linked into the "*service*" container, which can access
the internal ports in the usual Docker way (via environment variables
prefixed with `DB_`).

Images can be specified in any order and the plugin will take care of the
proper startup order (and will bail out if it detects circular
dependencies). 

### Other highlights

Some other highlights in random order (and not complete):

* Auto pulling of images (with a progress indicator)
* Waiting for a container to startup based on time, the reachability
  of an URL, or a pattern in the log output
* Support for SSL authentication (since Docker 1.3)
* Specification of encrypted registry passwords for push and pull in
  `~/.m2/settings.xml` (i.e., outside the `pom.xml`)
* Color output ;-)

### Why another Maven Plugin ?

If you search it GitHub you will find a whole cosmos of Maven Docker
plugins (As of November 2014: 12 (!) plugins which 4 actively maintained).
On the one hand, variety is a good thing, but on the other hand for
users it is hard to decide which one to choose. So, you might wonder
why you should choose this one.

I setup a dedicated [shootout project](https://github.com/rhuss/shootout-docker-maven)
which compares the four most active plugins. It contains a simple demo
project with a database and a microservice image, along with an integration
test. Each plugin is configured to create images and run the
integration test (if possible). Although it might be a bit biased, I
think it can be useful for figuring out which plugin suites you best.

But here is my motivation for writing this plugin: 

First of all, you might wonder, why did I start this plugin if there
were already quite a few out there?

The reason is quite simple: I didn't know about them when I started, and if
you look at the commit history you will see that they all started
roughly at the same time (March 2014).

My design goals were quite simple, and these were my initial needs for
this plugin:

* I needed a flexible, **dynamic port mapping** from container to host
  ports so that truly isolated builds could be made. This should
  work on indirect setups with VMs like
  [boot2docker](https://github.com/boot2docker/boot2docker) for
  running on OS X.

* It should be possible to **pull images** on the fly to get
  self-contained and repeatable builds with the only requirement to
  have Docker installed.

* The configuration of the plugin should be **simple**, since developers
  don't want to be forced to dive into specific Docker details only to
  start a container. So, only a handful options should be exposed,
  which needs not necessarily map directly to docker config setup.

* The plugin should play nicely with
  [Cargo](http://cargo.codehaus.org/) so that deployments into
  containers is easy.

* I wanted as **few dependencies** as possible for this plugin. So I
  decided to *not* use the Java Docker API
  [docker-java](https://github.com/docker-java/docker-java) which is
  external to docker and has a different lifecycle than Docker's
  [remote API](http://docs.docker.io/en/latest/reference/api/docker_remote_api/).
  That is probably the biggest difference to the other
  docker-maven-plugins since AFAIK they all rely on this API. Since
  this plugin needs only a small subset of the whole API,
  I think it is OK to do the REST calls directly. That way I only have
  to deal with Docker peculiarities and not docker-java's as well.
  As a side-effect, this plugin has fewer transitive dependencies.
  FYI: There are other Docker Java/Groovy client libraries out, which
  might be suitable for plugins like this:
  [fabric/fabric-docker-api](https://github.com/fabric8io/fabric8/tree/master/fabric/fabric-docker-api),
  [sprotify/docker-client](https://github.com/spotify/docker-client)
  or
  [gesellix-docker/docker-client](https://github.com/gesellix-docker/docker-client).
  Can you see the pattern ;-) ?
  
So, final words: Enjoy this plugin, and please use the
[issue tracker](https://github.com/rhuss/docker-maven-plugin/issues)
for anything what hurts, or when you have a wish list. I'm quite
committed to this plugin and have quite some plans. Please stay tuned...
