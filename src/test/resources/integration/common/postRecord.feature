@Ignore
Feature: postRecord

  Background:
    * url baseUrl

  Scenario:
    Given path 'source-storage/records'
    And header Accept = 'application/json'
    And header x-okapi-tenant = tenant
    * def record = read('marc_record.json')
    * set record.id = id
    * set record.externalIdsHolder.instanceId = instanceId
    And request record
    When method POST
    Then status 201
