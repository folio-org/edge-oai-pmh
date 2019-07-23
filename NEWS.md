## 2.0.1 Released

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
