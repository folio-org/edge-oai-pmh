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

In case when multi-tenant harvesting is needed, institutional users should be granted additional permission:
- `user-tenants.collection.get`

Additionally, edge system users within each tenant must have same `username`, for example:
```
secureStore.type=Ephemeral
# a comma separated list of tenants
tenants=central_tenant,tenant1,tenant2
#######################################################
# For each tenant, the institutional user password...
#######################################################
central_tenant=edge-user,password
tenant1=edge-user,password1
tenant2=edge-user,password2
```

### Configuration
Please refer to the [Configuration](https://github.com/folio-org/edge-common/blob/master/README.md#configuration) section in the [edge-common](https://github.com/folio-org/edge-common/blob/master/README.md) documentation to see all available system properties and their default values.
For stable operation, the application requires the following memory configuration. Java: -XX:MetaspaceSize=384m -XX:MaxMetaspaceSize=512m -Xmx1440m. Amazon Container: cpu - 1024, memory - 1512, memoryReservation - 1360.

For example, to enable HTTP compression based on `Accept-Encoding` header the `-Dresponse_compression=true` should be specified as VM option.

### Issue tracker
See project [EDGOAIPMH](https://issues.folio.org/browse/EDGOAIPMH)
at the [FOLIO issue tracker](https://dev.folio.org/guidelines/issue-tracker).

### Other documentation
Other [modules](https://dev.folio.org/source-code/#server-side) are described,
with further FOLIO Developer documentation at
[dev.folio.org](https://dev.folio.org/)
