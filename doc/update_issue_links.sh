#!/bin/sh
perl -i -p -e 's|\(\s*#(\d+)\s*\)|([#](https://github.com/rhuss/docker-maven-plugin/issues/))|g' changelog.md
