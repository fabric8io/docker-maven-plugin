### docker:build

This goal will build all images which have a `<build>` configuration
section, or, if the global configuration `image` is set, only those
images contained in this variable will be build. 

All build relevant configuration is contained in the `<build>` section
of an image configuration. The available subelements are

* **assembly** specifies the assembly configuration as described in
  [Build Assembly](#build-assembly)
* **cleanup** indicates if dangling (untagged) images should be cleaned up after each build (including any containers
  created from them). Default is `true`   
* **cmd** A command to execute by default (i.e. if no command
  is provided when a container for this image is started). See 
  [Start-up Arguments](#start-up-arguments) for details.
* **entrypoint** An entrypoint allows you to configure a container that will run as an executable. 
  See [Start-up Arguments](#start-up-arguments) for details.
* **env** holds environments as described in
  [Setting Environment Variables and Labels](#setting-environment-variables-and-labels). 
* **from** specifies the base image which should be used for this
  image. If not given this default to `busybox:latest` and is suitable
  for a pure data image.
* **labels** holds labels  as described in
  [Setting Environment Variables and Labels](#setting-environment-variables-and-labels). 
* **maintainer** specifies the author (MAINTAINER) field for the generated image
* **ports** describes the exports ports. It contains a list of
  `<port>` elements, one for each port to expose.
* **runCmds** specifies commands to be run during the build process. It contains **run** elements 
  which are passed to bash. The run commands are inserted right after the assembly and after **workdir** in to the
  Dockerfile. This tag is not to be confused with the `<run>` section for this image which specifies the runtime
  behaviour when starting containers. 
* **optimise** if set to true then it will compress all the `runCmds` into a single RUN directive so that only one image layer is created.
* **compression** is the compression mode how the build archive is transmitted to the docker daemon (`docker:build`) and how 
  docker build archives are attached to this build as sources (`docker:source`). The value can be `none` (default), 
  `gzip` or `bzip2`. 
* **skip** if set to true disables building of the image. This config option is best used together with a maven property
* **tags** contains a list of additional `tag` elements with which an
  image is to be tagged after the build.
* **volumes** contains a list of `volume` elements to create a container
  volume.
* **workdir** the directory to change to when starting the container.

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
  
  <entryPoint>
    <!-- exec form for ENTRYPOINT -->
    <exec>
      <arg>java</arg>
      <arg>-jar</arg>
      <arg>/opt/demo/server.jar</arg>
    </exec>
  </entryPoint>

  <assembly>
    <mode>dir</mode>
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
* **mode** specifies how the assembled files should be collected. By default the files a simply
  copied (`dir`), but can be set to be a Tar- (`tar`), compressed Tar- (`tgz`) or Zip- (`zip`) Archive. 
  The archive formats have the advantage that file permission can be preserved better (since the copying is 
  independent from the underlying files systems), but might triggers internal bugs from the Maven assembler (as 
  it has been in #171)
* **user** can be used to specify the user and group under which the files should be added. The user must be already exist in 
  the base image. It has the general format 
  `user[:group[:run-user]]`. The user and group can be given either as numeric user- and group-id or as names. The group 
  id is optional. If a third part is given, then the build changes to user `root` before changing the ownerships, 
  changes the ownerships and then change to user `run-user` which is then used for the final command to execute. This feature
  might be needed, if the base image already changed the user (e.g. to 'jboss') so that a `chown` from root to this user would fail. 
  For example, the image `jboss/wildfly` use a "jboss" user under which all commands are executed. Adding files in Docker
  always happens under the UID root. These files can only be changed to "jboss" is the `chown` command is executed as root. 
  For the following commands to be run again as "jboss" (like the final `standalone.sh`), the plugin switches back to 
  user `jboss` (this is this "run-user") after changing the file ownership. For this example a specification of 
  `jboss:jboss:jboss` would be required. 

In the event you do not need to include any artifacts with the image, you may
safely omit this element from the configuration.

##### Start-up Arguments

Using `entryPoint` and `cmd` it is possible to specify the [entry point](https://docs.docker.com/reference/builder/#entrypoint) 
or [cmd](https://docs.docker.com/reference/builder/#cmd) for a container.

The difference is, that an `entrypoint` is the command that always be executed, with the `cmd` as argument.
If no `entryPoint` is provided, it defaults to `/bin/sh -c` so any `cmd` given is executed 
with a shell. The arguments given to `docker run` are always given as arguments to the 
`entrypoint`, overriding any given `cmd` option. On the other hand if no extra arguments
are given to `docker run` the default `cmd` is used as argument to `entrypoint`. See also
this [stackoverflow question](http://stackoverflow.com/questions/21553353/what-is-the-difference-between-cmd-and-entrypoint-in-a-dockerfile)
for an even more detailed explanation.

A entry point or command can be specified in two alternative formats:

* **shell** shell form in which the whole line is given to `shell -c` for interpretation.
* **exec** list of arguments (with inner `<args>`) arguments which will be given to the `exec` call directly without any shell interpretation. 

Either shell or params should be specified. 

Example:
 
```xml
<entryPoint>
   <!-- shell form  -->
   <shell>java -jar $HOME/server.jar</shell>
</entryPoint>
```

or 

```xml
<entryPoint>
   <!-- exec form  -->
   <exec>
     <args>java</args>
     <args>-jar</args>
     <args>/opt/demo/server.jar</args>
   </exec>
</entryPoint>
```

This can be formulated also more dense with:

```xml
<!-- shell form  -->
<entryPoint>java -jar $HOME/server.jar</entryPoint>
```

or 

```xml
<entryPoint>
  <!-- exec form  -->
  <arg>java</arg>
  <arg>-jar</arg>
  <arg>/opt/demo/server.jar</arg>
</entryPoint>
```

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
  all its dependencies. Also, when a `classpath` file exists in the target 
  directory, this will be added to.
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
  <finalName>your-desired-final-name</finalName>
  ...
</build>
```

Please note, based upon the following documentation listed [here](http://maven.apache.org/pom.html#BaseBuild_Element),
there is no guarantee the plugin creating your artifact will honor it in which case you will need to use a custom
descriptor like above to achieve the desired naming.

Currently the `jar` and `war` plugins properly honor the usage of `finalName`.

