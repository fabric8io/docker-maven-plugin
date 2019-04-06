FROM openjdk:jre

ADD target/${project.build.finalName}.jar /opt/hello-world.jar
ADD target/docker-extra/run-java/run-java.sh /opt

# See https://github.com/fabric8io-images/run-java-sh/ for more information
# about run-java.sh
CMD JAVA_MAIN_CLASS=HelloWorld sh /opt/run-java.sh

