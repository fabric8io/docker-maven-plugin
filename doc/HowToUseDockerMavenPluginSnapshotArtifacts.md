# How to use Fabric8 docker-maven-plugin snapshot artifacts?

Artifacts are hosted at [docker-maven-plugin's Sonatype Snapshot repository](https://oss.sonatype.org/content/repositories/snapshots/io/fabric8/docker-maven-plugin/)

Our [GitHub Action Snapshot release workflow](https://github.com/fabric8io/docker-maven-plugin/blob/master/.github/workflows/release-snapshots.yml) updates SNAPSHOT artifacts every night.

## Using SNAPSHOTs in Maven Project

In order to use these artifacts, update your `pom.xml` with these:

```xml
<pluginRepositories>
  <pluginRepository>
    <id>oss.sonatype.org</id>
    <url>https://oss.sonatype.org/content/repositories/snapshots</url>
    <snapshots>
      <enabled>true</enabled>
      <updatePolicy>always</updatePolicy>
    </snapshots>
  </pluginRepository>
</pluginRepositories>
```

You'd also need to update version of the plugin you're using to use a SNAPSHOT version instead of a stable version. Here is an example:

```xml
<properties>
    <docker-maven-plugin.version>x.yz-SNAPSHOT</docker-maven-plugin.version>
</properties>

<build>
    <plugins>
        <plugin>
            <groupId>io.fabric8</groupId>
            <artifactId>docker-maven-plugin</artifactId>
            <version>${docker-maven-plugin.version}</version>
        </plugin>
    </plugins>
</build>
```

