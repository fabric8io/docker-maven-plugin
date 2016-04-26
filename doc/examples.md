## Examples

This plugin comes with some commented examples in the `samples/` directory:

### Jolokia Demo

[data-jolokia-demo](https://github.com/fabric8io/docker-maven-plugin/tree/master/samples/data-jolokia-demo)
is a setup for testing the [Jolokia](http://www.jolokia.org) HTTP-JMX
bridge in a tomcat. It uses a Docker data container which is linked
into the Tomcat container and contains the WAR files to deploy. There
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
# Use two ("data" and "server") connected containers
mvn clean install
# Use a single image with tomcat and data:
mvn -Pmerge clean install
# Use a property based configuration:
mvn -Pprops clean install
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

In addition to running the integration test with building images, starting containers, 
running tests and stopping containers one can also only start the containers:

```bash
mvn docker:start
```

In order to get the dynamically exposed port, use `docker ps`. You can connect to the 
Jolokia agent inside the container then with an URL like `http://localhost:http://localhost:49171/jolokia` to 
the Agent.

For stopping the server simply call

```bash
mvn docker:stop
```

### Cargo Demo

[cargo-jolokia-demo](https://github.com/fabric8io/docker-maven-plugin/tree/master/samples/cargo-jolokia-demo)
will use Docker to start a Tomcat 7 server with dynamic port mapping,
which is used for remote deployment via
[Cargo](http://cargo.codehaus.org/Maven2+plugin) and running the
integration tests.

### docker-maven-plugin Shootout

In order to help in the decision, which plugin to use, there is a
sample project
[rhuss/shootout-docker-maven](https://github.com/rhuss/shootout-docker-maven),
which has more complex sample project involving two images:

* Vanilla PostgreSQL 9 Image
* HTTP Request Logging Service
  - MicroService mit embedded Tomcat
  - DB Schema is created during startup via [Flyway](http://flywaydb.org/)
* PostgreSQL container is connected via a Docker 'link'
* Simple integration test which excercises the service

The different plugins can be enabled with different Maven profiles,
the one for this plugin is called `fabric8io` (and the others `wouterd`,
`alexec` and `spotify`).

For more information please look over there.
