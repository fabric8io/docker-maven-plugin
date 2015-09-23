## Registry handling

Docker uses registries to store images. The registry is typically
specified as part of the name. I.e. if the first part (everything
before the first `/`) contains a dot (`.`) or colon (`:`) this part is
interpreted as an address (with an optionally port) of a remote
registry. This registry (or the default `registry.hub.docker.com` if no
registry is given) is used during push and pull operations. This
plugin follows the same semantics, so if an image name is specified
with a registry part, this registry is contacted. Authentication is
explained in the next [section](#authentication). 

There are some situations however where you want to have more
flexibility for specifying a remote registry. This might be, because
you do not want to hard code a registry within the `pom.xml` but
provide it from the outside with an environment variable or a system
property. 

This plugin supports various ways of specifying a registry:

* If the image name contains a registry part, this registry is used
  unconditionally and can not be overwritten from the outside.
* If an image name doesn't contain a registry, then by default the
  default Docker registry `docker.io` is used for push and pull
  operations. But this can be overwritten through various means:
  - If the `<image>` configuration contains a `<registry>` subelement
    this registry is used.
  - Otherwise, a global configuration element `<registry>` is
    evaluated which can be also provided as system property via
    `-Ddocker.registry`. 
  - Finally an environment variable `DOCKER_REGISTRY` is looked up for
    detecting a registry.
    
Example:

```xml
<configuration>
  <registry>docker.jolokia.org:443</registry>
  <images>
    <image>
      <!-- Without an explicit registry ... -->
      <name>jolokia/jolokia-java</name>
      <!-- ... hence use this registry -->
      <registry>docker.ro14nd.de</registry>
      ....
    <image>
    <image>
      <name>postgresql</name>
      <!-- No registry in the name, hence use the globally 
           configured docker.jolokia.org:443 as registry -->
      ....
    </image>
    <image>
      <!-- Explicitely specified always wins -->
      <name>docker.example.com:5000/another/server</name>
    </image>
  </images>
</configuration>
```

There is some special behaviour when using an externally provided
registry like described above:

* When *pulling*, the image pulled will be also tagged with a repository
  name **without** registry. The reasoning behind this is that this
  image then can be referenced also by the configuration when the
  registry is not specified anymore explicitly.
* When *pushing* a local image, temporarily a tag including the
  registry is added and removed after the push. This is required
  because Docker can only push registry-named images.

