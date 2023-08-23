buildMvn {
  publishModDescriptor = 'yes'
  mvnDeploy = 'no'

  buildNode = 'jenkins-agent-java17'
  doDocker = {
    buildJavaDocker {
      publishMaster = 'yes'
      healthChk = 'yes'
      healthChkCmd = 'curl -sS --fail -o /dev/null  http://localhost:8081/admin/health || exit 1'
    }
  }
}
