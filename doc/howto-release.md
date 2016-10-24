
# Release instructions

## Preparation

* Increase version numbers in the poms below samples/ (they are not automatically updated)

```
cd samples
mvn versions:set -DgenerateBackupPoms=false -DnewVersion=0.15.4
```

* Run "update_issue_links.sh" in "doc/"
* Check into Git and push

## Building and deploying

* Run the build over the fabric8 CD Pipeline 

## After the build

* Set sample version back to the snaphot version

```
mvn versions:set -DgenerateBackupPoms=false -DnewVersion=0.15-SNAPSHOT
```

* Check in

* Rebase `integration` with `master` again

```
git co integration
git rebase master
git push
```
