FROM image
MAINTAINER maintainer@example.com
ENV foo=bar
LABEL com.acme.foobar="How are \"you\" ?"
EXPOSE 8080
COPY /src /export/dest
WORKDIR /tmp
SHELL ["/bin/sh","-c"]
RUN echo something
RUN echo second
VOLUME ["/vol1"]
CMD ["c1","c2"]
