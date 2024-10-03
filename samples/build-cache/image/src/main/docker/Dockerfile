# java-debian10 Distroless (https://github.com/GoogleContainerTools/distroless) image is so minimalistic
# that even doesn't contain any shell.
#
# If one wants to create a directory when inheriting this image then he/she may suffer because of the lack
# of possibility to use shell commands in the RUN directive.
# To solve this, follow the demonstrated approach: add everything to the image - including the new directories -
# using ADD directive and an archive, which is built on the host and contains desired structure of the file system.
# Or install shell (add busybox following described approach) and then use it in the RUN directive as usual.
FROM gcr.io/distroless/java-debian10

# Just for security
USER nonroot

# /app directory contains all items of the wanted Java class path.
WORKDIR "/app"

# Override (to zero) 130 and 143 exit codes matching SIGTERM and SIGINT.
CMD ["tini", "-e", "130", "-e", "143", "--", "java", "org.springframework.boot.loader.JarLauncher"]

# From the least frequently changed files to the most frequently changed files:
# ... application dependencies
ADD ["dependencies.tar", "/"]
# ... Spring Boot loader classes
ADD ["spring-boot-loader.tar", "/"]
# ... application snapshot dependencies
ADD ["snapshot-dependencies.tar", "/"]
# ... application code
ADD ["application.tar", "/"]

# Entry point of the base image executes java which we don't need because we use tini in the image command.
# tini (https://github.com/krallin/tini) is used to enforce zero exit code when Java application is stopped
# using SIGINT or SIGTERM.
ENTRYPOINT []

# Put version into the last layer because LABEL directive produces more lightweight layer than ADD.
LABEL name="@{image}" \
    version="@{project.version}"
