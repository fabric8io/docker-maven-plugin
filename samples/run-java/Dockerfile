FROM openjdk:jre

ADD ${project.build.directory}/${project.build.finalName}.jar /opt/hello-world.jar
ADD target/dependency/run-java-sh/fp-files/run-java.sh /opt
ENV JAVA_MAIN_CLASS HelloWorld
CMD sh /opt/run-java.sh

