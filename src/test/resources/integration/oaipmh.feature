Feature: mod-oai-pmh tests

  Background:
    * url baseUrl
    * table modules
      | name                                       |
      | 'mod-oai-pmh'                              |
      | 'mod-login'                                |
      | 'mod-configuration'                        |

    * def testTenant = 'test_oaipmh'

    * def testUser = {tenant: '#(testTenant)', name: 'test-user', password: 'test'}

    * table adminAdditionalPermissions
      | name                              |

    * table userPermissions
      | name                              |
      | 'oai-pmh.all'                     |
      | 'configuration.all'               |



  Scenario: create tenant and users for testing
    Given call read('common/setup-users.feature')

  Scenario: init global data
    * call login testUser

    * callonce read('init_data/srs_init_data.feature')
    * callonce read('init_data/mod_configuration_init_data.feature')
    * callonce read('init_data/mod_inventory_init_data.feature')

  Scenario: new functionality test
    Given call read('new_tests/new-oaipmh-tests.feature')

  Scenario: test oai-pmh
    Given call read('base_oai_pmh_tests/oaipmh-cases.feature')

  Scenario: wipe data
    Given call read('common/destroy-data.feature')
