## 1.0.0 Unreleased
 * Initial commit ([EDGOAIPMH-2](https://issues.folio.org/projects/EDGOAIPMH/issues/EDGOAIPMH-2))
 * Module/Deployment Descriptors added in scope of [EDGOAIPMH-5](https://issues.folio.org/projects/EDGOAIPMH/issues/EDGOAIPMH-5)
 * In scope of [EDGOAIPMH-4](https://issues.folio.org/projects/EDGOAIPMH/issues/EDGOAIPMH-4) added:
    + RAML file
    + OAI-PMH Schema: [OAI-PMH.xsd](http://www.openarchives.org/OAI/2.0/OAI-PMH.xsd) (please refer to [OAI-PMH specification](http://www.openarchives.org/OAI/openarchivesprotocol.html#OAIPMHschema) for more details)
 * POJO binding generation of [OAI-PMH.xsd](http://www.openarchives.org/OAI/2.0/OAI-PMH.xsd) added in scope of [EDGOAIPMH-7](https://issues.folio.org/projects/EDGOAIPMH/issues/EDGOAIPMH-7)
 * Initial implmentation
    + Details TBD
 * Added the ability to specify the API key on the path, e.g. `GET /oai/<apiKey>?verb=...` and `POST /oai/<apiKey>?verb=...`.  This will allow testing of incomplete responses/resumptionTokens via the marcEdit OAI-PMH harvester.  You can also specify the API Key via the Authorization header or the `apikey` (case sensitive) query argument.  See [Edge-Common](https://github.com/folio-org/edge-common/#api-key-sources) for details.
