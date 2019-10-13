#!groovy

/**
 * @Description This Jenkinsfile should be used for projects requiring PHP builds
 *
 * - To build and test your PHP projects
 * - To manually build, test (optionally deploy)
 *
 * Job parameters used
 * - @env NEXT_JOB (optional) Next jenkins job to run after this build
 **/

// After doing everying, merge this build
def autoMergeBuild(targetBranch = null, sourceBranch = null) {
    targetBranch = targetBranch ?: env.gitlabTargetBranch
    sourceBranch = sourceBranch ?: env.gitlabSourceBranch
    sh "echo 'merging to ${targetBranch}'"
    sh "git checkout -f ${targetBranch}"
    sh "git pull origin ${sourceBranch}"
    sh "git push origin ${targetBranch}"
}

def preMergeBuild() {
    if(env.gitlabActionType=="PUSH" || (env.gitlabActionType=="MERGE" && env.gitlabMergeRequestState=="opened")) {
        def credentialsId = scm.userRemoteConfigs[0].credentialsId
        myscmVars = checkout ([
                        $class: 'GitSCM',
                        doGenerateSubmoduleConfigurations: false,
                        branches: [[name: "origin/${env.gitlabSourceBranch}"]],
                        extensions: [[$class: 'PreBuildMerge', options: [fastForwardMode: 'FF', mergeRemote: 'origin', mergeStrategy: 'DEFAULT', mergeTarget: "${env.gitlabTargetBranch}"]]],
                        submoduleCfg: [],
                        userRemoteConfigs: [[refspec: "+refs/heads/*:refs/remotes/origin/* +refs/merge-requests/*/head:refs/remotes/origin/merge-requests/*", name: 'origin', url: env.gitlabSourceRepoSshURL]]
                    ])
        return myscmVars
    }
}

def exportGitDiff() {
    sh(returnStdout: false, script: "git diff origin/${env.gitlabTargetBranch} origin/${env.gitlabSourceBranch} > diff.txt")
}

def manualPreMergeBuild(repo, target, credentialid) {
    def credentialsId = scm.userRemoteConfigs[0].credentialsId
    myscmVars = checkout ([
                    $class: 'GitSCM',
                    doGenerateSubmoduleConfigurations: false,
                    branches: [[name: "origin/${target}"]],
                    submoduleCfg: [],
                    userRemoteConfigs: [[name: 'origin', url: repo, credentialsId: credentialid]]
                ])
    return myscmVars
}

def isManualTriggered() {
    return (currentBuild.rawBuild.getCause(hudson.model.Cause$UserIdCause) != null)
}

def handleCheckout() {
    def isManuallyTriggeredJob = isManualTriggered()
    def scmVars = null

    if(isManuallyTriggeredJob) {
        scmVars = manualPreMergeBuild(env.manualrepo, env.manualbranch, env.manualcredential)
    } else {
        scmVars = preMergeBuild()
    }

    env.COMMITTER_EMAIL=scmVars.GIT_COMMITTER_EMAIL
    env.JOB_INPUT_URL="${env.BUILD_URL}/input"

    if(env.gitlabMergeRequestIid) {
        env.GIT_PROJECT_URL="${env.gitlabSourceRepoHomepage}/merge_requests/${env.gitlabMergeRequestIid}/diffs"
    }
}

def lintTests() {
    def excludedDirs = []
    if(env.EXCLUDED_DIRS) excludedDirs = env.EXCLUDED_DIRS.tokenize(',')
    def excludeDirMap = ["--exclude vendor"]
    for(int i = 0; i < excludedDirs.size(); i++) {
        if(excludedDirs[i] != "vendor" && (excludedDirs[i] != '')){
            excludeDirMap.add("--exclude ${excludedDirs[i]}")
        }
    }
    def excludes = excludeDirMap.join(' ')
    sh "$HOME/.composer/vendor/bin/parallel-lint $excludes ."
}

def isHotfix() {
    return (env.gitlabTargetBranch == "master" && env.gitlabSourceBranch == "master")
}

node() {

    gitlabCommitStatus(name: "Build") {
        stage('PreMerge') {
            sh "env | sort"
            handleCheckout()
        }
    }

    gitlabCommitStatus(name: "Test") {
        stage('test') {
            lintTests()
            //Add any more tests you like
        }
    }

    if(!env.gitlabMergeRequestId) {

        def isManuallyTriggeredJob = isManualTriggered()
        //Probably a hotfix. Proceed to deploy
        if(isManuallyTriggeredJob && (env.manualbranch == "master")) {
            parallel("Rebase stage": {
                autoMergeBuild('stage', 'master')
            }, "Diff": {
                exportGitDiff()
            })
        }
        // Exit cleanly since our work is done
        sh 'exit 0'
    }
}

if(env.gitlabMergeRequestId && (env.gitlabTargetBranch == "stage" || env.gitlabTargetBranch == "master")) {
    if((env.gitlabTargetBranch == "stage")|| (env.gitlabSourceBranch != "stage" && env.gitlabTargetBranch == "master")) {
        // Proceed to ask input
        updateGitlabCommitStatus name: 'Merge', state: 'pending'
        node {
            stage('Merge') {
                exportGitDiff()
                autoMergeBuild()
                updateGitlabCommitStatus name: 'Merge', state: 'success'
            }
        }
    }
}


