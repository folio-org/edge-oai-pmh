function fn() {

  karate.configure('logPrettyRequest', true);
  karate.configure('logPrettyResponse', true);

  var env = karate.env;

  // specify runId property for tenant postfix to avoid close connection issues
  // once we run tests again
  var runId = karate.properties['runId'];

  var config = {
    baseUrl: 'http://localhost:9130',
    admin: {tenant: 'diku', name: 'diku_admin', password: 'admin'},
    runId: runId ? runId: '',

    // define global features
    login: karate.read('common/login.feature'),
    dev: karate.read('common/dev.feature'),
    getModuleIdByName: karate.read('common/module.feature@getModuleIdByName'),
    enableModule: karate.read('common/module.feature@enableModule'),
    deleteModule: karate.read('common/module.feature@deleteModule'),
    // define global functions
    uuid: function () {
      return java.util.UUID.randomUUID() + ''
    },

    random: function (max) {
      return Math.floor(Math.random() * max)
    }
  };

  if (env == 'testing') {
    // dikuid=f8f97da4-c39a-548c-8092-3f297bc7779d

    config.baseUrl = 'https://folio-testing-okapi.aws.indexdata.com';
    config.admin = {tenant: 'supertenant', name: 'testing_admin', password: 'admin'};
    config.edgeHost = 'https://folio-testing.aws.indexdata.com:8000';
    config.edgeApiKey = 'eyJzIjoiNXNlNGdnbXk1TiIsInQiOiJkaWt1IiwidSI6ImRpa3UifQ==';
  } else if (env == 'snapshot') {
    config.baseUrl = 'https://folio-snapshot-okapi.aws.indexdata.com';
    config.admin = {tenant: 'supertenant', name: 'testing_admin', password: 'admin'};
    config.edgeHost = 'https://folio-snapshot.aws.indexdata.com:8000';
    config.edgeApiKey = 'eyJzIjoiNXNlNGdnbXk1TiIsInQiOiJkaWt1IiwidSI6ImRpa3UifQ==';
  } else if (env != null && env.match(/^ec2-\d+/)) {
    // Config for FOLIO CI "folio-integration" public ec2- dns name
    config.baseUrl = 'http://' + env + ':9130';
    config.admin = {
      tenant: 'supertenant',
      name: 'admin',
      password: 'admin'
    }
  }
  return config;
}
