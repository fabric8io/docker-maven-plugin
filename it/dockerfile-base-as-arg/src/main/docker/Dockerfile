# Sample Dockerfile for use with the Docker file mode
ARG baseImage
FROM ${baseImage}

ENV SAMPLE_BUILD_MODE=dockerfile
LABEL PROJECT_NAME=hello-world \
      PROJECT=${project.artifactId}

# Arbitrary files can be added
ADD ${file} /

# In maven/ the files as specified in the <assembly> section is stored
# and need to be added manually
COPY maven/ /var/lib/jetty/webapps/

EXPOSE 8080
