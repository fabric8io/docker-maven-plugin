# Sample Dockerfile for use with the Docker file mode
FROM ${base}

ENV SAMPLE_BUILD_MODE=dockerfile
LABEL PROJECT_NAME=hello-world \
      PROJECT=${project.artifactId}

# Arbitrary files can be added
ADD ${file} /

# Jetty 12 enables no deployer by default, so webapps/ would never be scanned.
# ee11-deploy pulls in the deployment scanner plus the Jakarta EE 11 web app support.
RUN java -jar "$JETTY_HOME/start.jar" --add-modules=ee11-deploy

# In maven/ the files as specified in the <assembly> section is stored
# and need to be added manually
COPY maven/ /var/lib/jetty/webapps/

EXPOSE 8080
