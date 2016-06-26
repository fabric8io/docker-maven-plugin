### docker:push

This goals uploads images to the registry which have a `<build>`
configuration section. The images to push can be restricted with with
the global option `image` (see
[Global Configuration](global-configuration.html) for details). The
registry to push is by default `docker.io` but can be
specified as part of the images's `name` name the Docker
way. E.g. `docker.test.org:5000/data:1.5` will push the image `data`
with tag `1.5` to the registry `docker.test.org` at port
`5000`. Security information (i.e. user and password) can be specified
in multiple ways as described in section [Authentication](authentication.html).

Options:

* **skipPush** (`docker.skip.push`)
  If set to `true` the plugin won't push any images that have been built.
* **pushRegistry** (`docker.push.registry`)
  The registry to use when pushing the image. See [Registry Handling](registry-handling.html) for 
  more details.


