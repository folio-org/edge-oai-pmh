package org.folio.edge.oaipmh;

import io.vertx.ext.web.RoutingContext;
import org.folio.edge.core.Handler;
import org.folio.edge.core.security.SecureStore;
import org.folio.edge.core.utils.OkapiClientFactory;

public class OaiPmhHandler extends Handler {

  public OaiPmhHandler(SecureStore secureStore, OkapiClientFactory ocf) {
    super(secureStore, ocf);
  }

  protected void handle(RoutingContext ctx) {
    //TODO
  }
}
