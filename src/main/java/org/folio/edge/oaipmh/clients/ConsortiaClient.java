package org.folio.edge.oaipmh.clients;

import static org.apache.commons.lang3.ObjectUtils.isEmpty;

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.folio.edge.core.utils.OkapiClient;
import org.folio.rest.jaxrs.model.ConsortiumCollection;
import org.folio.rest.jaxrs.model.Tenant;
import org.folio.rest.jaxrs.model.TenantCollection;

@Slf4j
public class ConsortiaClient extends OkapiClient {
  private static final String CONSORTIA_ENDPOINT = "/consortia";
  private static final String CONSORTIA_TENANTS_ENDPOINT_TEMPLATE = "/consortia/%s/tenants?limit=";

  public ConsortiaClient(OkapiClient client) {
    super(client);
  }

  public Future<List<String>> getTenantList(String initialTenant, MultiMap headers) {
    return get(okapiURL + CONSORTIA_ENDPOINT, tenant, headers)
              .map(resp -> resp.bodyAsJson(ConsortiumCollection.class))
              .compose(collection -> processConsortiaCollection(collection,
                    initialTenant, headers));
  }

  private Future<List<String>> processConsortiaCollection(ConsortiumCollection collection,
                                                          String initialTenant, MultiMap headers) {
    var consortia = collection.getConsortia();
    if (isEmpty(consortia)) {
      return Future.succeededFuture(Collections.singletonList(initialTenant));
    }
    var consortiaId = consortia.getFirst().getId();
    return get(okapiURL + String.format(CONSORTIA_TENANTS_ENDPOINT_TEMPLATE + Integer.MAX_VALUE,
                consortiaId),
          tenant, headers)
              .map(resp -> resp.bodyAsJson(TenantCollection.class))
              .map(TenantCollection::getTenants)
              .map(tenants -> tenants.stream()
                    .filter(t -> !t.getIsCentral())
                    .map(Tenant::getId)
                    .sorted().toList());
  }
}
