## 2.11.0 - Unreleased

## 2.10.0 - Released

This release includes rmb upgrades

[Full Changelog](https://github.com/folio-org/edge-oai-pmh/compare/v2.9.2...v2.10.0)

### Technical tasks
* [FOLIO-4087](https://folio-org.atlassian.net/browse/FOLIO-4087) - RMB & Spring upgrades (all modules)

## 2.9.2 - Released

This release includes only edge-common upgrade

[Full Changelog](https://github.com/folio-org/edge-oai-pmh/compare/v2.9.1...v2.9.2)

### Technical tasks
* [EDGOAIPMH-120](https://folio-org.atlassian.net/browse/EDGOAIPMH-120) - edge-common 4.7.1: AwsParamStore to support FIPS-approved crypto modules

## 2.9.1 - Released

This release includes TLS support

[Full Changelog](https://github.com/folio-org/edge-oai-pmh/compare/v2.9.0...v2.9.1)

### Technical tasks
* [EDGOAIPMH-118](https://folio-org.atlassian.net/browse/EDGOAIPMH-118) - Vert.x 4.5.7 fixing netty-codec-http form POST OOM CVE-2024-29025
* [EDGOAIPMH-117](https://folio-org.atlassian.net/browse/EDGOAIPMH-117) - Enhance HTTP Endpoint Security with TLS and FIPS-140-2 Compliant Cryptography
* [EDGOAIPMH-116](https://folio-org.atlassian.net/browse/EDGOAIPMH-116) - Enhance WebClient TLS Configuration for Secure Connections to OKAPI

## 2.9.0 - Released

This release includes only edge-common upgrade

[Full Changelog](https://github.com/folio-org/edge-oai-pmh/compare/v2.8.0...v2.9.0)

## 2.8.0 - Released

This release includes updates to RAML Module Builder, fixes for retrieving tenants limit

[Full Changelog](https://github.com/folio-org/edge-oai-pmh/compare/v2.7.2...v2.8.0)

### Technical tasks
* [EDGOAIPMH-114](https://issues.folio.org/browse/EDGOAIPMH-114) - Upgrade RAML Module Builder

### Bug Fixes
* [EDGOAIPMH-110](https://issues.folio.org/browse/EDGOAIPMH-110) - Retrieving tenants limit issue

## 2.7.2 - Unreleased

This release contains update for retrieving tenants limit

[Full Changelog](https://github.com/folio-org/edge-oai-pmh/compare/v2.7.1...v2.7.2)

### Bug fixes
* [EDGOAIPMH-110](https://issues.folio.org/browse/EDGOAIPMH-110) - Retrieving tenants limit issue

## 2.7.1 - Unreleased

This release contains dependency updates

[Full Changelog](https://github.com/folio-org/edge-oai-pmh/compare/v2.7.0...v2.7.1)

### Technical tasks
* [EDGOAIPMH-107](https://issues.folio.org/browse/EDGOAIPMH-107) - Update edge-common version

### Bug fixes
* [EDGOAIPMH-108](https://issues.folio.org/browse/EDGOAIPMH-108) - RMB 35.1.1, Vert.x 4.4.6 fixing Netty/Jackson DoS

## 2.7.0 - Released

This release includes harvesting across tenants, ConsortiaTenant API Client, updates to Java 17

[Full Changelog](https://github.com/folio-org/edge-oai-pmh/compare/v2.6.1...v2.7.0)

### Technical tasks
* [EDGOAIPMH-101](https://issues.folio.org/browse/EDGOAIPMH-101) - Update to Java 17 edge-oai-pmh
* [EDGOAIPMH-100](https://issues.folio.org/browse/EDGOAIPMH-100) - ConsortiaTenant API Client
* [EDGOAIPMH-99](https://issues.folio.org/browse/EDGOAIPMH-99) - Harvesting Across Tenants Orchestrator
* [EDGOAIPMH-81](https://issues.folio.org/browse/EDGOAIPMH-81) - Logging improvement

### Bug fixes
* [EDGOAIPMH-104](https://issues.folio.org/browse/EDGOAIPMH-104) - Issues related to Cross tenant incremental harvest

## 2.6.1 - Released

This release upgrades dependencies.

* [EDGOAIPMH-94](https://issues.folio.org/browse/EDGOAIPMH-94) mod-configuration-client 5.9.1, Vertx 4.3.8, apk upgrade

[Full Changelog](https://github.com/folio-org/edge-oai-pmh/compare/v2.6.0...v2.6.1)

## 2.6.0 - Released

This release includes logging improvements configuration.

[Full Changelog](https://github.com/folio-org/edge-oai-pmh/compare/v2.5.1...v2.6.0)

### Technical tasks

* [EDGOAIPMH-91](https://issues.folio.org/browse/EDGOAIPMH-91) - Logging improvement - Configuration

## 2.5.1 - Released

This release includes bug fixes in the Vert.x dependency.

[Full Changelog](https://github.com/folio-org/edge-oai-pmh/compare/v2.5.0...v2.5.1)

### Bug Fixes

* [EDGOAIPMH-87](https://issues.folio.org/browse/EDGOAIPMH-87) - edge-common 4.4.1 fixing disabled SSL in Vert.x WebClient

## 2.5.0 - Released

This release includes minor improvements and libraries dependencies update

[Full Changelog](https://github.com/folio-org/edge-oai-pmh/compare/v2.4.2...v2.5.0)

### Stories
* [EDGOAIPMH-83](https://issues.folio.org/browse/EDGOAIPMH-83) - Morning Glory 2022 R2 - Vert.x 3.5.4/v4 upgrade
* [EDGOAIPMH-82](https://issues.folio.org/browse/EDGOAIPMH-82) - Handle missing permissions gracefully

## 2.4.0 - Released

This release includes improvements for creating HTTP client and responses when no records harvested

[Full Changelog](https://github.com/folio-org/edge-oai-pmh/compare/v2.3.1...v2.4.0)

### Stories
* [EDGOAIPMH-62](https://issues.folio.org/browse/EDGOAIPMH-62) - Replace the current approach of creating HTTP Clients with keeping a single client for the tenant
* [EDGOAIPMH-60](https://issues.folio.org/browse/EDGOAIPMH-60) - Check if the response contains records

## 2.3.1 - Released

## 2.3.0 - Released

This release includes updating module Java version up to 11

[Full Changelog](https://github.com/folio-org/edge-oai-pmh/compare/v2.2.0...v2.3.0)

### Stories
* [EDGOAIPMH-50](https://issues.folio.org/browse/EDGOAIPMH-50) - Move all request validations from ed

## 2.2.2 - Released

Increasing edge timeout for metadata prefix marc21_withholdings

### Stories
* [MODOAIPMH-226](https://issues.folio.org/browse/MODOAIPMH-226) ListRecords with metdataPrefix=marc21_withholdings timeouts on BugFest

## 2.2.1 - Released

This release includes minor bug fixes for edge-oai-pmh module (Q2/2020).

### Stories
* [EDGOAIPMH-49](https://issues.folio.org/browse/EDGOAIPMH-49) ListRecords with metdataPrefix=marc21_withholdings timeouts on BugFest

## 2.2.0 - Released

This release includes transfer of the business logic to the corresponding business module cause edge is a proxy between clients and pmh  (Q2/2020). 

[Full Changelog](https://github.com/folio-org/edge-oai-pmh/compare/v2.1.2...v2.2.0)

### Stories
* [EDGOAIPMH-131](https://issues.folio.org/browse/MODOAIPMH-131) - Move all request validations from edge module to oai-pmh module & remove interactions with [mod-configuration](https://github.com/folio-org/mod-configuration).

* [MODOAIPMH-86](https://issues.folio.org/browse/MODOAIPMH-86) - add support of text/xml headers.

## 2.1.2 - Released

This is a bugfix release for inclusion in Edelweiss (Q4/2019). 

[Full Changelog](https://github.com/folio-org/edge-oai-pmh/compare/v2.1.1...v2.1.2)

### Stories
* [EDGOAIPMH-39](https://issues.folio.org/browse/EDGOAIPMH-39) - Replace the Accept header when proxying requests to mod-oai-pmh

## 2.1.1 - Released

This is a bugfix release for inclusion in Edelweiss (Q4/2019).  The only change is to update the dependency version of the 'oai-pmh' interface

[Full Changelog](https://github.com/folio-org/edge-oai-pmh/compare/v2.1.0...v2.1.1)

## 2.1.0 - Released

This release includes tuning environment settings

[Full Changelog](https://github.com/folio-org/edge-oai-pmh/compare/v2.0.1...v2.1.0)

### Stories
* [EDGOAIPMH-34](https://issues.folio.org/browse/EDGOAIPMH-34) - Use JVM features to manage container memory
* [FOLIO-2235](https://issues.folio.org/browse/FOLIO-2235) - Add LaunchDescriptor settings to each backend non-core module repository

## 2.0.1 (Released on 07/23/2019)

The only change in this release was to upgrade to latest login interface 6.0

[Full Changelog](https://github.com/folio-org/edge-oai-pmh/compare/v2.0.0...v2.0.1)

## 2.0.0 (Released on 03/09/2019)

The main focus of this release was to upgrade login interface and edge-common dependency to actual versions.

[Full Changelog](https://github.com/folio-org/edge-oai-pmh/compare/v1.0.0...v2.0.0)

### Stories
 * [EDGOAIPMH-26](https://issues.folio.org/browse/EDGOAIPMH-26): login 5.0 interface has been added to the required interfaces in the module descriptor.
 * [EDGOAIPMH-28](https://issues.folio.org/browse/EDGOAIPMH-28): the edge-common dependency upgraded to version 2.0.0 in order to leverage the new API key structure.

## 1.0.0 (Released on 11/27/2018)
 * Initial commit ([EDGOAIPMH-2](https://issues.folio.org/projects/EDGOAIPMH/issues/EDGOAIPMH-2))
 * Module/Deployment Descriptors added in scope of [EDGOAIPMH-5](https://issues.folio.org/projects/EDGOAIPMH/issues/EDGOAIPMH-5)
 * In scope of [EDGOAIPMH-4](https://issues.folio.org/projects/EDGOAIPMH/issues/EDGOAIPMH-4) added:
    + RAML file
    + OAI-PMH Schema: [OAI-PMH.xsd](http://www.openarchives.org/OAI/2.0/OAI-PMH.xsd) (please refer to [OAI-PMH specification](http://www.openarchives.org/OAI/openarchivesprotocol.html#OAIPMHschema) for more details)
 * POJO binding generation of [OAI-PMH.xsd](http://www.openarchives.org/OAI/2.0/OAI-PMH.xsd) added in scope of [EDGOAIPMH-7](https://issues.folio.org/projects/EDGOAIPMH/issues/EDGOAIPMH-7)
 * Initial implementation
    + To access the OAI-PMH repository the apiKey is required which might be provided as part of the URI path, `apikey` parameter or `Authorization` header. Please refer to [Security](https://github.com/folio-org/edge-common#security) section of the the [edge-common](https://github.com/folio-org/edge-common/blob/master/README.md) documentation for the details.
    + The endpoint depends on authorization mechanism chosen. `GET` or `POST` requests are supported. Please refer to [edge-oai-pmh.raml](ramls/edge-oai-pmh.raml) for more details.
    + Once the request is received, the flow is following:
      1. The request is being validated if it valid according to OAI-PMH specification. If some parameters are missing or invalid, the OAI-PMH response with errors is sent back to harvester with `400` http status code.
      2. The `apiKey` is being validated. In case it is missing or invalid, the response with error and `401` http status code is sent back to caller. If the apiKey of valid structure, the service tries to login to FOLIO system. If something is wrong, the response with error is sent back to caller with `403` or `408` http status code.
      3. The request is sent to repository business logic. If something is wrong, the response with error is sent back to caller with `408` or `500` http status code. If all is okay, the OAI-PMH response is being sent to caller with `200`, `400`, `404` or `422` http status code.
 * In scope of the [EDGCOMMON-8](https://issues.folio.org/browse/EDGCOMMON-8) support of the compression is added. By default it is disabled and can be activated by corresponding VM option. The `gzip` and `deflate` compressions supported.
 * [EDGOAIPMH-24](https://issues.folio.org/browse/EDGOAIPMH-24): added the ability to specify the API key on the path, e.g. `GET /oai/<apiKey>?verb=...` and `POST /oai/<apiKey>?verb=...`.  This will allow testing of incomplete responses/resumptionTokens via the marcEdit OAI-PMH harvester.  You can also specify the API Key via the Authorization header or the `apikey` (case sensitive) query argument.  See [Edge-Common](https://github.com/folio-org/edge-common/#api-key-sources) for details.
