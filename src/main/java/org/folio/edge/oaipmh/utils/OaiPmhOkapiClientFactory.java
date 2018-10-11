package org.folio.edge.oaipmh.utils;

import io.vertx.core.Vertx;
import org.folio.edge.core.utils.OkapiClientFactory;

public class OaiPmhOkapiClientFactory extends OkapiClientFactory {

  public OaiPmhOkapiClientFactory(Vertx vertx, String okapiURL, long reqTimeoutMs) {
    super(vertx, okapiURL, reqTimeoutMs);
  }

  public OaiPmhOkapiClient getOaiPmhOkapiClient(String tenant) {
    return new OaiPmhOkapiClient(vertx, okapiURL, tenant, reqTimeoutMs);
  }
}
