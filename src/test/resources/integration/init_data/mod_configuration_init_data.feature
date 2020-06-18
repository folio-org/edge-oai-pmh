Feature: init data for mod-configuration

  Background:
    * url baseUrl
    * call login testUser
    * def edgeUrl = 'http://localhost:8082/oai/eyJzIjoiQlBhb2ZORm5jSzY0NzdEdWJ4RGgiLCJ0IjoidGVzdF9vYWlwbWgiLCJ1IjoidGVzdC11c2VyIn0='

  Scenario: set errors to 500 Http status
    Given path 'configurations/entries'
    And param query = 'module==OAIPMH and configName==behavior'
    And header Accept = 'application/json'
    And header Content-Type = 'application/json'
    And header x-okapi-token = okapitoken
    When method GET
    Then status 200

    * def configId = get response.configs[0].id

    Given path 'configurations/entries', configId
    And header Accept = 'application/json'
    And header Content-Type = 'application/json'
    And header x-okapi-token = okapitoken
    And request
    """
    {
       "module" : "OAIPMH",
       "configName" : "behavior",
       "enabled" : true,
       "value" : "{\"deletedRecordsSupport\":\"no\",\"suppressedRecordsProcessing\":\"false\",\"errorsProcessing\":\"500\"}"
    }
    """
    When method PUT
    Then status 204
