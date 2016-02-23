## Migration Guide

This recipes gives you some hint and help for migrating from the old
(0.9.x) configuration syntax to the new one (0.10.x and later).

If there are any issue when doing the migration or you do need some
help, please raise an
[issue](https://github.com/fabric8io/docker-maven-plugin/issues) with your
original configuration and you will get some help for the migration.

The biggest change was support for multiple images. The whole story
about the change can be found in this
[blog post](https://ro14nd.de/Docker-Maven-Plugin-Rewrite/). In the
old version there was a single configurtion which resulted in one or
two images, depending on the `mergeData` property.

In general now all build aspects are collected now below a `<build>`
section and all runtime aspects in a `<run>` section for each image. 

Typically it should be clear what configuration from the original,
flat configuration list is a runtime or build aspect, here are some
hints, depending on whether data merging is on or off (property
`mergeData`) 

* `image` becomes the name of an image with a `<run>` configuration
  (non-merging) or the base image within a `<from>`
  element in the `<build>` section (merging). 
* `dataImage` is used for the name of the data image (non-merging)
  with only a `<build>` section or the name of the single image with
  both `<run>` and `<build>` section when merging is used.

The [Examples](#examples) below show sample migrations for these two
different situations and make this transformation clearer.

### Wait configuration

When waiting for certain conditions during startup, in the old format
there where dedicated configuration params for each possible
conditions. Now they are collected within a subelement `<wait>` which
is part of a `<run>` configuration.

For example:

```xml
<waitHttp>http://localhost:${port}/jolokia</waitHttp>
<wait>10000</wait>
```

becomes 

```
<wait>
  <http>
    <url>http://localhost:${port}/jolokia</url>
  </http>
  <time>10000</time>
</wait>
```

In addition the new syntax support also waiting on a log outpbut
(`<log>`). 

### Lifecycle binding

The old version of the plugin combined some task: E.g. if you used
`docker:start` or `docker:pull` a `docker:build` was done
implicitely. In order to make stuff more explicite and easier to
understand this is not the case anymore. So, when you bind to a
lifecycle phase you have to add all steps explicitely:

```xml
<executions>
  <execution>
    <id>start</id>
    <phase>pre-integration-test</phase>
    <goals>
      <goal>build</goal>
      <goal>start</goal>
    </goals>
  </execution>
  ...
</executions>
``` 

### Examples

The migration is different depending on whether you use `mergeData`
or not. 

## Non merged images

When `mergeData` was false, two images where created: One holding the
data and one for the server which is connected to the data container
during startup. So, the following old configuration 

```xml
<configuration>
  <mergeData>false</mergeData>
  <image>consol/tomcat-7.0</image>
  <dataImage>jolokia/data</dataImage>
  <assemblyDescriptor>src/main/docker-assembly.xml</assemblyDescriptor>
  <env>
    <CATALINA_OPTS>-Xmx32m</CATALINA_OPTS>
  </env>
  <ports>
    <port>jolokia.port:8080</port>
  </ports>
  <waitHttp>http://localhost:${jolokia.port}/jolokia</waitHttp>
  <wait>10000</wait>
</configuration>
```
becomes in the new syntax a configuration for two images

```xml
<configuration>
  <images>
    <image>
      <alias>server</alias>
      <name>consol/tomcat-7.0</name>
      <run>
        <volumes>
          <from>data</from>
        </volumes>
        <env>
          <CATALINA_OPTS>-Xmx32m</CATALINA_OPTS>
        </env>
        <ports>
          <port>jolokia.port:8080</port>
        </ports>
        <wait>
          <http>
            <url>http://localhost:${jolokia.port}/jolokia</url>
          </http>
          <time>10000</time>
        </wait>
      </run>
    </image>
    <image>
      <alias>data</alias>
      <name>jolokia/data</name>
      <build>
        <assemblyDescriptor>src/main/docker-assembly.xml</assemblyDescriptor>
      </build>
    </image>
  </images>
</configuration>
```

Please note, that one image only has a `<run>` configuration, the
data image has a `<build>` section only. They both are linked together
during startup of the `server` container via `<volumes>` (where the
symbolic name of the data container can be used). 

## Merged images

When  `mergeData` was true, only a single image was created. So
the original configuration which looks like

```xml
<configuration>
  <mergeData>true</mergeData>
  <image>consol/tomcat-7.0</image>
  <dataImage>jolokia/data</dataImage>
  <assemblyDescriptor>src/main/docker-assembly.xml</assemblyDescriptor>
  <env>
    <CATALINA_OPTS>-Xmx32m</CATALINA_OPTS>
  </env>
  <ports>
    <port>jolokia.port:8080</port>
  </ports>
  <waitHttp>http://localhost:${jolokia.port}/jolokia</waitHttp>
  <wait>10000</wait>
</configuration>
```

can be directly translated to a single image configuration

```xml
<configuration>
  <images>
    <image>
      <name>jolokia/data</name>
      <build>
        <from>consol/tomcat-7.0</from>
        <assemblyDescriptor>src/main/docker-assembly.xml</assemblyDescriptor>
      </build>
      <run>
        <env>
          <CATALINA_OPTS>-Xmx32m</CATALINA_OPTS>
        </env>
        <ports>
          <port>jolokia.port:8080</port>
        </ports>
        <wait>
          <http>
            <url>http://localhost:${jolokia.port}/jolokia</url>
          </http>
          <time>10000</time>
        </wait>
      </run>
    </image>
  </images>
</configuration>
```

### Misc renamings

Some properties where renamed for consistencies sake:

* `url` to `dockerHost` and the corresponding system property
  `docker.url` to `docker.host`
* `color` to `useColor` 
