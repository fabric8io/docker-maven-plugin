
# Release instructions

## Preparation

* Increase version numbers in the poms below samples/ (they are not automatically updated)

```
cd samples
mvn versions:set -DgenerateBackupPoms=false -DnewVersion=0.15.4
```

* Run "update_issue_links.sh" in "doc/"
* Check into Git, push, create PR and apply

## Building and deploying

* Run the build over the fabric8 CD Pipeline 

## After the build

* Set sample version back to the snaphot version

```
mvn versions:set -DgenerateBackupPoms=false -DnewVersion=0.15-SNAPSHOT
```

* Check in

