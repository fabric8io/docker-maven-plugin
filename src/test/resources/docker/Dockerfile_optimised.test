FROM image
MAINTAINER maintainer@example.com
ENV foo=bar
LABEL com.acme.foobar="How are \"you\" ?"
EXPOSE 8080
COPY /src /export/dest
WORKDIR /tmp
RUN echo something && echo second && echo third && echo fourth && echo fifth
VOLUME ["/vol1"]
CMD ["c1","c2"]
