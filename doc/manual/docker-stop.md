### docker:stop

Stops and removes a docker container. This goals starts every
container started with `<docker:stop>` either during the same build
(e.g. when bound to lifecycle phases when doing integration tests) or
for containers created by a previous call to `<docker:start>`

If called within the same build run it will exactly stop and destroy
all containers started by this plugin. If called in a separate run it
will stop (and destroy) all containers which were created from images
which are configured for this goal. This might be dangerous, but of
course you can always stop your containers with the Docker CLI, too.

For tuning what should happen when stopping there are two global
parameters which are typically used as system properties:

* **keepContainer** (`docker.keepContainer`) If given will not destroy
  container after they have been stopped. 
* **keepRunning** (`docker.keepRunning`) actually won't stop the
  container. This apparently makes only sense when used on the command
  line when doing integration testing (i.e. calling `docker:stop`
  during a lifecycle binding) so that the container are still running
  after an integration test. This is useful for analysis of the
  containers (e.g. by entering it with `docker exec`). 
* **removeVolumes** (`docker.removeVolumes`) If given will remove any
  volumes associated to the container as well. This option will be ignored
  if either `keepContainer` or `keepRunning` are true.

Example: 

    $ mvn -Ddocker.keepRunning clean install
