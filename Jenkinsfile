properties(
    [
        [
            $class  : 'jenkins.model.BuildDiscarderProperty',
            strategy: [
                $class              : 'LogRotator',
                numToKeepStr        : '5',
                artifactNumToKeepStr: '5'
            ]
        ]
    ]
)

node {
    def gradleCmd = "./gradlew "
    docker.image('openjdk:11').inside("-u root --network container:${name}") {
        stage('checkout') {
            checkout scm
        }

        stage('build') {
            sh "java -version"
            sh gradleCmd + " clean assemble --no-daemon"
        }

        stage('publish') {
            try {
                sh gradleCmd + "publish --no-daemon"
                archiveArtifacts artifacts: '**/build/distributions/*.zip', fingerprint: true
            } finally {
                sh gradleCmd + "clean --no-daemon"
            }
        }
    }
}
