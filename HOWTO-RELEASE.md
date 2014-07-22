
# Release instructions

## Preparation

* Increase version number in README.md example

## Building and deploying

The release process uses the maven release plugin:

     mvn -DdevelopmentVersion=1.0.1-SNAPSHOT -DreleaseVersion=1.0.0 -Dtag=v1.0.0 -Pdist release:prepare
     mvn -Pdist release:perform
 
This will deploy to Maven central. The profile "dist" enables signing
of artefacts and uses a running GPG agent.

## Publishing 

In order to publish the staged artefacts:

* Login into https://oss.sonatype.org/
* "Staging Repositories"
* Select staging repository
* Click "Close" in menu
* Check the artifacts in the "closed" repository
* If ok --> click "Release" in menu

See also Sonatype's [introduction][1]


   [1]: https://docs.sonatype.org/display/Repository/Sonatype+OSS+Maven+Repository+Usage+Guide
