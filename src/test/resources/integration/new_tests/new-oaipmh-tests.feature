@ignore
Feature: Test new oai-pmh functionality

  Background:
    * url baseUrl
    * def edgeUrl = 'http://localhost:8082/oai/eyJzIjoiQlBhb2ZORm5jSzY0NzdEdWJ4RGgiLCJ0IjoidGVzdF9vYWlwbWgiLCJ1IjoidGVzdC11c2VyIn0='
    * call login testUser

  Scenario Outline: set errors to 200 and 500 and check Http status in responses
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
       "value" : "{\"deletedRecordsSupport\":\"no\",\"suppressedRecordsProcessing\":\"false\",\"errorsProcessing\":\"<errorCode>\"}"
    }
    """
    When method PUT
    Then status 204

    Given url edgeUrl
    And param verb = 'ListRecords'
    And param metadataPrefix = 'marc'
    And header Accept = 'text/xml'
    And header Content-Type = 'application/json'
    And header x-okapi-token = okapitoken
    When method GET
    Then status <httpStatus>

    Examples:
      | errorCode | httpStatus |
      | 200       | 200        |
      | 500       | 422        |

  Scenario Outline: check enable and disable OAI service
    Given path 'configurations/entries'
    And param query = 'module==OAIPMH and configName==general'
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
      "configName" : "general",
      "enabled" : true,
      "value" : "{\"administratorEmail\":\"oai-pmh@folio.org\",\"repositoryName\":\"FOLIO_OAI_Repository\",\"enableOaiService\":\"<enableOAIService>\",\"timeGranularity\":\"YYYY-MM-DDThh:mm:ssZ\",\"baseUrl\":\"http://folio.org/oai\"}"
    }
    """
    When method PUT
    Then status 204

    Given url edgeUrl
    And param verb = 'ListRecords'
    And param metadataPrefix = 'marc21_withholdings'
    And header Accept = 'text/xml'
    And header Content-Type = 'application/json'
    And header x-okapi-token = okapitoken
    When method GET
    Then status <httpStatus>

    Examples:
      | enableOAIService | httpStatus |
      | false            | 503        |
      | true             | 200        |

  Scenario: get ListRecords for marc21_withholdings
    Given url edgeUrl
    And header Accept = 'text/xml'
    And header Content-Type = 'application/json'
    And header x-okapi-token = okapitoken
    And param verb = 'ListRecords'
    And param metadataPrefix = 'marc21_withholdings'
    When method GET
    Then status 200

  Scenario: get ListIdentifiers for marc21_withholdings
    Given url edgeUrl
    And header Accept = 'text/xml'
    And header Content-Type = 'application/json'
    And header x-okapi-token = okapitoken
    And param verb = 'ListIdentifiers'
    And param metadataPrefix = 'marc21_withholdings'
    When method GET
    Then status 200

  Scenario: get GetRecord request for marc21_withholdings
    Given url edgeUrl
    And header Accept = 'text/xml'
    And header Content-Type = 'application/json'
    And header x-okapi-token = okapitoken
    And param verb = 'GetRecord'
    And param identifier = 'oai:folio.org:test_oaipmh/6b4ae089-e1ee-431f-af83-e1133f8e3da0'
    And param metadataPrefix = 'marc21_withholdings'
    When method GET
    Then status 200

    # Unhappy path cases

  Scenario: check badArgument in GetRecord request without identifier for marc21_withholdings
    Given url edgeUrl
    And header Accept = 'text/xml'
    And header Content-Type = 'application/json'
    And header x-okapi-token = okapitoken
    And param verb = 'GetRecord'
    And param metadataPrefix = 'marc21_withholdings'
    When method GET
    Then status 400

  Scenario: check badArgument in GetRecord request with invalid identifier for marc21_withholdings
    Given url edgeUrl
    And header Accept = 'text/xml'
    And header Content-Type = 'application/json'
    And header x-okapi-token = okapitoken
    And param verb = 'GetRecord'
    And param identifier = 'invalid'
    And param metadataPrefix = 'marc21_withholdings'
    When method GET
    Then status 400

  Scenario: check badArgument in ListRecords with invalid from for marc21_withholdings
    Given url edgeUrl
    And header Accept = 'text/xml'
    And header Content-Type = 'application/json'
    And header x-okapi-token = okapitoken
    And param verb = 'ListRecords'
    And param metadataPrefix = 'marc21_withholdings'
    And param from = 'junk'
    When method GET
    Then status 400

  Scenario: check badArgument in ListRecords with invalid resumptionToken for marc21_withholdings
    Given url edgeUrl
    And header Accept = 'text/xml'
    And header Content-Type = 'application/json'
    And header x-okapi-token = okapitoken
    And param verb = 'ListRecords'
    And param resumptionToken = 'junk'
    And param metadataPrefix = 'marc21_withholdings'
    When method GET
    Then status 400

  Scenario: check badArgument in ListRecords with invalid until for marc21_withholdings
    Given url edgeUrl
    And header Accept = 'text/xml'
    And header Content-Type = 'application/json'
    And header x-okapi-token = okapitoken
    And param verb = 'ListRecords'
    And param metadataPrefix = 'marc21_withholdings'
    And param until = 'junk'
    When method GET
    Then status 400

    #Checking for version 2.0 specific exceptions

  Scenario: check badArgument in ListRecords with invalid format date for marc21_withholdings
    Given url edgeUrl
    And header Accept = 'text/xml'
    And header Content-Type = 'application/json'
    And header x-okapi-token = okapitoken
    And param verb = 'ListRecords'
    And param metadataPrefix = 'marc21_withholdings'
    And param from = '2002-02-05'
    And param until = '2002-02-06T05:35:00Z'
    When method GET
    Then status 400

  Scenario: check noRecordsMatch in ListRecords request for marc21_withholdings
    Given url edgeUrl
    And header Accept = 'text/xml'
    And header Content-Type = 'application/json'
    And header x-okapi-token = okapitoken
    And param verb = 'ListRecords'
    And param metadataPrefix = 'marc21_withholdings'
    And param until = '1969-01-01T00:00:00Z'
    When method GET
    Then status 404

  Scenario: check idDoesNotExist error in GetRecord request for marc21_withholdings
    Given url edgeUrl
    And header Accept = 'text/xml'
    And header Content-Type = 'application/json'
    And header x-okapi-token = okapitoken
    And param verb = 'GetRecord'
    And param identifier = 'oai:folio.org:test_oaipmh/777be1ac-5073-44cc-9925-a6b8955f4a75'
    And param metadataPrefix = 'marc21_withholdings'
    When method GET
    Then status 404
