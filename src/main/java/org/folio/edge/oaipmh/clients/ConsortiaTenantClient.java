package org.folio.edge.oaipmh.clients;

import static org.apache.commons.lang3.ObjectUtils.isNotEmpty;

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import lombok.extern.slf4j.Slf4j;
import org.folio.edge.core.utils.OkapiClient;
import org.folio.rest.jaxrs.model.UserTenantCollection;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Slf4j
public class ConsortiaTenantClient extends OkapiClient {
  private static final String USER_TENANTS_ENDPOINT_LIMIT_1 = "/user-tenants?limit=1";
  private final OkapiClient okapiClient;

  public ConsortiaTenantClient(OkapiClient client) {
    super(client);
    okapiClient = client;
  }

  public Future<List<String>> getConsortiaTenants(MultiMap headers) {
    return this.get(okapiURL + USER_TENANTS_ENDPOINT_LIMIT_1, tenant, headers)
      .map(resp -> resp.bodyAsJson(UserTenantCollection.class))
      .compose(collection -> processUserTenants(collection, headers));
  }

  private Future<List<String>> processUserTenants(UserTenantCollection userTenantCollection, MultiMap headers) {
    var userTenants = userTenantCollection.getUserTenants();
    if (isNotEmpty(userTenants)) {
      var centralTenantId = userTenants.get(0).getCentralTenantId();
      if (Objects.equals(tenant, centralTenantId)) {
        var consortiaClient = new ConsortiaClient(okapiClient);
        consortiaClient.setToken(getToken());
        return consortiaClient.getTenantList(tenant, headers);
      }
    }
    return Future.succeededFuture(Collections.singletonList(tenant));
  }
}
