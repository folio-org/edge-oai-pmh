Feature: Test integration with mod-configuration during Posting the mod-oai-pmh module for tenant

  Background:
    * url baseUrl
    * def result = call getModuleIdByName {tenant: #(testTenant), moduleName: mod-oai-pmh}
    * def moduleId = result.response[0].id
    * def module = {tenant: #(testTenant), moduleId: #(moduleId)}
    * callonce login testUser

  Scenario: Should post default configs to mod-configuration and enable the module when mod-config does not contain the data
    * def result = call read('mod-configuration/get_oaipmh_configs.feature')
    * def configResponse = result.response
    * def func = function(configResponse){return configResponse.configs.map(config=>config.id)}
    * print 'response - '+result.response
    * def configIds = func(configResponse)
    * print 'CONFIG IDS - '+configIds

    Given call deleteModule $module
    Given call read('mod-configuration/delete_config_by_id.feature') configIds
    Given call enableModule $module
    Given path '/configurations/entries'
    And header Content-Type = 'application/json'
    And header x-okapi-tenant = testTenant
    And header x-okapi-token = okapitoken
    When method POST
    Then status 201
    And match $ contains {configName : "behavior"}
    And match $ contains {configName : "technical"}
    And match $ contains {configName : "general"}

#  Scenario: Should post missing default configs to mod-configuration and enable module when mod-config has only part of oaipmh configuration groups
#    * def response = call read('mod-configuration/get_oaipmh_configs.feature')
#    * def configIds = $response[*].response[0].id
#    * print <result of evaluation 'response[*].response[0].id'>
#    * print configIds
#
#    Given call deleteModule $module
#    Given call read('mod-configuration/delete_config_by_id.feature') configIds
#    Given path '/configurations/entries'
#    And header Content-Type = 'application/json'
#    And header x-okapi-tenant = testTenant
#    And header x-okapi-token = okapitoken
#    And request
#    """
#    {
#      "module" : "OAIPMH",
#      "configName" : "technical",
#      "enabled" : true,
#      "value" : "{\"maxRecordsPerResponse\":\"50\",\"enableValidation\":\"false\",\"formattedOutput\":\"false\"}"
#    }
#    """
#    When method POST
#    Then status 201
#
#    Given call enableModule $module
#    Given path '/configurations/entries'
#    And header Content-Type = 'application/json'
#    And header x-okapi-tenant = testTenant
#    And header x-okapi-token = okapitoken
#    When method POST
#    Then status 201
#    And match $ contains {configName : "behavior"}
#    And match $ contains {configName : "technical"}
#    And match $ contains {configName : "general"}
#
#  Scenario: Should just enable module when mod-configuration already contains all related configs
#    * def response = call read('mod-configuration/get_oaipmh_configs.feature')
#    * def configIds = $response[*].response[0].id
#
#    Given call deleteModule $module
#    Given call enableModule $module
#    Given path '/configurations/entries'
#    And header Content-Type = 'application/json'
#    And header x-okapi-tenant = testTenant
#    And header x-okapi-token = okapitoken
#    When method POST
#    Then status 201
#    And match $ contains {configName : "behavior"}
#    And match $ contains {configName : "technical"}
#    And match $ contains {configName : "general"}
#
