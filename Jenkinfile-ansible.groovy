#!groovy
/** Job parameters used
 * 1. @env ANSIBLE_PATH (optional): Provide Basepath for ansible scripts. @default /var/lib/jenkins/ansible-scripts
 * 2. @env ANSIBLE_FOLDER (optional): Provides folder name AFTER basepath. @default Null
 * 3. @env ANSIBLE_SERVERS (required): Comma Separated list of servers. Ex- India,US1,US2 etc.
 * 4. @env MAINTAINER_EMAIL (required): List of maintainers (comma sep). Ex- abc@domain.com,def@domain.com
 * 5. @choice-input `server_input`: If true, it asks to choose server after each deployment, else deploys on all
        servers without any confirmation.
 *
 * Please note that the ansible files should be placed inside `$ANSIBLE_PATH` with the names of servers defined.
 * For example, For server lists: [IN,US1,US2], you can place the ansible files in:
 * `/var/lib/jenkins/ansible-scripts/api/in.yml, /var/lib/jenkins/ansible-scripts/api/us1.yml` and so on.
 * Then you can set your environment variables as (NOTE NO WHITESPACE):
 *
 * ANSIBLE_FOLDER=api
 * ANSIBLE_SERVERS=IN,US1,US2
 * MAINTAINER_EMAIL=admin@myorgdomain.tld
 **/

/** Ansible Server List
 * Comma separated list of ansible servers (IN,US1,HYD2)
 * @note This list should not contain any whitespace or undesired characters
 **/
def ansibleServers = env.ANSIBLE_SERVERS.tokenize(',')
ansibleServers = ansibleServers + "STOP"

// Ansible Path builder
def getAnsiblePathScript(basepath = '/var/lib/jenkins/ansible-scripts') {
    def mBasePath = basepath
    if(env.ANSIBLE_PATH && (env.ANSIBLE_PATH != '')) {
        mBasePath = env.ANSIBLE_PATH
    }
    def finalPath = mBasePath
    if(env.ANSIBLE_FOLDER && (env.ANSIBLE_FOLDER != '')) {
        finalPath = "${mBasePath}/${env.ANSIBLE_FOLDER}"
    }
    return finalPath
}

// Slack notification builder
def notifyBuild(String buildStatus = 'STARTED', String _summary=null) {
  // build status of null means successful
  buildStatus =  buildStatus ?: 'SUCCESSFUL'

  // Default values
  def colorName = 'RED'
  def colorCode = '#FF0000'
  def subject = "${buildStatus}: Job '${env.JOB_NAME} [${env.BUILD_NUMBER}]'"
  def summary = "${subject} (${env.BUILD_URL})"

  if(buildStatus == 'APPROVE') {
      summary = "${subject} (${env.BUILD_URL}/input)"
  }

  if(_summary != null) {
      summary = "${summary} \n\n ${_summary}"
  }

  // Override default values based on build status
  if (buildStatus == 'STARTED' || buildStatus == 'APPROVE') {
    color = 'YELLOW'
    colorCode = '#FFFF00'
  } else if (buildStatus == 'SUCCESSFUL') {
    color = 'GREEN'
    colorCode = '#00FF00'
  } else {
    color = 'RED'
    colorCode = '#FF0000'
  }

  // Send notifications
  slackSend (color: colorCode, message: summary)
}

def sendEmail() {
    notifyBuild('APPROVE')

    def to = env.MAINTAINER_EMAIL
    def approveUrl = "${env.BUILD_URL}/input"
    emailext (
        subject: "Deployment input required- '${env.JOB_NAME} build #${env.BUILD_NUMBER}'",
        body: """<b>Build #${env.BUILD_NUMBER} Waiting for you.</b><br>\n\nApproval URL: ${approveUrl} <br>\n\n<br>Project: ${env.JOB_NAME} <br>Build Number: ${env.BUILD_NUMBER} <br> Build URL: ${env.BUILD_URL}""",
        attachLog: true,
        mimeType: 'text/html',
        to: "${to}"
    )
}

def runAnsible(playbook) {
    def mBasePath = getAnsiblePathScript()
    def ansiblePlaybookPath = "${mBasePath}/${playbook}"
    ansiblePlaybook(playbook: ansiblePlaybookPath)
}

def deployBuild(server) {
    node {
        stage("Server ${server}") {
            def servernameLower = server.toLowerCase()
            def playbookName = "${servernameLower}.yml"
            sh "echo 'Deploying ansible Script to ${server} Server. Using ansible playbook ${playbookName}'"
            try{
                runAnsible(playbookName)
                notifyBuild('SUCCESSFUL', "Deployment succeeded on ${server} server")
            } catch(e) {
                currentBuild.result = 'UNSTABLE'
                notifyBuild("FAILED", "Deployment failed on server ${server} server")
            }
            return true
        }
    }
}

if(params.server_input) {
    notifyBuild()
    sendEmail()

    milestone 1
    while (true) {
        def deployToServer = null
        node {
            stage("Input") {
                deployToServer = input message: 'On which server do you want to deploy?', parameters: [choice(choices: ansibleServers.join('\n'), description: "List of servers: ${ansibleServers.join(',')}", name: 'Stage')]
            }
        }
        if(deployToServer.toLowerCase() == "stop") {
            notifyBuild('SUCCESSFUL')
            break
            return
        } else {
            deployBuild(deployToServer)
        }
    }
    milestone 2

} else {
    notifyBuild()
    milestone 1
    for (int i = 0; i < (ansibleServers.size() - 1); i++) {
        deployBuild(ansibleServers[i])
    }
    notifyBuild('SUCCESSFUL')
    milestone 2
}
