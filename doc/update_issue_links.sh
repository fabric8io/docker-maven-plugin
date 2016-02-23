#!/bin/sh
perl -i -p -e 's|\(\s*#(\d+)\s*\)|([#$1](https://github.com/fabric8io/docker-maven-plugin/issues/$1))|g' changelog.md
