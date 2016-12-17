## Contributing 

You want to contribute ? Awesome ! We **♥︎♥︎ LOVE ♥︎♥︎** contributions ;-)

Here some things to check out when doing a PR:

* Please sign-off your commits as described below.
* If adding a new feature please [update the documentation](https://github.com/fabric8io/docker-maven-plugin/blob/master/src/main/asciidoc/), too.
* Don't forget the unit tests.
* If adding a new configuration option, don't forget to add this to the [PropertyHandler](https://github.com/fabric8io/docker-maven-plugin/blob/master/src/main/java/io/fabric8/maven/docker/config/handler/property/PropertyConfigHandler.java), too.

However, if you can't do some of the points above, please still consider contributing. Simply ask us on `#fabric8` at Freenode or via an GitHub [issue](https://github.com/fabric8io/docker-maven-plugin/issues). We are not dogmatic.

### Signing off your commits

Pull requests are highly appreciated and most of them get applied. However, you
must sign-off your code so that you certify that your  contributions is compatible with the
license of this project (which is the [Apache Public License 2](../LICENSE)). The sign-off also certifies
that you wrote it or otherwise have the right to
pass it on as an open-source patch under the APL 2.  The rules are simple: if you
can certify the below (from
[developercertificate.org](http://developercertificate.org/)):

```
Developer Certificate of Origin
Version 1.1

Copyright (C) 2004, 2006 The Linux Foundation and its contributors.
660 York Street, Suite 102,
San Francisco, CA 94110 USA

Everyone is permitted to copy and distribute verbatim copies of this
license document, but changing it is not allowed.

Developer's Certificate of Origin 1.1

By making a contribution to this project, I certify that:

(a) The contribution was created in whole or in part by me and I
    have the right to submit it under the open source license
    indicated in the file; or

(b) The contribution is based upon previous work that, to the best
    of my knowledge, is covered under an appropriate open source
    license and I have the right under that license to submit that
    work with modifications, whether created in whole or in part
    by me, under the same open source license (unless I am
    permitted to submit under a different license), as indicated
    in the file; or

(c) The contribution was provided directly to me by some other
    person who certified (a), (b) or (c) and I have not modified
    it.

(d) I understand and agree that this project and the contribution
    are public and that a record of the contribution (including all
    personal information I submit with it, including my sign-off) is
    maintained indefinitely and may be redistributed consistent with
    this project or the open source license(s) involved.
```

then you just add a line to every git commit message:

    Signed-off-by: Joe Smith <joe.smith@email.com>

with your real name (first and last name)

If you set your `user.name` and `user.email` git configs, you can sign your
commit automatically with `git commit -s`. If you forgot this you can
use `git commit -s --amend` to add this in retrospective for the last commit.
If you need to sign-off multiple commits within a branch, you need to to do an interactive
rebase with `git rebase -i`. A nice shortcut for signing off every commit in a branch can
be provided with this [alias](http://stackoverflow.com/questions/25570947/how-to-use-git-interactive-rebase-for-signing-off-a-series-of-commits)
which you can put into your `~/.gitconfig`:

````
[alias]
  # Usage: git signoff-rebase [base-commit]
  signoff-rebase = "!EDITOR='sed -i -re s/^pick/e/' sh -c 'git rebase -i $1 && while test -f .git/rebase-merge/interactive; do git commit --amend --signoff --no-edit && git rebase --continue; done' -"
  # Ideally we would use GIT_SEQUENCE_EDITOR in the above instead of EDITOR but that's not supported for git < 1.7.8.
````

When sending pull request we prefer that to be a single commit. So please squash your commits
with an interactive rebase before sending the pull request.  This is nicely explained [here](https://github.com/edx/edx-platform/wiki/How-to-Rebase-a-Pull-Request).

Said all this, don't hesitate to ask when there are any problems or you have an issue with this process.
