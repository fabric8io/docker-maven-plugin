## Image configuration

The plugin's configuration is centered around *images*. These are
specified for each image within the `<images>` element of the
configuration with one `<image>` element per image to use. 

The `<image>` element can contain the following sub elements:

* **name** : Each `<image>` configuration has a mandatory, unique docker
  repository *name*. This can include registry and tag parts, too. For
  definition of the repository name please refer to the
  Docker documentation
* **alias** is a shortcut name for an image which can be used for
  identifying the image within this configuration. This is used when
  linking images together or for specifying it with the global
  **image** configuration.
* **registry** is a registry to use for this image. If the `name`
  already contains a registry this takes precedence. See
  [Registry handling](#registry-handling) for more details.
* **build** is a complex element which contains all the configuration
  aspects when doing a `docker:build` or `docker:push`. This element
  can be omitted if the image is only pulled from a registry e.g. as
  support for integration tests like database images.
* **run** contains subelements which describe how containers should be
  created and run when `docker:start` or `docker:stop` is called. If
  this image is only used a *data container* for exporting artifacts
  via volumes this section can be missing.
* **external** can be used to fetch the configuration through other
  means than the intrinsic configuration with `run` and `build`. It
  contains a `<type>` element specifying the handler for getting the
  configuration. See [External configuration](#external-configuration)
  for details.

Either `<build>` or `<run>` must be present. They are explained in
details in the corresponding goal sections.

Example:

````xml
<configuration>
  ....
  <images>
    <image>
      <name>jolokia/docker-demo:0.1</name>
      <alias>service</alias>
      <run>....</run>
      <build>....</build>      
    </image>  
  </images>
</configuration>
````
