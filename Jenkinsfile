buildMvn {
  publishModDescriptor = 'yes'
  mvnDeploy = 'no'

  buildNode = 'jenkins-agent-java21'
  doDocker = {
    buildJavaDocker {
      publishMaster = 'yes'
      //healthChk for /admin/health in OaiPmhTest.java
    }
  }
}
