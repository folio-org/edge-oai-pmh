package org.folio.edge.oaipmh.clients;

import static org.apache.commons.lang3.ObjectUtils.isNotEmpty;

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import lombok.extern.slf4j.Slf4j;
import org.folio.edge.core.utils.OkapiClient;
import org.folio.rest.jaxrs.model.UserTenantCollection;

import java.util.Collections;
import java.util.List;

@Slf4j
public class ConsortiaTenantClient extends OkapiClient {
  private static final String USER_TENANTS_ENDPOINT_LIMIT_1 = "/user-tenants?limit=1";

  public ConsortiaTenantClient(OkapiClient client) {
    super(client);
  }

  ConsortiaTenantClient(Vertx vertx, String okapiURL, String tenant, int timeout) {
    super(vertx, okapiURL, tenant, timeout);
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
      if (centralTenantId.equals(tenant)) {
        var consortiaClient = new ConsortiaClient(vertx, okapiURL, centralTenantId, reqTimeout);
        consortiaClient.setToken(getToken());
        return consortiaClient.getTenantList(tenant, headers);
      }
    }
    return Future.succeededFuture(Collections.singletonList(tenant));
  }
}
