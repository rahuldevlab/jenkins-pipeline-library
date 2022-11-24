import org.jenkinsci.plugins.pipeline.modeldefinition.Utils

def call(body) {
  try {
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()
    print config
    if (!config.extra_args?.trim()) {
       config.extra_args=""
    }
    def nextStage = ""
    def runNextStage = false
    def version = ""

    // Set the agent as kubernetes with yaml

    agent {
        kubernetes {
            yaml '''
                apiVersion: v1
                kind: Pod
                spec:
                    containers:
                    - name: maven
                    image: maven:alpine
                    command:
                    - cat
                    tty: true
                    - name: docker
                    image: docker:latest
                    command:
                    - cat
                    tty: true
                    volumeMounts:
                    - mountPath: /var/run/docker.sock
                        name: docker-sock
                    volumes:
                    - name: docker-sock
                    hostPath:
                        path: /var/run/docker.sock
            
            '''
        }
    }

    node() {
      def nodeJS = tool name: 'node16', type: 'nodejs'
      env.PATH = "${nodeJS}/bin:${env.PATH}"
      //def server = Artifactory.server 'jfrog_artifactory_01'
      //def buildNpm = Artifactory.newNpmBuild()
      //def buildInfo
      ansiColor('xterm') {
        stage("1. PreTasks") {
          parallel(
            failFast: true,
	          "1.1 clean workspace": {
              try {
                cleanWs()
	              runNextStage = true
	              emailext body: '\'\'\'<a href="${BUILD_URL}input">click to approve</a>\'\'\'', recipientProviders: [requestor(), buildUser(), contributor(), brokenBuildSuspects()], subject: '[Jenkins]${currentBuild.fullDisplayName}', to: 'anand.bansal@osttra.com'
	            }
	            catch(Exception e) {
                runNextStage = false
                error "\u274C workspace clean stage failed, exiting... \u2639"
	            }
	            nextStage = "checkout"
	          },
            "1.2 Code Checkout": {
              try {
                while (nextStage != "checkout") {
                  continue;
                }
                if (runNextStage.toString() == "false") {
                  Utils.markStageSkippedForConditional("1.2 Code Checkout")
                }
                else {
                  checkout scm
                  runNextStage = true
                }
              }
              catch(Exception e) {
                runNextStage = false
                error "\u274C Code Checkout stage failed, exiting... \u2639"
              }
              nextStage = "npm Build"
            }
	        ) 
        }
        stage("2. Build") {
            parallel (
              failFast: true,
              "2.1 npm Build": {
                try {
                  while (nextStage != "npm Build") {
                    continue;
                  }
                  if (runNextStage.toString() == "false") {
                    Utils.markStageSkippedForConditional("2.1 npm Build")
                  }
                  else {
                    println "\u261E Running frontend build, Please Wait... \u270B"
                    def server = Artifactory.server "jf01"
                    def buildNpm = Artifactory.newNpmBuild()
                    def buildInfo
                    buildNpm.deployer server: server, repo: "dms-npm"
                    buildNpm.resolver server: server, repo: "dms-npm"
                    buildInfo = Artifactory.newBuildInfo()
                    buildNpm.install buildInfo: buildInfo, path: '.'
                    // sh "npm run test"

                    //build.npm_build()
                    runNextStage = true
                    
                  }
                }
                catch(Exception e) {
                  runNextStage = false
                    error "\u274C build npm Build stage failed, exiting... \u2639"
                }
                nextStage = "Sonar Scanner"
              },

              "2.2 Sonar Scanner": {
                dir("${env.WORKSPACE}") {
                  try {
                   while (nextStage != "Sonar Scanner") {
                    continue;
                  }
		              if (runNextStage.toString() == "false") {
                    Utils.markStageSkippedForConditional("2.2 Sonar Scanner")
                  }
		              else {
                      //build.sonar_scanner1("env.BRANCH_NAME","${config.sonarProjectKey}")
                      println "code quality scanning Integration Work in progress..."
		                  runNextStage = true
                  }
                }
		            catch(Exception e) {
                  runNextStage = false
                  error "\u274C Build code quality scanning stage failed, exiting... \u2639"
                }
		            nextStage = "Sonar Validation"
              }

            },

            "2.3 Sonar Validation": {
              try {
                while (nextStage != "Sonar Validation") {
                  continue;
                }
                if (runNextStage.toString() == "false") {
                  Utils.markStageSkippedForConditional("2.3 Sonar Validation")
                }
                else {
                  //build.sonar_validation()
                  println "validation Integration Work in progress..."
                  runNextStage = true
                }
              }
              catch(Exception e) {
                runNextStage = false
                error "\u274C build Validation stage failed, exiting... \u2639"
              }
              nextStage = "Checkmarx"
            }
          )
        }
      }
    }
  }
  

  catch(Exception e) {
    error "\u274C Build pipeline failed, please check logs... \u2639"
  }
}