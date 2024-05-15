# edge-oai-pmh

Copyright (C) 2018-2023 The Open Library Foundation

This software is distributed under the terms of the Apache License,
Version 2.0. See the file "[LICENSE](LICENSE)" for more information.

## Introduction
Edge API for Metadata Harvesting ([OAI-PMH Version 2.0](http://www.openarchives.org/OAI/openarchivesprotocol.html))

## Additional information
### Schemas
The following schemas used:
 + OAI-PMH Schema: [OAI-PMH.xsd](http://www.openarchives.org/OAI/2.0/OAI-PMH.xsd) (please refer to [OAI-PMH specification](http://www.openarchives.org/OAI/openarchivesprotocol.html#OAIPMHschema) for more details)

### Required Permissions
Institutional users should be granted the following permissions in order to use this edge API:
- `oai-pmh.all`

### Multi-tenant (consortia) harvesting setup
In order to perform harvesting across consortium tenants, central tenant's institutional user should be granted additional permission `user-tenants.collection.get`. Also, additional system users should be created for each consortia member tenant, with same as central tenant's `username` and granted `oai-pmh.all` permission.

### Configuration
Please refer to the [Configuration](https://github.com/folio-org/edge-common/blob/master/README.md#configuration) section in the [edge-common](https://github.com/folio-org/edge-common/blob/master/README.md) documentation to see all available system properties and their default values.
For stable operation, the application requires the following memory configuration. Java: -XX:MetaspaceSize=384m -XX:MaxMetaspaceSize=512m -Xmx1440m. Amazon Container: cpu - 1024, memory - 1512, memoryReservation - 1360.

For example, to enable HTTP compression based on `Accept-Encoding` header the `-Dresponse_compression=true` should be specified as VM option.

### System Properties

| Property               | Default           | Description                                                             |
|------------------------|-------------------|-------------------------------------------------------------------------|
| `port`                 | `8081`            | Server port to listen on                                                |
| `okapi_url`            | *required*        | Where to find Okapi (URL)                                               |
| `request_timeout_ms`   | `30000`           | Request Timeout                                                         |
| `ssl_enabled`          | `false`           | Set whether SSL/TLS is enabled for Vertx Http Server                    |
| `keystore_type`        | `NA`              | Set the key store type                                                  |
| `keystore_provider`    | `NA`              | Set the provider name of the key store                                  |
| `keystore_path`        | `NA`              | Set the path to the key store                                           |
| `keystore_password`    | `NA`              | Set the password for the key store                                      |
| `key_alias`            | `NA`              | Optional identifier that points to a specific key within the key store  |
| `key_alias_password`   | `NA`              | Optional param that points to a password of `key_alias` if it protected |
| `log_level`            | `INFO`            | Log4j Log Level                                                         |
| `token_cache_capacity` | `100`             | Max token cache size                                                    |
| `token_cache_ttl_ms`   | `100`             | How long to cache JWTs, in milliseconds (ms)                            |
| `secure_store`         | `Ephemeral`       | Type of secure store to use.  Valid: `Ephemeral`, `AwsSsm`, `Vault`     |
| `secure_store_props`   | `NA`              | Path to a properties file specifying secure store configuration         |

### Issue tracker
See project [EDGOAIPMH](https://issues.folio.org/browse/EDGOAIPMH)
at the [FOLIO issue tracker](https://dev.folio.org/guidelines/issue-tracker).

### Other documentation
Other [modules](https://dev.folio.org/source-code/#server-side) are described,
with further FOLIO Developer documentation at
[dev.folio.org](https://dev.folio.org/)
