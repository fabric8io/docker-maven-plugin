# Spring Boot Sample with JIB Build Mode

This is also a Spring Boot application to demonstrate how Docker Maven Plugin handles build workflows by 
integrating with [JIB](https://github.com/GoogleContainerTools/jib) which makes Docker Maven Plugin independent of docker
daemon.

### How to Build?
You can compile project as usual by issuing a simple `mvn clean install` command.

## Standard Configuration
You can build images in JIB mode by just setting `docker.build.jib=true`. Plugin would switch to JIB Build and build your image tarball into project's output directory. You can either copy this tarball and load into some docker daemon or push your image to some registry. I will be proceeding with building image and pushing to docker hub. So I would modify `pom.xml` properties to set `docker.user` to my dockerhub username:
```.xml
 <docker.user>exampleuser</docker.user>
```

### Standard Configuration Build:

You can see that my local docker daemon is not running:
```.shell script
# Check Docker Daemon Status
spring-boot-with-jib : $ systemctl status docker
● docker.service - Docker Application Container Engine
     Loaded: loaded (/usr/lib/systemd/system/docker.service; disabled; vendor preset: disabled)
     Active: inactive (dead) since Sat 2020-08-29 16:55:32 IST; 3min 2s ago
TriggeredBy: ● docker.socket
       Docs: https://docs.docker.com
   Main PID: 4631 (code=exited, status=0/SUCCESS)

Aug 29 16:49:28 localhost.localdomain dockerd[4639]: time="2020-08-29T16:49:28.401304638+05:30" level=info msg="shim reaped" id=3c5e5b5555fdd8a020bf248d1a7e09d1578de62c0130>
Aug 29 16:49:28 localhost.localdomain dockerd[4631]: time="2020-08-29T16:49:28.410926927+05:30" level=info msg="ignoring event" module=libcontainerd namespace=moby topic=/t>
Aug 29 16:55:31 localhost.localdomain systemd[1]: Stopping Docker Application Container Engine...
Aug 29 16:55:31 localhost.localdomain dockerd[4631]: time="2020-08-29T16:55:31.170170702+05:30" level=info msg="Processing signal 'terminated'"
Aug 29 16:55:31 localhost.localdomain dockerd[4631]: time="2020-08-29T16:55:31.174848312+05:30" level=info msg="Daemon shutdown complete"
Aug 29 16:55:31 localhost.localdomain dockerd[4631]: time="2020-08-29T16:55:31.174895349+05:30" level=info msg="stopping event stream following graceful shutdown" error="co>
Aug 29 16:55:31 localhost.localdomain dockerd[4631]: time="2020-08-29T16:55:31.175137692+05:30" level=info msg="stopping healthcheck following graceful shutdown" module=lib>
Aug 29 16:55:31 localhost.localdomain dockerd[4631]: time="2020-08-29T16:55:31.175219226+05:30" level=info msg="stopping event stream following graceful shutdown" error="co>
Aug 29 16:55:32 localhost.localdomain systemd[1]: docker.service: Succeeded.
Aug 29 16:55:32 localhost.localdomain systemd[1]: Stopped Docker Application Container Engine.
spring-boot-with-jib : $ 

```
To build project issue this command:
> mvn docker:build
```.text
[INFO] Scanning for projects...
[INFO] 
[INFO] ---------< io.fabric8.dmp.samples:dmp-sample-spring-boot-jib >----------
[INFO] Building Docker Maven Plugin :: Spring Boot JIB 0.38.0
[INFO] --------------------------------[ jar ]---------------------------------
[INFO] 
[INFO] --- docker-maven-plugin:0.38.0:build (default-cli) @ dmp-sample-spring-boot-jib ---
[INFO] DOCKER> Building Container image with JIB(Java Image Builder) mode
[INFO] DOCKER> JIB image build started
[INFO] DOCKER> Preparing assembly files
[INFO] Copying files to /home/rohaan/work/repos/docker-maven-plugin/samples/spring-boot-with-jib/target/docker/rohankanojia/spring-boot-dmp-sample-jib/build/maven
[INFO] Building tar: /home/rohaan/work/repos/docker-maven-plugin/samples/spring-boot-with-jib/target/docker/rohankanojia/spring-boot-dmp-sample-jib/tmp/docker-build.tar
[WARNING] Cannot include project artifact: io.fabric8.dmp.samples:dmp-sample-spring-boot-jib:jar:0.38.0; it doesn't have an associated file or directory.
[WARNING] The following patterns were never triggered in this artifact inclusion filter:
o  'io.fabric8.dmp.samples:dmp-sample-spring-boot-jib'
JIB> Base image 'fabric8/java-centos-openjdk8-jdk:1.5.6' does not use a specific image digest - build may not be reproducible
JIB> Containerizing application with the following files:                                                                    
JIB> 	:                                                                                                                      
JIB> 		/home/rohaan/work/repos/docker-maven-plugin/samples/spring-boot-with-jib/target/docker/rohankanojia/spring-boot-dmp-sample-jib/build/maven
JIB> 		/home/rohaan/work/repos/docker-maven-plugin/samples/spring-boot-with-jib/target/docker/rohankanojia/spring-boot-dmp-sample-jib/build/maven/dmp-sample-spring-boot-jib-0.38.0.jar
JIB> 	:                                                                                                                      
JIB> 		/home/rohaan/work/repos/docker-maven-plugin/samples/spring-boot-with-jib/target/docker/rohankanojia/spring-boot-dmp-sample-jib/build/Dockerfile
JIB> Getting manifest for base image fabric8/java-centos-openjdk8-jdk:1.5.6...                                               
JIB> Building  layer...                                                                                                      
JIB> Building  layer...                                                                                                      
JIB> The base image requires auth. Trying again for fabric8/java-centos-openjdk8-jdk:1.5.6...                                
JIB> Retrieving registry credentials for registry-1.docker.io...                                                             
JIB> Using base image with digest: sha256:92530aa1eb4c49e3b1d033f94e9cd4dc891d49922459e13f84e59c9d68d800eb                   
JIB> Container program arguments set to [java, -jar, /maven/dmp-sample-spring-boot-jib-0.38.0.jar]                    
JIB> Building image to tar file...                                                                                           
JIB> [========================      ] 80.0% complete > writing to tar file
JIB> [==============================] 100.0% complete
[INFO] DOCKER>  /home/rohaan/work/repos/docker-maven-plugin/samples/spring-boot-with-jib/target/docker/rohankanojia/spring-boot-dmp-sample-jib/tmp/docker-build.tar successfully built
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  13.587 s
[INFO] Finished at: 2020-08-29T17:08:10+05:30
[INFO] ------------------------------------------------------------------------
```

JIB build creates a tarball as image output(You can see in logs that it shows the build image tarball). You can then load this image into some other docker daemon like this:
```
spring-boot-with-jib : $ docker load -i /home/rohaan/work/repos/docker-maven-plugin/samples/spring-boot-with-jib/target/docker/rohankanojia/spring-boot-dmp-sample-jib/tmp/docker-build.tar
f38418c08d5d: Loading layer [==================================================>]  17.85MB/17.85MB
f1ef686aa9e0: Loading layer [==================================================>]     215B/215B
Loaded image: rohankanojia/spring-boot-dmp-sample-jib:latest
```

**Note**: Make sure that `docker.build.jib` is disabled when you're using other plugin goals. Jib mode does not work with other goals. 
After this, you can use [`docker:start`](http://dmp.fabric8.io/#docker:start) as usual

Or you may want to push image to some registry as described in next section.
### Zero Configuration Push
In order to push image, you need to make sure that your image name is set with respect to your registry. I'm going to push the image to docker hub so `${docker.user}/spring-boot-dmp-sample-jib` would be my image name.

You can push image with [`docker:push`](http://dmp.fabric8.io/#docker:push) goal as usual, I already have `docker.build.jib=true` set in project properties:
```
spring-boot-with-jib : $ mvn docker:push
[INFO] Scanning for projects...
[INFO] 
[INFO] ---------< io.fabric8.dmp.samples:dmp-sample-spring-boot-jib >----------
[INFO] Building Docker Maven Plugin :: Spring Boot JIB 0.38.0
[INFO] --------------------------------[ jar ]---------------------------------
[INFO] 
[INFO] --- docker-maven-plugin:0.38.0:push (default-cli) @ dmp-sample-spring-boot-jib ---
[INFO] DOCKER> Pushing Container image with JIB(Java Image Builder) mode
[INFO] DOCKER> This push refers to: rohankanojia/spring-boot-dmp-sample-jib
JIB> Containerizing application with the following files:                                                                    
JIB> Retrieving registry credentials for registry-1.docker.io...                                                             
JIB> Container program arguments set to [java, -jar, /maven/dmp-sample-spring-boot-jib-0.38.0.jar] (inherited from base image)
JIB> Pushing manifest for latest...                                                                                          

JIB> [==============================] 100.0% complete
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  02:57 min
[INFO] Finished at: 2020-08-29T17:36:11+05:30
[INFO] ------------------------------------------------------------------------
spring-boot-with-jib : $ 
```

## JIB with Customized Assembly
This profile tries to add some extra files inside the image. If you see there is an extra directory `static` in project base directory. It is the copied to target image.

### JIB with Customized Assembly Build
Now to build you need to issue same build goal but with different profile, build goal generates a tarball which needs to be loaded into your docker daemon afterwards. Or maybe you can push it to some registry:
>  mvn docker:build -PJib-With-Assembly
```
spring-boot-with-jib : $ mvn docker:build -PJib-With-Assembly
[INFO] Scanning for projects...
[INFO] 
[INFO] ---------< io.fabric8.dmp.samples:dmp-sample-spring-boot-jib >----------
[INFO] Building Docker Maven Plugin :: Spring Boot JIB 0.38.0
[INFO] --------------------------------[ jar ]---------------------------------
[INFO] 
[INFO] --- docker-maven-plugin:0.38.0:build (default-cli) @ dmp-sample-spring-boot-jib ---
[INFO] DOCKER> Building Container image with JIB(Java Image Builder) mode
[INFO] DOCKER> JIB image build started
[INFO] DOCKER> Preparing assembly files
[INFO] Copying files to /home/rohaan/work/repos/docker-maven-plugin/samples/spring-boot-with-jib/target/docker/rohankanojia/spring-boot-sample/build/my-project-assembly
JIB> Base image 'fabric8/java-centos-openjdk8-jdk:1.5.6' does not use a specific image digest - build may not be reproducible
JIB> Containerizing application with the following files:                                                                    
JIB> 	:                                                                                                                      
JIB> 		/home/rohaan/work/repos/docker-maven-plugin/samples/spring-boot-with-jib/target/docker/rohankanojia/spring-boot-sample/build/my-project-assembly
JIB> 		/home/rohaan/work/repos/docker-maven-plugin/samples/spring-boot-with-jib/target/docker/rohankanojia/spring-boot-sample/build/my-project-assembly/static
JIB> 		/home/rohaan/work/repos/docker-maven-plugin/samples/spring-boot-with-jib/target/docker/rohankanojia/spring-boot-sample/build/my-project-assembly/static/testFile.txt
JIB> 		/home/rohaan/work/repos/docker-maven-plugin/samples/spring-boot-with-jib/target/docker/rohankanojia/spring-boot-sample/build/my-project-assembly/dmp-sample-spring-boot-jib-0.38.0.jar
JIB> 	:                                                                                                                      
JIB> 		/home/rohaan/work/repos/docker-maven-plugin/samples/spring-boot-with-jib/target/docker/rohankanojia/spring-boot-sample/build/Dockerfile
JIB> Getting manifest for base image fabric8/java-centos-openjdk8-jdk:1.5.6...                                               
JIB> Building  layer...                                                                                                      
JIB> Building  layer...                                                                                                      
JIB> The base image requires auth. Trying again for fabric8/java-centos-openjdk8-jdk:1.5.6...                                
JIB> Retrieving registry credentials for registry-1.docker.io...                                                             
JIB> Using base image with digest: sha256:92530aa1eb4c49e3b1d033f94e9cd4dc891d49922459e13f84e59c9d68d800eb                   
JIB> Container program arguments set to [java, -jar, /my-project-assembly/dmp-sample-spring-boot-jib-0.38.0.jar]      
JIB> Building image to tar file...                                                                                           
JIB> [========================      ] 80.0% complete > writing to tar file
JIB> [==============================] 100.0% complete
[INFO] DOCKER>  /home/rohaan/work/repos/docker-maven-plugin/samples/spring-boot-with-jib/target/docker/rohankanojia/spring-boot-sample/tmp/docker-build.tar successfully built
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  13.173 s
[INFO] Finished at: 2020-08-29T17:40:13+05:30
[INFO] ------------------------------------------------------------------------
```

### Jib With Customized Assembly Push
Pushing image to docker hub in this case, you can provide registry credentials in plugin XML config, in `~/.m2/settings.xml` or in `~/.docker/config.json`:
> mvn docker:push -PJib-With-Assembly 
```
spring-boot-with-jib : $ mvn docker:push -PJib-With-Assembly
[INFO] Scanning for projects...
[INFO] 
[INFO] ---------< io.fabric8.dmp.samples:dmp-sample-spring-boot-jib >----------
[INFO] Building Docker Maven Plugin :: Spring Boot JIB 0.38.0
[INFO] --------------------------------[ jar ]---------------------------------
[INFO] 
[INFO] --- docker-maven-plugin:0.38.0:push (default-cli) @ dmp-sample-spring-boot-jib ---
[INFO] DOCKER> Pushing Container image with JIB(Java Image Builder) mode
[INFO] DOCKER> This push refers to: rohankanojia/spring-boot-sample
JIB> Containerizing application with the following files:                                                                    
JIB> Retrieving registry credentials for registry-1.docker.io...                                                             
JIB> Container program arguments set to [java, -jar, /my-project-assembly/dmp-sample-spring-boot-jib-0.38.0.jar] (inherited from base image)
JIB> [====================          ] 66.7% complete > pushing blob sha256:0a2b4dc56d7e822495adcdfa8a2c2316e9ae420d84a6JIB> Skipping push; BLOB already exists on target registry : digest: sha256:707713df6baa24192428099d6444fed7fe3c91dc10bad2742b7350f0147b1b3b, size: 8851
JIB> [====================          ] 67.7% complete > pushing blob sha256:0a2b4dc56d7e822495adcdfa8a2c2316e9ae420d84a6JIB> Skipping push; BLOB already exists on target registry : digest: sha256:109b828942fdee847c02ec43b13105bf0d1bc1701675a4306781193f11c997df, size: 6543
JIB> [=====================         ] 68.7% complete > pushing blob sha256:0a2b4dc56d7e822495adcdfa8a2c2316e9ae420d84a6JIB> Skipping push; BLOB already exists on target registry : digest: sha256:a44f5f69f08a44895565ad9cdfa05f3378a46205cf81c64de068efed4f644a46, size: 467
JIB> Skipping push; BLOB already exists on target registry : digest: sha256:8e2fef958d6c5532e73960cab9b4acf1de2fa0dac8ce1d404751de240157e5cd, size: 739790
JIB> [=====================         ] 70.7% complete > pushing blob sha256:0a2b4dc56d7e822495adcdfa8a2c2316e9ae420d84a6JIB> Skipping push; BLOB already exists on target registry : digest: sha256:743ce65d298dc1684e06ba2d255db095cceaf75060dcbdff43baa8a7e525255a, size: 100
JIB> [=====================         ] 69.7% complete > pushing blob sha256:0a2b4dc56d7e822495adcdfa8a2c2316e9ae420d84a6JIB> Skipping push; BLOB already exists on target registry : digest: sha256:ac9208207adaac3a48e54a4dc6b49c69e78c3072d2b3add7efdabf814db2133b, size: 75161332
JIB> [======================        ] 72.7% complete > pushing blob sha256:0a2b4dc56d7e822495adcdfa8a2c2316e9ae420d84a6JIB> [======================        ] 71.7% complete > pushing blob sha256:0a2b4dc56d7e822495adcdfa8a2c2316e9ae420d84a6JIB> Skipping push; BLOB already exists on target registry : digest: sha256:6b82b8ee52d8ef43d1b35637e65d9134277e63fd23316516c084ab0dad7a017b, size: 1375
JIB> [======================        ] 73.7% complete > pushing blob sha256:0a2b4dc56d7e822495adcdfa8a2c2316e9ae420d84a6JIB> Skipping push; BLOB already exists on target registry : digest: sha256:0a2b4dc56d7e822495adcdfa8a2c2316e9ae420d84a6a5e8e9d0b505a5fddcd1, size: 6539
JIB> [======================        ] 74.7% complete > pushing blob sha256:46839fd680b76fed7005bc63ae1207193c129051c10dJIB> Skipping push; BLOB already exists on target registry : digest: sha256:08f3d26bbf994fd2c8ce6418a0b090bee22878c7998f0e78b7247a860411bbb2, size: 86409418
JIB> [=======================       ] 75.8% complete > pushing blob sha256:46839fd680b76fed7005bc63ae1207193c129051c10dJIB> Skipping push; BLOB already exists on target registry : digest: sha256:99230c1ff85faaf8fd0c9ba8db6b20d009db4fb35f17f7b6a24a6baa6821c306, size: 4702
JIB> [==========================    ] 86.9% complete > pushing blob sha256:46839fd680b76fed7005bc63ae1207193c129051c10dJIB> Skipping push; BLOB already exists on target registry : digest: sha256:18e8deddb3f584f36c47b481067a6fa0a3a977b71df199a8d89ee8720b9c587c, size: 239
JIB> [==========================    ] 87.9% complete > pushing blob sha256:46839fd680b76fed7005bc63ae1207193c129051c10dJIB> [==========================    ] 87.9% complete > pushing blob sha256:46839fd680b76fed7005bc63ae1207193c129051c10dJIB> [==========================    ] 87.9% complete > pushing blob sha256:46839fd680b76fed7005bc63ae1207193c129051c10dJIB> [==========================    ] 87.9% complete > pushing blob sha256:46839fd680b76fed7005bc63ae1207193c129051c10dJIB> [==========================    ] 87.9% complete > pushing blob sha256:46839fd680b76fed7005bc63ae1207193c129051c10dJIB> [==========================    ] 87.9% complete > pushing blob sha256:46839fd680b76fed7005bc63ae1207193c129051c10dJIB> [==========================    ] 87.9% complete > pushing blob sha256:46839fd680b76fed7005bc63ae1207193c129051c10dJIB> [==========================    ] 88.0% complete > pushing blob sha256:46839fd680b76fed7005bc63ae1207193c129051c10dJIB> [==========================    ] 88.0% complete > pushing blob sha256:46839fd680b76fed7005bc63ae1207193c129051c10dJIB> [==========================    ] 88.0% complete > pushing blob sha256:46839fd680b76fed7005bc63ae1207193c129051c10dJIB> [==========================    ] 88.1% complete > pushing blob sha256:46839fd680b76fed7005bc63ae1207193c129051c10dJIB> [==========================    ] 88.1% complete > pushing blob sha256:46839fd680b76fed7005bc63ae1207193c129051c10dJIB> [==========================    ] 88.2% complete > pushing blob sha256:46839fd680b76fed7005bc63ae1207193c129051c10dJIB> [==========================    ] 88.3% complete > pushing blob sha256:46839fd680b76fed7005bc63ae1207193c129051c10dJIB> [==========================    ] 88.3% complete > pushing blob sha256:46839fd680b76fed7005bc63ae1207193c129051c10dJIB> [===========================   ] 88.4% complete > pushing blob sha256:46839fd680b76fed7005bc63ae1207193c129051c10dJIB> [===========================   ] 88.5% complete > pushing blob sha256:46839fd680b76fed7005bc63ae1207193c129051c10dJIB> [===========================   ] 88.6% complete > pushing blob sha256:46839fd680b76fed7005bc63ae1207193c129051c10dJIB> [===========================   ] 88.7% complete > pushing blob sha256:46839fd680b76fed7005bc63ae1207193c129051c10dJIB> [===========================   ] 88.8% complete > pushing blob sha256:46839fd680b76fed7005bc63ae1207193c129051c10dJIB> [===========================   ] 88.8% complete > pushing blob sha256:46839fd680b76fed7005bc63ae1207193c129051c10dJIB> Pushing manifest for latest...                                                                                          

JIB> [==============================] 100.0% complete
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  29.416 s
[INFO] Finished at: 2020-08-29T17:43:57+05:30
[INFO] ------------------------------------------------------------------------
```

You can check afterwards whether the loaded container contains the file you mentioned in assembly or not:
```
spring-boot-with-jib : $ docker container run -it rohankanojia/spring-boot-sample:latest /bin/bash
[root@c402fe994291 /]# ls     
Dockerfile         bin          dev  home  lib64  mnt                  opt   root  sbin  sys  usr
anaconda-post.log  deployments  etc  lib   media  my-project-assembly  proc  run   srv   tmp  var
[root@c402fe994291 /]# ls my-project-assembly/
dmp-sample-spring-boot-jib-0.38.0.jar  static
[root@c402fe994291 /]# ls my-project-assembly/static/
testFile.txt
[root@c402fe994291 /]# cat my-project-assembly/static/testFile.txt 
I should be present
[root@c402fe994291 /]# exit
exit
```

### Registry Configuration
You can provide registry credentials in 3 formats:
- You can do a `docker login`(for Docker Hub) or `docker login <your-registry` and plugin would read your `~/.docker/config.json` file
- You can provide registry credentials in your `~/.m2/settings.xml` file like this and plugin would read it from there:
```
<?xml version="1.0" encoding="UTF-8"?>
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0 http://maven.apache.org/xsd/settings-1.0.0.xsd">

  <servers>
    <server>
      <id>docker.io</id>
      <username>testuser</username>
      <password>testpassword</password>
    </server>
    <server>
      <id>quay.io</id>
      <username>testuser</username>
      <password>testpassword</password>
    </server>
  </servers>

</settings>

```

-  You can provide registry credentials as part of XML configuration:
```
<plugin>
    <groupId>org.eclipse.jkube</groupId>
    <artifactId>kubernetes-maven-plugin</artifactId>
    <version>${project.version}</version>
    <configuration>
        <images>
            <!-- Your Image Configuration -->  
        </images>
        <authConfig>
          <username>testuser</username>
          <password>testpassword</password>
        </authConfig>
    </configuration>
</plugin>

```
