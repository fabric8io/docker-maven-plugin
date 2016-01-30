### docker:source

The `docker:source` target can be used to attach a docker build
archive containing the Dockerfile and all added files to the Maven
project with a certain classifier. It reuses the configuration from
[docker:build](docker-build.md).

`docker:source` uses the image's [alias](image-configuration.md) as
part of the classifier, so it is mandatory that the alias is set for
this goal to work. The classifier is calculated as `docker-<alias>` so
when the alias is set to `service`, then the classifier is
`docker-service`. 

`docker:source` can be attached to a Maven execution phase, which is
`generate-sources` by default.

For example, this configuration will attach the docker build archive
to the artifacts to store in the repository:

````xml
<plugin>
  <artifactId>docker-maven-plugin</artifactId>
  <!-- ..... -->
  <executions>
     <execution>
       <id>sources</id>
       <goals>
         <goal>source</goal>
       </goals>
     </execution>
  </executions>
</plugin>
````

If the plugin is not bound to an execute phase but called directly, it must be ensured that the package phase is 
called, too. That is required to find the artifacts created with this build when referenced from an assembly. 