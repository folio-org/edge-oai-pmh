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

In order to perform harvesting across consortium tenants, central tenant's institutional user should be granted
additional permission `user-tenants.collection.get`. Also, additional system users should be created for each consortia
member tenant, with same as central tenant's `username` and granted `oai-pmh.all` permission.

### Configuration

Please refer to the [Configuration](https://github.com/folio-org/edge-common/blob/master/README.md#configuration)
section in the [edge-common](https://github.com/folio-org/edge-common/blob/master/README.md) documentation to see all
available system properties and their default values.
For stable operation, the application requires the following memory configuration. Java: -XX:MetaspaceSize=384m -XX:
MaxMetaspaceSize=512m -Xmx1440m. Amazon Container: cpu - 1024, memory - 1512, memoryReservation - 1360.

For example, to enable HTTP compression based on `Accept-Encoding` header the `-Dresponse_compression=true` should be
specified as VM option.

### System Properties

| Property               | Default           | Description                                                             |
|------------------------|-------------------|-------------------------------------------------------------------------|
| `port`                 | `8081`            | Server port to listen on                                                |
| `okapi_url`            | *required*        | Where to find Okapi (URL)                                               |
| `request_timeout_ms`   | `30000`           | Request Timeout                                                         |
| `log_level`            | `INFO`            | Log4j Log Level                                                         |
| `token_cache_capacity` | `100`             | Max token cache size                                                    |
| `token_cache_ttl_ms`   | `100`             | How long to cache JWTs, in milliseconds (ms)                            |
| `secure_store`         | `Ephemeral`       | Type of secure store to use.  Valid: `Ephemeral`, `AwsSsm`, `Vault`     |
| `secure_store_props`   | `NA`              | Path to a properties file specifying secure store configuration         |

### Env variables for TLS configuration for Http server

To configure Transport Layer Security (TLS) for HTTP server in edge module, the following configuration parameters
should be used.
Parameters marked as Required are required only in case when ssl_enabled is set to true.

| Property                                             | Default           | Description                                                                                 |
|------------------------------------------------------|-------------------|---------------------------------------------------------------------------------------------|
| `SPRING_SSL_BUNDLE_JKS_WEB-SERVER_KEYSTORE_TYPE`     | `NA`              | (Required). Set the type of the keystore. Common types include `JKS`, `PKCS12`, and `BCFKS` |
| `SPRING_SSL_BUNDLE_JKS_WEB-SERVER_KEYSTORE_LOCATION` | `NA`              | (Required). Set the location of the keystore file in the local file system                  |
| `SPRING_SSL_BUNDLE_JKS_WEB-SERVER_KEYSTORE_PASSWORD` | `NA`              | (Required). Set the password for the keystore                                               |
| `SPRING_SSL_BUNDLE_JKS_WEB-SERVER_KEY_ALIAS`         | `NA`              | Set the alias of the key within the keystore.                                               |

### Env variables for TLS configuration for Web Client

To configure Transport Layer Security (TLS) for Web clients in the edge module, you can use the following configuration
parameters.
Truststore parameters for configuring Web clients are optional even when ssl_enabled = true.
If truststore parameters need to be populated - truststore_type, truststore_path, truststore_password - are required.

| Property                                | Default           | Description                                                                      |
|-----------------------------------------|-------------------|----------------------------------------------------------------------------------|
| `FOLIO_CLIENT_TLS_ENABLED`              | `false`           | Set whether SSL/TLS is enabled for Vertx Http Server                             |
| `FOLIO_CLIENT_TLS_TRUSTSTORETYPE`       | `NA`              | Set the type of the keystore. Common types include `JKS`, `PKCS12`, and `BCFKS`  |
| `FOLIO_CLIENT_TLS_TRUSTSTOREPATH`       | `NA`              | Set the location of the keystore file in the local file system                   |
| `FOLIO_CLIENT_TLS_TRUSTSTOREPASSWORD`   | `NA`              | Set the password for the keystore                                                |

### Issue tracker

See project [EDGOAIPMH](https://issues.folio.org/browse/EDGOAIPMH)
at the [FOLIO issue tracker](https://dev.folio.org/guidelines/issue-tracker).

### Other documentation

Other [modules](https://dev.folio.org/source-code/#server-side) are described,
with further FOLIO Developer documentation at
[dev.folio.org](https://dev.folio.org/)
