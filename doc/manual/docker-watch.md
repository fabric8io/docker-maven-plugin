### docker:watch

When developing and testing applications you will often have to
rebuild Docker images and restart containers. Typing `docker:build`
and `docker:start` all the time is cumbersome. With `docker:watch` you
can enable automatic rebuilding of images and restarting of containers
in case of updates.

`docker:watch` is the top-level goal which perform these tasks. There
are two watch modes, which can be specified in multiple ways:

* `build` : Automatically rebuild one or more Docker images when one
  of the files selected by an assembly changes. This works for all files
  included directly in `assembly.xml` but also for arbitrary dependencies. 
  For example:

        $ mvn package docker:build docker:watch -Ddocker.watch.mode=build

  This mode works only when there is a `<build>` section
  in an image configuration. Otherwise no automatically build will be triggered for an 
  image with only a `<run>` section. Note that you need the `package` phase to be executed before
  otherwise any artifact created by this build can not be included
  into the assembly. As described in the section about `docker:start` this
  is a Maven limitation. 
  
* `run` : Automatically restart container when their associated images
  changes. This is useful if you pull a new version of an image
  externally or especially in combination with the `build` mode to
  restart containers when their image has been automatically
  rebuilt. This mode works reliably only when used together with
  `docker:start`.

        $ mvn docker:start docker:watch -Ddocker.watch.mode=run

The mode can also be `both` or `none` to select both or none of these
variants, respectively. The default is `both`. 

`docker:watch` will run forever until it is interrupted with `CTRL-C`
after which it will stop all containers. Depending on the configuration
parameters `keepContainer` and `removeVolumes` the stopped containers
with their volumes will be removed, too.

When an image is removed while watching it, error messages will be printed out
periodically.  So don't do that ;-)

Dynamically assigned ports stay stable in that they won't change after
a container has been stopped and a new container is created and started. The new
container will try to allocate the same ports as the previous container.

If containers are linked together network or volume wise, and you
update a container which other containers dependent on, the dependant
containers are not restarted for now. E.g. when you have a "service"
container accessing a "db" container and the "db" container is
updated, then you "service" container will fail until it is restarted,
too. A future version of this plugin will take care of restarting
these containers, too (in the right order), but for now you would have
to do this manually.

This maven goal can be configured with the following top-level
parameters:

* **watchMode** `docker.watch.mode`: The watch mode specifies what should be watched
  - `build` : Watch changes in the assembly and rebuild the image in
  case
  - `run` : Watch a container's image whether it changes and restart
  the container in case
  - `both` : `build` and `run` combined
  - `none` : Neither watching for builds nor images. This is useful if
  you use prefactored images which won't be changed and hence don't
  need any watching. `none` is best used on an per image level, see below how this can 
  be specified.
* **watchInterval** `docker.watch.interval` specifies the interval in
  milliseconds how  often to check for changes, which must be larger
  than 100ms. The default are 5 seconds.
* **watchPostGoal** A maven goal which should be called if a rebuild or a restart has 
  been performed. This goal must have the format `<pluginGroupId>:<pluginArtifactId>:<goal>` and 
  the plugin must be configured in the `pom.xml`. For example a post-goal `io.fabric8:fabric8:delete-pods` will 
  trigger the deletion of PODs in Kubernetes which in turn triggers are new start of a POD within the 
  Kubernetes cluster. The value specified here is the the default post goal which can be overridden 
  by `<postGoal>` in a `<watch>` configuration.
* **keepRunning** `docker.keepRunning` if set to `true` all
  container will be kept running after `docker:watch` has been
  stopped. By default this is set to `false`. 
* **keepContainer** `docker.keepContainer` similar to `docker:stop`, if this is set to `true`
  (and `keepRunning` is disabled) then all container will be removed
  after they have been stopped. The default is `true`.
* **removeVolumes** `docker.removeVolumes` if given will remove any
  volumes associated to the container as well. This option will be ignored
  if either `keepContainer` or `keepRunning` are `true`.

Image specific watch configuration goes into an extra image-level
`<watch>` section (i.e. `<image><watch>...</watch></image>`). 
The following parameters are recognized:

* **mode** Each image can be configured for having individual watch mode. These
  take precedence of the global watch mode. The mode specified in this
  configuration takes precedence over the globally specified mode.  
* **interval** The watch interval can be specified in milliseconds on
  image level. If given this will override the global watch interval.
* **postGoal** A post Maven plugin goal after a rebuild or restart. The value here must have the 
  format `<pluginGroupId>:<pluginArtifactId>:<goal>` (e.g. `io.fabric8:fabric8:delete-pods`)
  
Here is an example how the watch mode can be tuned:

````xml
<configuration>
   <!-- Check every 10 seconds by default -->
   <watchInterval>10000</watchInterval>
   <!-- Watch for doing rebuilds and restarts --> 
   <watchMode>both</watch>
   <images>
      <image>
         <!-- Service checks every 5 seconds -->
         <alias>service</alias>
         ....
         <watch>
            <interval>5000</interval>
         </watch>
      </image>
      <image>
         <!-- Database needs no watching -->
         <alias>db<alias>
         ....
         <watch>
            <mode>none</mode>
         </watch>
      </image>
      ....   
   </images>
</configuration>
````

Given this configuration 

````sh
mvn package docker:build docker:start docker:watch
````

you can build the service image, start up all containers and go into a watch
loop. Again, you need the `package` phase in order that the assembly
can find the artifact build by this project. This is a Maven
limitation. The `db` image will never be watch since it assumed to not change 
while watching. 

