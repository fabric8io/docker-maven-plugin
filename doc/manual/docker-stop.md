### docker:stop

Stops and removes a docker container. This goals stops every
container started with `<docker:start>` either during the same build
(e.g. when bound to lifecycle phases when doing integration tests) or
for containers created by a previous call to `<docker:start>`

If called within the same build run, only the containers that were 
explicitly started during the run will be stopped. Existing containers
started using `docker:start` for the project will not be affected.

If called as a separate invocation, the plugin will stop and remove any
container it finds whose image is defined in the project's configuration.
Any existing containers found running whose image name matches but was not 
started by the plugin will not be affected.

In case the naming strategy for an image is `alias` (i.e. the container name is 
set to the given alias), then only the container with this alias is stopped. Other 
containers originating from the same image are not touched.

It should be noted that any containers created prior to version `0.13.7` of the
plugin may not be stopped correctly by the plugin because the label needed to tie
the container to the project may not exist. Should this happen, you will need to
use the Docker CLI to clean up the containers and/or use the `docker.sledgehammer`
option listed below. 

For tuning what should happen when stopping there are four global
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
* **allContainers** (`docker.allContainers`) Stops and removes any container that
  matches an image defined in the current project's configuration. This was the
  default behavior of the plugin prior up to version 0.13.6

Example: 

    $ mvn -Ddocker.keepRunning clean install
