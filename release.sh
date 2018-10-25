#!/bin/bash

# ======================================
# Release script for docker-maven-plugin
# ======================================

# Exit if any error occurs
# Fail on a single failed command in a pipeline (if supported)
set -o pipefail

# Save global script args, use "build" as default
if [ -z "$1" ]; then
    ARGS=("")
else
    ARGS=("$@")
fi

# Fail on error and undefined vars (please don't use global vars, but evaluation of functions for return values)
set -eu

usage() {
    cat - <<EOT
Release docker-maven-plugin


-n  --dry-run                 Dry run, which performs the whole build but does no tagging, artefact
                              upload or pushing Docker images
    --release-version <ver>   Version to release (e.g. "1.2.1"). One version arg is mandatory
    --snapshot-release        Snapshot release which can be created on a daily basis.
    --settings <file>         Path to a custom settings.xml to use for the release.
                              This file must contain all the credentials to be used for Sonatype.
                              By default ~/.m2/settings.xml is used.
    --local-maven-repo <dir>  Local dir for holding the local Maven repo cache. If not given, then a new
                              temporary directory will be used (and removed after the release)
    --no-git-push             Don't push the release tag (and symbolic major.minor tag) at the end
    --git-remote              Name of the git remote to push to. If not given, its trying to be pushed
                              to the git remote to which the currently checked out branch is attached to.
                              Works only when on a branch, not when checked out directly.
    --log <log-file>          Write full log to <log-file>, only print progress to screen
EOT
}


# Dir where this script is located
basedir() {
    # Default is current directory
    local script=${BASH_SOURCE[0]}

    # Resolve symbolic links
    if [ -L $script ]; then
        if readlink -f $script >/dev/null 2>&1; then
            script=$(readlink -f $script)
        elif readlink $script >/dev/null 2>&1; then
            script=$(readlink $script)
        elif realpath $script >/dev/null 2>&1; then
            script=$(realpath $script)
        else
            echo "ERROR: Cannot resolve symbolic link $script"
            exit 1
        fi
    fi

    local dir=$(dirname "$script")
    local full_dir=$(cd "${dir}" && pwd)
    echo ${full_dir}
}

# Checks if a flag is present in the arguments.
hasflag() {
    filters="$@"
    for var in "${ARGS[@]}"; do
        for filter in $filters; do
          if [ "$var" = "$filter" ]; then
              echo 'true'
              return
          fi
        done
    done
}

# Read the value of an option.
readopt() {
    filters="$@"
    next=false
    for var in "${ARGS[@]}"; do
        if $next; then
            echo $var
            break;
        fi
        for filter in $filters; do
            if [[ "$var" = ${filter}* ]]; then
                local value="${var//${filter}=/}"
                if [ "$value" != "$var" ]; then
                    echo $value
                    return
                fi
                next=true
            fi
        done
    done
}

check_error() {
    local msg="$*"
    if [ "${msg//ERROR/}" != "${msg}" ]; then
        echo "==============================================================="
        echo $msg
        exit 1
    fi
}

get_release_version() {
    if [ $(hasflag --snapshot-release) ]; then
        echo $(calc_timestamp_version)
        return
    fi

    local release_version=$(readopt --release-version)
    if [ -z "${release_version}" ]; then
        echo "ERROR: Please specify --release-version"
        return
    fi
    echo $release_version
}

calc_timestamp_version() {
    # ./mvnw -N help:evaluate -Dexpression="project.version"
    local pom_version=$(./mvnw -N help:evaluate -Dexpression="project.version" | grep  '^[0-9]' | sed -e 's/\([0-9]*\.[0-9]*\).*/\1/')
    if [ -z "${pom_version}" ]; then
        echo "ERROR: Cannot extract version from pom.xml"
        exit 1
    fi
    local patch_level=$(git tag | grep ^$pom_version | grep -v '-' | grep '[0-9]*\.[0-9]*\.' | sed -e s/${pom_version}.// | sort -n -r | head -1)
    echo "${pom_version}.$((patch_level+1))-$(date '+%Y%m%d')"
}

check_git_clean() {
    echo "==== Checking for clean Git Repo"
    set +e
    git diff-index --quiet HEAD --
    local git_uncommitted=$?
    set -e
    if [ $git_uncommitted != 0 ]; then
       echo "Untracked or changed files exist. Please run release on a clean repo"
       git status
       exit 1
    fi
}

update_pom_versions() {
    local version="$1"
    local maven_opts="$2"

    echo "==== Updating pom.xml versions to $version"
    ./mvnw ${maven_opts} versions:set -DnewVersion=$version -DprocessAllModules=true -DgenerateBackupPoms=false
}

extract_maven_opts() {
    local maven_opts="-Dmaven.repo.local=$1 --batch-mode"

    local settings_xml=$(readopt --settings-xml --settings)
    if [ -n "${settings_xml}" ]; then
        maven_opts="$maven_opts -s $settings_xml"
    fi

    echo $maven_opts
}

mvn_clean_install() {
    local maven_opts="$1"

    echo "==== Running 'mvn clean install'"
    ./mvnw ${maven_opts} clean install -DskipTests
}

build_and_stage_artefacts() {
    local maven_opts="$1"

    if [ $(hasflag --snapshot-release) ]; then
        echo "==== Building locally (--no-maven-release)"
        ./mvnw ${maven_opts} clean install -Pflash
    else
        echo "==== Building and staging Maven artefacts to Sonatype"
        ./mvnw ${maven_opts} -Prelease clean deploy -DstagingDescription="Staging Syndesis for $(readopt --release-version)"
    fi
}

drop_staging_repo() {
    local maven_opts="$1"

    if [ $(hasflag --snapshot-release) ]; then
        return
    fi

    echo "==== Dropping Sonatype staging repo"
    ./mvnw ${maven_opts} nexus-staging:drop -Prelease -DstagingDescription="Dropping repo"
}

release_staging_repo() {
    local maven_opts="$1"

    if [ $(hasflag --snapshot-release) ]; then
        return
    fi

    echo "==== Releasing Sonatype staging repo"
    ./mvnw ${maven_opts} -Prelease nexus-staging:release -DstagingDescription="Releasing $(readopt --release-version)"
}

git_commit_files() {
    local version=$1

    echo "==== Committing files to local git"
    git_commit pom.xml "Update pom.xmls to $version"
}

git_tag_release() {
    local release_version=${1}

    echo "==== Tagging version $release_version"
    git tag -f "$release_version"
}

git_push() {
    local release_version=${1:-}

    if [ ! $(hasflag --no-git-push) ] && [ ! $(hasflag --dry-run -n) ]; then
        local remote=$(readopt --git-remote)
        if [ -z "${remote}" ]; then
            # Push to the remote attached to the local checkout branch
            remote=$(git for-each-ref --format='%(upstream:short)' $(git symbolic-ref -q HEAD) | sed -e 's/\([^\/]*\)\/.*/\1/')
            if [ -z "${remote}" ]; then
              echo "ERROR: Cannot find remote repository to git push to"
              exit 1
            fi
        fi

        echo "==== Pushing to GitHub"
        if [ -n "$release_version" ]; then
            echo "* Pushing $release_version"
            if [ $(hasflag --snapshot-release) ]; then
                # Force push to allow multiple releases per day
                git push -f -u $remote $release_version
            else
                git push -u $remote $release_version
            fi
        fi
    fi
}

# ===================================================================================

if [ $(hasflag --help -h) ]; then
   usage
   exit 0
fi

cd $(basedir)
release_version=$(get_release_version)
check_error "$release_version"

# Write to logfile if requested
if [ $(readopt --log) ]; then
    logfile=$(readopt --log)
    touch $logfile
    tail -f $logfile > >(grep ^====) &
    tail_pid=$!
    trap "kill $tail_pid" EXIT
    exec >>$logfile 2>&1
    sleep 1
fi

# Verify that there are no modified file in git repo
check_git_clean

# Temporary local repository to guarantee a clean build
local_maven_repo=$(readopt --local-maven-repo)
if [ -z "$local_maven_repo" ]; then
    local_maven_repo=$(mktemp -d 2>/dev/null || mktemp -d -t 'maven_repo')
    trap "echo 'Removing temp maven repo $local_maven_repo' && rm -rf $local_maven_repo" "EXIT"
fi

# Calculate common maven options
maven_opts="$(extract_maven_opts $local_maven_repo)"
check_error $maven_opts

# Set pom.xml version to the given release_version
update_pom_versions "$release_version" "$maven_opts"

# Make a clean install
mvn_clean_install "$maven_opts"

# Build and stage artefacts to Sonatype
build_and_stage_artefacts "$maven_opts"

# For a test run, we are done
if [ $(hasflag --dry-run -n) ]; then
    drop_staging_repo "$maven_opts"

    echo "==== Dry run finished, nothing has been committed"
    echo "==== Use 'git reset --hard' to cleanup"
    exit 0
fi

# ========================================================================
# Commit, tag, release, push
# --------------------------

# Git Commit all changed files
git_commit_files "$release_version"

# Tag the release version
git_tag_release "$release_version"

# Push everything (if configured)
git_push "$release_version"

# Release staging repo
release_staging_repo "$maven_opts"
