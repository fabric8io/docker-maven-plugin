# Docker Maven Plugin Copy Sample

This is a plain maven project used to demonstrate how to copy files or directories from container using `docker:copy` goal

## Copying File from Container to Local Host
We have a `copy-file` profile which copies `/etc/hosts` file from container to your project build directory. It has executions set up for `start`,`copy`,`stop` goals so they will be activated when you build using the specified profile. In order to run it, use this command:
```
$ mvn clean package -Pcopy-file
```
Once command finishes successfully, try checking your `target` directory to see if the file got created or not. If everything goes okay, you should be able to see output like this:
```
copy-from-container : $ ls target
hosts  
copy-from-container : $ cat target/hosts
127.0.0.1 localhost
::1 localhost ip6-localhost ip6-loopback
fe00::0 ip6-localnet
ff00::0 ip6-mcastprefix
ff02::1 ip6-allnodes
ff02::2 ip6-allrouters
172.17.0.3  80dd81263592
```
Docker Maven Plugin external configuration can be used to define entries which need to be coped, e.g.:

```
$ mvn clean package -Pcopy-file \
    -Ddocker.imagePropertyConfiguration=override \
    -Ddocker.name=alpine \
    -Ddocker.copy.entries.1.containerPath=/etc/os-release \
    -Ddocker.copy.entries.1.hostDirectory=target \
    -Ddocker.copy.entries.2.containerPath=/etc/hostname \
    -Ddocker.copy.entries.2.hostDirectory=target
...
$ ls target
hostname  os-release
$ cat target/hostname
f1387e0c6253
$ cat target/os-release
NAME="Alpine Linux"
ID=alpine
VERSION_ID=3.13.2
PRETTY_NAME="Alpine Linux v3.13"
HOME_URL="https://alpinelinux.org/"
BUG_REPORT_URL="https://bugs.alpinelinux.org/"
```

## Copying Directory from Container to Local Host
We have a `copy-directory` profile which copies `/dev` directory from container to your `target` directory. It has `createContainers=true` with which plugin copies from a temporary(created, but not started) container instead of copying from the standard container. In order to run it, use this command:
```
$ mvn clean package -Pcopy-directory
```
Once command finishes successfully, try checking your `target` directory to see file got created or not. If everything goes okay, you should be able to see output like this:
```
copy-from-container : $ ls target
dev
copy-from-container : $ ls target/dev/
console  pts  shm
```
