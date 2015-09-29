
# Release instructions

## Preparation

* Increase version number in doc/manual/installation.md example
* Increase version number in README.md
* Increase version numbers in the poms below samples/ (they are not automatically updated)
* Run "update_issue_links.sh" in "doc/"
* Check into Git

## Building and deploying

The release process uses the maven release plugin:

     mvn -Dmaven.repo.local=/tmp/clean-repo -DdevelopmentVersion=1.0.1-SNAPSHOT -DreleaseVersion=1.0.0 -Dtag=v1.0.0 -Pdist release:prepare
     mvn -Dmaven.repo.local=/tmp/clean-repo -Pdist release:perform
 
This will deploy to Maven central. The profile "dist" enables signing
of artifacts and uses a running GPG agent.

## Publishing 

In order to publish the staged artifacts:

* Login into https://oss.sonatype.org/
* "Staging Repositories"
* Select staging repository
* Click "Close" in menu
* Check the artifacts in the "closed" repository
* If ok --> click "Release" in menu

See also Sonatype's [introduction][1]


   [1]: https://docs.sonatype.org/display/Repository/Sonatype+OSS+Maven+Repository+Usage+Guide
