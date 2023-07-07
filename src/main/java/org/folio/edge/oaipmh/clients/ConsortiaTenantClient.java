package org.folio.edge.oaipmh.clients;

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.folio.edge.core.utils.OkapiClient;
import org.folio.rest.jaxrs.model.UserTenantCollection;

import java.util.Collections;
import java.util.List;

@Slf4j
public class ConsortiaTenantClient extends OkapiClient {
  private static final String USER_TENANTS_ENDPOINT = "/user-tenants";

  private ConsortiaClient consortiaClient;

  public ConsortiaTenantClient(OkapiClient client) {
    super(client);
  }

  ConsortiaTenantClient(Vertx vertx, String okapiURL, String tenant, int timeout) {
    super(vertx, okapiURL, tenant, timeout);
  }

  public Future<List<String>> getConsortiaTenants(MultiMap headers) {
    return this.get(okapiURL + USER_TENANTS_ENDPOINT, tenant, headers)
      .map(resp -> resp.bodyAsJson(UserTenantCollection.class))
      .compose(collection -> processUserTenants(collection, headers));
  }

  private Future<List<String>> processUserTenants(UserTenantCollection userTenantCollection, MultiMap headers) {
    var userTenants = userTenantCollection.getUserTenants();
    if (ObjectUtils.isEmpty(userTenants)) {
      return Future.succeededFuture(Collections.singletonList(tenant));
    }
    var centralTenantId = userTenants.get(0).getCentralTenantId();
    consortiaClient = new ConsortiaClient(vertx, okapiURL, centralTenantId, reqTimeout);
    return consortiaClient.getTenantList(tenant, headers);

  }
}
