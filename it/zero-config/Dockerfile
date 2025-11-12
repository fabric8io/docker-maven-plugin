FROM eclipse-temurin:21-jre-alpine

#RUN VERSION=$(mvn -q -Dexec.executable=echo -Dexec.args='${project.version}' --non-recursive exec:exec)
ARG jar_file=target/dmp-it-zero-config-${project.version}.jar

ADD $jar_file /tmp/zero-config.jar
CMD java -cp /tmp/zero-config.jar HelloWorld
