# docker-maven-plugin

This is Maven plugin for managing Docker images and containers which
is especially useful during integration tests. It plays nicely with
[Cargo](http://cargo.codehaus.org/)'s remote deployment model, which
is available for most of the supported containers. 

With this plugin it is possible to run completely isolated integration
tests so you don't need to take care of shared resources. Ports can be
mapped dynamically and made available as Maven properties. 

This plugin is still in a very early stage of development. If you like to
participate feel free to send me as many pull requests as you can ;-)

Here is an example, which uses a Docker image `jolokia/tomcat:7.0.52`
(not yet pushed) and Cargo for deploying artifacts to it. Integration
tests (not shown here) can then access the deployed artifact via an
URL which is unique for this particular test run.

````xml

     <plugins>     
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

### `docker:start`

Creates and starts a docker container.

#### Configuration

 * **url** URL to the docker daemon (default: `http://localhost:4242`) 
 * **image** Name of the docker image to use
     (e.g. `jolokia/tomcat:7.0.52`). Required 
 * **ports** List of ports to be mapped statically or dynamically. See
     above for an example. 
 * **autoPull** Set to `true` if an unknown image should be
     automatically pulled (not implemented yet)
 * **command** Command to execute in the docker container
 * **portPropertyFile** Path to a file where dynamically mapped ports 
     are written to ,
 * **wait** Ramp up time in milliseconds
 * **color** Set to `true` for colored output

### `docker:stop`

Stops and removes a docker container. 

#### Configuration

 * **url** URL to the docker daemon (default: `http://localhost:4242`)
 * **color** Set to `true` for colored output
 * **keepContainer** Set to `true` for not automatically removing the container after stopping it.

## Links

* [Script](https://gist.github.com/deinspanjer/9215467) for setting up NAT forwarding rules when using [boot2docker](https://github.com/boot2docker/boot2docker)
on OS X

* Use `maven-failsafe-plugin` for integration tests in order to stop the docker container even when the tests are failing.

