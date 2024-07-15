FROM alpine

RUN --mount=type=secret,id=myEnvVar cat /run/secrets/myEnvVar
RUN --mount=type=secret,id=myFile cat /run/secrets/myFile
