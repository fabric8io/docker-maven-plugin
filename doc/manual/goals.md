### Maven Goals

This plugin supports the following goals which are explained in detail
in the following sections.

| Goal                             | Description                          |
| -------------------------------- | ------------------------------------ |
| [`docker:build`](dockerbuild.md)   | Build images                         |
| [`docker:start`](dockerstart.md)   | Create and start containers          |
| [`docker:stop`](dockerstop.md)     | Stop and destroy containers          |
| [`docker:push`](dockerpush.md)     | Push images to a registry            |
| [`docker:remove`](dockerremove.md) | Remove images from local docker host |
| [`docker:logs`](dockerlogs.md)     | Show container logs                  |

Note that all goals are orthogonal to each other. For example in order
to start a container for your application you typically have to build
its image before. `docker:start` does **not** imply building the image
so you should use it then in combination with `docker:build`.
