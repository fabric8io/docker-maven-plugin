## Authentication

When pulling (via the `autoPull` mode of `docker:start`) or pushing image, it
might be necessary to authenticate against a Docker registry.

There are three different ways for providing credentials:
 
* Using a `<authConfig>` section in the plugin configuration with
  `<username>` and `<password>` elements. 
* Providing system properties `docker.username` and `docker.password`
  from the outside 
* Using a `<server>` configuration in the the `~/.m2/settings.xml`
  settings 
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
