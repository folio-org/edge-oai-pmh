package org.folio.edge.oaipmh.clients;

import org.folio.edge.core.utils.OkapiClientFactory;

import io.vertx.core.Vertx;

public class OaiPmhOkapiClientFactory extends OkapiClientFactory {

  public OaiPmhOkapiClientFactory(Vertx vertx, String okapiURL, int reqTimeoutMs) {
    super(vertx, okapiURL, reqTimeoutMs);
  }

  public OaiPmhOkapiClient getOaiPmhOkapiClient(String tenant) {
    return new OaiPmhOkapiClient(vertx, okapiURL, tenant, reqTimeoutMs);
  }

  public ConsortiaTenantClient getConsortiaTenantClient(String tenant) {
    return new ConsortiaTenantClient(vertx, okapiURL, tenant, reqTimeoutMs);
  }
}
