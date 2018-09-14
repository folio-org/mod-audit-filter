

buildMvn {
  publishModDescriptor = 'yes'
  mvnDeploy = 'yes'
  publishAPI = 'no'
  runLintRamlCop = 'no'

  doDocker = {
    buildJavaDocker {
      publishMaster = 'yes'
      healthChk = 'yes'
      healthChkCmd = 'curl -sS --fail -o /dev/null  http://localhost:8081/admin/health || exit 1'
    }
  }
}

