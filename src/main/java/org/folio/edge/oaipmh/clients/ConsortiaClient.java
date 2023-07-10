package org.folio.edge.oaipmh.clients;

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.folio.edge.core.utils.OkapiClient;
import org.folio.rest.jaxrs.model.ConsortiumCollection;
import org.folio.rest.jaxrs.model.Tenant;
import org.folio.rest.jaxrs.model.TenantCollection;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public class ConsortiaClient extends OkapiClient {
  private static final String CONSORTIA_ENDPOINT = "/consortia";
  private static final String CONSORTIA_TENANTS_ENDPOINT_TEMPLATE = "/consortia/%s/tenants";

  ConsortiaClient(Vertx vertx, String okapiURL, String tenant, int timeout) {
    super(vertx, okapiURL, tenant, timeout);
  }

  public Future<List<String>> getTenantList(String initialTenant, MultiMap headers) {
    return get(okapiURL + CONSORTIA_ENDPOINT, tenant, headers)
      .map(resp -> resp.bodyAsJson(ConsortiumCollection.class))
      .compose(collection -> processConsortiaCollection(collection, initialTenant, headers));
  }

  private Future<List<String>> processConsortiaCollection(ConsortiumCollection collection, String initialTenant, MultiMap headers) {
    var consortia = collection.getConsortia();
    if (ObjectUtils.isEmpty(consortia)) {
      return Future.succeededFuture(Collections.singletonList(initialTenant));
    }
    var consortiaId = consortia.get(0).getId();
    return get(okapiURL + String.format(CONSORTIA_TENANTS_ENDPOINT_TEMPLATE, consortiaId), tenant, headers)
      .map(resp -> resp.bodyAsJson(TenantCollection.class))
      .map(TenantCollection::getTenants)
      .map(tenants -> tenants.stream().map(Tenant::getId).collect(Collectors.toList()));
  }
}
