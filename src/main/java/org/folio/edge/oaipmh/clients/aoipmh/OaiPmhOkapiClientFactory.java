package org.folio.edge.oaipmh.clients.aoipmh;

import org.folio.edge.core.utils.OkapiClientFactory;

import io.vertx.core.Vertx;

public class OaiPmhOkapiClientFactory extends OkapiClientFactory {

  public OaiPmhOkapiClientFactory(Vertx vertx, String okapiURL, long reqTimeoutMs) {
    super(vertx, okapiURL, reqTimeoutMs);
  }

  public OaiPmhOkapiClient getOaiPmhOkapiClient(String tenant) {
    return new OaiPmhOkapiClient(vertx, okapiURL, tenant, reqTimeoutMs);
  }
}
