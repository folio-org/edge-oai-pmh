package org.folio.edge.oaipmh;

import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import org.folio.edge.core.EdgeVerticle;
import org.folio.edge.oaipmh.utils.OaiPmhOkapiClientFactory;

public class MainVerticle extends EdgeVerticle {

  @Override
  public Router defineRoutes() {
    OaiPmhOkapiClientFactory ocf = new OaiPmhOkapiClientFactory(vertx, okapiURL, reqTimeoutMs);
    OaiPmhHandler oaiPmhHandler = new OaiPmhHandler(secureStore, ocf);

    Router router = Router.router(vertx);
    router.route().handler(BodyHandler.create());

    router.route(HttpMethod.GET, "/admin/health").handler(this::handleHealthCheck);
    router.route(HttpMethod.GET, "/oai").handler(oaiPmhHandler::handle);
    router.route(HttpMethod.POST, "/oai").handler(oaiPmhHandler::handle);

    return router;
  }
}
