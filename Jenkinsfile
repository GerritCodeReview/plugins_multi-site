    def pluginName = "multi-site"
    def formatCheck = 'gerritforge:multi-site-format-47168e90078b0b3f11401610930e82830e76bff7'
    def buildCheck = 'gerritforge:multi-site-47168e90078b0b3f11401610930e82830e76bff7'
    def parm = { extraPlugins: [ 'pull-replication' ] }
    def pluginScmBaseUrl = "https://gerrit.googlesource.com/a"
    def pluginScmUrl = "${pluginScmBaseUrl}/${env.GERRIT_PROJECT}"
    def gjfVersion = '1.7'
    def javaVersion = 11
    def bazeliskCmd = "#!/bin/bash\n" + ". set-java.sh ${javaVersion} && bazelisk"
    echo "Starting pipeline for plugin '${pluginName}'" + (formatCheck ? " formatCheckId=${formatCheck}" : '') + (buildCheck ? " buildCheckId=${buildCheck}" : '')
    echo "Change : ${env.GERRIT_CHANGE_NUMBER}/${GERRIT_PATCHSET_NUMBER} '${env.GERRIT_CHANGE_SUBJECT}'"
    echo "Change URL: ${env.GERRIT_CHANGE_URL}"
    pipeline {
        options { skipDefaultCheckout true }
        agent { label 'bazel-debian' }
        stages {
            stage('Checkout') {
                steps {
                    withCredentials([usernamePassword(usernameVariable: "GS_GIT_USER", passwordVariable: "GS_GIT_PASS", credentialsId: env.GERRIT_CREDENTIALS_ID)]) {
                        sh 'echo "machine gerrit.googlesource.com login $GS_GIT_USER password $GS_GIT_PASS">> ~/.netrc'
                        sh 'chmod 600 ~/.netrc'
                        sh "git clone -b ${env.GERRIT_BRANCH} ${pluginScmUrl}"
                        sh "cd ${pluginName} && git fetch origin refs/changes/${BRANCH_NAME} && git config user.name jenkins && git config user.email jenkins@gerritforge.com && git merge FETCH_HEAD"
                        script {
                          def extraPlugins = parm.hasProperty("extraPlugins") ? parm.extraPlugins : []
			  extraPlugins.each { plugin -> sh "git clone -b ${GERRIT_BRANCH} ${pluginScmBaseUrl}/plugins/${plugin}" }
                        }
                    }
                }
            }
            stage('Formatting') {
                when {
                    expression { formatCheck }
                }
                steps {
                    gerritCheck (checks: ["${formatCheck}": 'RUNNING'], url: "${env.BUILD_URL}console")
                    sh "find ${pluginName} -name '*.java' | xargs /home/jenkins/format/google-java-format-${gjfVersion} -i"
                    script {
                        def formatOut = sh (script: "cd ${pluginName} && git status --porcelain", returnStdout: true)
                        if (formatOut.trim()) {
                            def files = formatOut.split('\n').collect { it.split(' ').last() }
                            files.each { gerritComment path:it, message: 'Needs reformatting with GJF' }
                            gerritCheck (checks: ["${formatCheck}": 'FAILED'], url: "${env.BUILD_URL}console")
                        } else {
                            gerritCheck (checks: ["${formatCheck}": 'SUCCESSFUL'], url: "${env.BUILD_URL}console")
                        }
                    }
                }
            }
            stage('build') {
                environment {
                    DOCKER_HOST = """${sh(
                         returnStdout: true,
                         script: "/sbin/ip route|awk '/default/ {print  \"tcp://\"\$3\":2375\"}'"
                     )}"""
            }
                steps {
                    script { if (buildCheck) { gerritCheck (checks: ["${buildCheck}": 'RUNNING'], url: "${env.BUILD_URL}console") } }
                    sh 'git clone --recursive -b $GERRIT_BRANCH https://gerrit.googlesource.com/gerrit'
                    dir ('gerrit') {
                        sh "cd plugins && ln -s ../../${pluginName} ."
                        sh "if [ -f ../${pluginName}/external_plugin_deps.bzl ]; then cd plugins && ln -sf ../../${pluginName}/external_plugin_deps.bzl .; fi"
                        sh "if [ -f ../${pluginName}/external_package.json ]; then cd plugins && ln -sf ../../${pluginName}/external_package.json package.json; fi"
                        script {
                            def extraPlugins = parm.hasProperty("extraPlugins") ? parm.extraPlugins : []
                            extraPlugins.each { plugin -> sh "cd plugins && ln -s ../../${plugin} ." }
                        }
                        sh "${bazeliskCmd} build plugins/${pluginName}"
                        sh "${bazeliskCmd} test --test_env DOCKER_HOST=" + '$DOCKER_HOST' + " plugins/${pluginName}/..."
                    }
                }
        }
    }
        post {
            success {
                script { if (buildCheck) { gerritCheck (checks: ["${buildCheck}": 'SUCCESSFUL'], url: "${env.BUILD_URL}console") } }
                gerritReview labels: [Verified: 1]
            }
            unstable {
                script { if (buildCheck) { gerritCheck (checks: ["${buildCheck}": 'FAILED'], url: "${env.BUILD_URL}console") } }
                gerritReview labels: [Verified: -1]
            }
            failure {
                script { if (buildCheck) { gerritCheck (checks: ["${buildCheck}": 'FAILED'], url: "${env.BUILD_URL}console") } }
                gerritReview labels: [Verified: -1]
            }
        }
    }

