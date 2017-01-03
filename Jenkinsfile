#!/usr/bin/groovy
@Library('github.com/fabric8io/fabric8-pipeline-library@master')
def dummy
mavenNode {
  dockerNode {
    checkout scm
    sh "git remote set-url origin git@github.com:fabric8io/docker-maven-plugin.git"

    def pipeline = load 'release.groovy'

    stage 'Stage'
    def stagedProject = pipeline.stage()

    stage 'Promote'
    pipeline.release(stagedProject)

  }
}
