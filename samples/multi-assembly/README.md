# Spring Boot Sample with multiple layers

This is also a Spring Boot application to demonstrate how Docker Maven Plugin handles build workflows 
using multiple assembly layers.

### How to Build?
You can compile project as usual by issuing a simple `mvn clean install` command.

## Standard Configuration
You can build images with multiple layers by adding multiple assemblies. Replace the existing assembly configuration:
```xml
<build>
    <from>fabric8/java-centos-openjdk8-jdk:1.5.6</from>
    <assembly>
        <descriptorRef>artifact-with-dependencies</descriptorRef>
        <targetDir>/app</targetDir>
    </assembly>
    <cmd>java -jar /app/${project.artifactId}-${project.version}.jar</cmd>
</build>
```
with multiple assemblies:
```xml
<build>
    <from>fabric8/java-centos-openjdk8-jdk:1.5.6</from>
    <assemblies>
        <assembly>
            <descriptorRef>dependencies</descriptorRef>
            <name>deps</name>
            <targetDir>/app/lib</targetDir>
        </assembly>
        <assembly>
            <descriptorRef>artifact</descriptorRef>
            <targetDir>/app</targetDir>
        </assembly>
    </assemblies>
    <cmd>java -jar /app/${project.artifactId}-${project.version}.jar</cmd>
    <ports>
        <port>8080</port>
    </ports>
</build>
```

### Standard Configuration Build:

To build project issue this command:
> mvn package
```text
[INFO] Scanning for projects...
[INFO]
[INFO] ----------< io.fabric8.dmp.samples:dmp-sample-multi-assembly >----------
[INFO] Building dmp-sample-multi-assembly 0.37.0
[INFO] --------------------------------[ jar ]---------------------------------
[INFO]
[INFO] --- maven-resources-plugin:2.6:resources (default-resources) @ dmp-sample-multi-assembly ---
[INFO] Using 'UTF-8' encoding to copy filtered resources.
[INFO] skip non existing resourceDirectory C:\Source\Other\docker-maven-plugin\samples\multi-assembly\src\main\resources
[INFO]
[INFO] --- maven-compiler-plugin:3.1:compile (default-compile) @ dmp-sample-multi-assembly ---
[INFO] Compiling 1 source file to C:\Source\Other\docker-maven-plugin\samples\multi-assembly\target\classes
[INFO]
[INFO] --- maven-resources-plugin:2.6:testResources (default-testResources) @ dmp-sample-multi-assembly ---
[INFO] Using 'UTF-8' encoding to copy filtered resources.
[INFO] skip non existing resourceDirectory C:\Source\Other\docker-maven-plugin\samples\multi-assembly\src\test\resources
[INFO]
[INFO] --- maven-compiler-plugin:3.1:testCompile (default-testCompile) @ dmp-sample-multi-assembly ---
[INFO] No sources to compile
[INFO]
[INFO] --- maven-surefire-plugin:2.12.4:test (default-test) @ dmp-sample-multi-assembly ---
[INFO] No tests to run.
[INFO]
[INFO] --- maven-jar-plugin:3.2.0:jar (default-jar) @ dmp-sample-multi-assembly ---
[INFO] Building jar: C:\Source\Other\docker-maven-plugin\samples\multi-assembly\target\dmp-sample-multi-assembly-0.37.0.jar
[INFO]
[INFO] --- docker-maven-plugin:0.37.0:build (docker-build) @ dmp-sample-multi-assembly ---
[INFO] Copying files to C:\Source\Other\docker-maven-plugin\samples\multi-assembly\target\docker\fabric8\dmp-sample-multi-assembly\build\deps
[INFO] Copying files to C:\Source\Other\docker-maven-plugin\samples\multi-assembly\target\docker\fabric8\dmp-sample-multi-assembly\build\maven
[INFO] Building tar: C:\Source\Other\docker-maven-plugin\samples\multi-assembly\target\docker\fabric8\dmp-sample-multi-assembly\tmp\docker-build.tar
[INFO] DOCKER> [fabric8/dmp-sample-multi-assembly:latest]: Created docker-build.tar in 3 seconds
[INFO] DOCKER> Step 1/5 : FROM fabric8/java-centos-openjdk8-jdk:1.5.6
[INFO] DOCKER>
[INFO] DOCKER> ---> 34854f349ada
[INFO] DOCKER> Step 2/5 : EXPOSE 8080
[INFO] DOCKER>
[INFO] DOCKER> ---> Using cache
[INFO] DOCKER> ---> 2c5801f68060
[INFO] DOCKER> Step 3/5 : COPY deps /app/lib/
[INFO] DOCKER>
[INFO] DOCKER> ---> Using cache
[INFO] DOCKER> ---> 5e275cc1bdd3
[INFO] DOCKER> Step 4/5 : COPY maven /app/
[INFO] DOCKER>
[INFO] DOCKER> ---> 95f28fe7c902
[INFO] DOCKER> Step 5/5 : CMD java -jar /app/dmp-sample-multi-assembly-0.37.0.jar
[INFO] DOCKER>
[INFO] DOCKER> ---> Running in 6a784e62c7db
[INFO] DOCKER> Removing intermediate container 6a784e62c7db
[INFO] DOCKER> ---> 97d2a21984a2
[INFO] DOCKER> Successfully built 97d2a21984a2
[INFO] DOCKER> Successfully tagged fabric8/dmp-sample-multi-assembly:latest
[INFO] DOCKER> [fabric8/dmp-sample-multi-assembly:latest]: Built image sha256:97d2a
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  7.855 s
[INFO] Finished at: 2020-10-27T12:44:36-04:00
[INFO] ------------------------------------------------------------------------
```

You can verify that the image contains multiple layers using:
> docker history --no-trunc fabric8/dmp-sample-multi-assembly:latest
```text
IMAGE                                                                     CREATED             CREATED BY                                                                                                                                               SIZE                COMMENT
sha256:97d2a21984a25b3e278a784c2ef8078fcf677d322e6e73bb847baf8ee6bd7ef4   9 minutes ago       /bin/sh -c #(nop)  CMD ["/bin/sh" "-c" "java -jar /app/dmp-sample-multi-assembly-0.37.0.jar"]                                                     0B
sha256:95f28fe7c9021870b123580d608b0b98da56507507873e354868235373363fce   9 minutes ago       /bin/sh -c #(nop) COPY dir:a8f75e88983c2f1f68b80d3566291efed7b22db12c90389955fba7c78777a078 in /app/                                                     11.2kB
sha256:5e275cc1bdd35f730d79c96918986a313917f3fbdba57a1ff44d977361a11739   12 days ago         /bin/sh -c #(nop) COPY dir:a04b4e112f340915a2989954b7849d7b1426bac844c51935882d24dd3eacee06 in /app/lib/                                                 13.6MB
sha256:2c5801f680604456b3111fb0a567c7403989478fe01fd0c81ab56f0ef28b7dca   12 days ago         /bin/sh -c #(nop)  EXPOSE 8080                                                                                                                           0B
sha256:34854f349ada36bdba0a1fe011d6826191ca0a08ee333b6bd3645911e1856e93   18 months ago       /bin/sh -c #(nop)  CMD ["/deployments/run-java.sh"]                                                                                                      0B
...              
```
