FROM openjdk:jre

ARG version=1-SNAPSHOT
ARG jar_file=target/zero-config-$version.jar

ADD $jar_file /tmp/zero-config.jar
CMD java -cp /tmp/zero-config.jar HelloWorld
