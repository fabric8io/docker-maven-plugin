## Authentication

When pulling (via the `autoPull` mode of `docker:start`) or pushing image, it
might be necessary to authenticate against a Docker registry.

There are three different ways for providing credentials:
 
* Using a `<authConfig>` section in the plugin configuration with
  `<username>` and `<password>` elements. 
* Providing system properties `docker.username` and `docker.password`
  from the outside 
* Using a `<server>` configuration in `~/.m2/settings.xml`
* Login into a registry with `docker login`

Using the username and password directly in the `pom.xml` is not
recommended since this is widely visible. This is most easiest and
transparent way, though. Using an `<authConfig>` is straight forward:

````xml
<plugin>
  <configuration>
    <image>consol/tomcat-7.0</image>
    ...
    <authConfig>
      <username>jolokia</username>
      <password>s!cr!t</password>      
    </authConfig>
  </configuration>
</plugin>
````

The system property provided credentials are a good compromise when
using CI servers like Jenkins. You simply provide the credentials from
the outside:

    mvn -Ddocker.username=jolokia -Ddocker.password=s!cr!t docker:push

The most secure and also the most *mavenish* way is to add a server to
the Maven settings file `~/.m2/settings.xml`:

```xml
<servers>
  <server>
    <id>docker.io</id>
    <username>jolokia</username>
    <password>s!cr!t</password>
  </server>
  ....
</servers>
```

The server id must specify the registry to push to/pull from, which by
default is central index `docker.io` (or `index.docker.io` / `registry.hub.docker.com` as fallbacks). 
Here you should add your docker.io account for your repositories. If you have multiple accounts 
for the same registry, the second user can be specified as part of the ID. In the example above, if you 
have a second accorunt 'rhuss' then use an `<id>docker.io/rhuss</id>` for this second entry. I.e. add the 
username with a slash to the id name. The default without username is only taken if no server entry with 
a username appended id is chosen.

As a final fallback, this plugin consults `~/.docker/config.json` for getting to the credentials. Within this 
file credentials are stored when connecting to a registry with the command `docker login` from the command line. 

#### Pull vs. Push Authentication

The credentials lookup described above is valid for both push and
pull operations. In order to narrow things down, credentials can be be
provided for pull or push operations alone:

In an `<authConfig>` section a sub-section `<pull>` and/or `<push>`
can be added. In the example below the credentials provider are only
used for image push operations:

```xml
<plugin>
  <configuration>
    <image>consol/tomcat-7.0</image>
    ...
    <authConfig>
      <push>
         <username>jolokia</username>
         <password>s!cr!t</password>
      </push>
    </authConfig>
  </configuration>
</plugin>
```

When the credentials are given on the command line as system
properties, then the properties `docker.pull.username` / 
`docker.pull.password` and `docker.push.username` /
`docker.push.password` are used for pull and push operations,
respectively (when given). Either way, the standard lookup algorithm
as described in the previous section is used as fallback. 

#### OpenShift Authentication

When working with the default registry in OpenShift, the credentials
to authtenticate are the OpenShift username and access token. So, a
typical interaction with the OpenShift registry from the outside is:

```
oc login
...
mvn -Ddocker.registry=docker-registry.domain.com:80/default/myimage \
    -Ddocker.username=$(oc whoami) \
    -Ddocker.password=$(oc whoami -t)
```

(note, that the image's user name part ("default" here") must
correspond to an OpenShift project with the same name to which you
currently connected account has access).

This can be simplified by using the system property
`docker.useOpenShiftAuth` in which case the plugin does the
lookup. The equivalent to the example above is

```
oc login
...
mvn -Ddocker.registry=docker-registry.domain.com:80/default/myimage \
    -Ddocker.useOpenShiftAuth
```

Alternatively the configuration option `<useOpenShiftAuth>` can be
added to the `<authConfig>` section. 

For dedicted *pull* and *push* configuration the system properties
`docker.pull.useOpenShiftAuth` and `docker.push.useOpenShiftAuth` are
available as well as the configuration option `<useOpenShiftAuth>` in
an `<pull>` or `<push>` section within the `<authConfig>`
configuration. 

#### Password encryption

Regardless which mode you choose you can encrypt password as described
in the
[Maven documentation](http://maven.apache.org/guides/mini/guide-encryption.html). Assuming
that you have setup a *master password* in
`~/.m2/security-settings.xml` you can create easily encrypted
passwords:

```bash
$ mvn --encrypt-password
Password:
{QJ6wvuEfacMHklqsmrtrn1/ClOLqLm8hB7yUL23KOKo=}
```

This password then can be used in `authConfig`, `docker.password`
and/or the `<server>` setting configuration. However, putting an
encrypted password into `authConfig` in the `pom.xml` doesn't make
much sense, since this password is encrypted with an individual master
password.
