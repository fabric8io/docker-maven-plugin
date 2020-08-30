FROM openjdk:jre

ARG jar_file=target/dmp-sample-zero-config.jar

ARG FOO=${}
ADD $jar_file /tmp/zero-config.jar
CMD java -cp /tmp/zero-config.jar HelloWorld
