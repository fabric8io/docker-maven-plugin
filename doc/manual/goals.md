## Maven Goals

This plugin supports the following goals which are explained in detail
in the following sections.

| Goal                                | Description                          |
| ----------------------------------- | ------------------------------------ |
| [`docker:build`](docker-build.md)   | Build images                         |
| [`docker:start`](docker-start.md)   | Create and start containers          |
| [`docker:stop`](docker-stop.md)     | Stop and destroy containers          |
| [`docker:push`](docker-push.md)     | Push images to a registry            |
| [`docker:remove`](docker-remove.md) | Remove images from local docker host |
| [`docker:logs`](docker-logs.md)     | Show container logs                  |
| [`docker:source`](docker-source.md) | Attach docker build archive to Maven project |

Note that all goals are orthogonal to each other. For example in order
to start a container for your application you typically have to build
its image before. `docker:start` does **not** imply building the image
so you should use it then in combination with `docker:build`.
