FROM openjdk:jre

#RUN VERSION=$(mvn -q -Dexec.executable=echo -Dexec.args='${project.version}' --non-recursive exec:exec)
ARG jar_file=target/zero-config-${project.version}.jar

ADD $jar_file /tmp/zero-config.jar
CMD java -cp /tmp/zero-config.jar HelloWorld
