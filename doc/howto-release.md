
# Release instructions

## Preparation

* Increase version number in doc/manual/installation.md example
* Increase version numbers in the poms below samples/ (they are not automatically updated)

cd samples
mvn versions:set -DgenerateBackupPoms=false -DnewVersion=0.15.4

* Run "update_issue_links.sh" in "doc/"
* Check into Git

## Building and deploying

* Run the build over the fabric8 CD Pipeline 
