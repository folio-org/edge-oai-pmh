package org.folio.edge.oaipmh;

import static org.folio.edge.core.Constants.SYS_OKAPI_URL;
import static org.folio.edge.core.Constants.SYS_REQUEST_TIMEOUT_MS;

import org.folio.edge.core.Constants;
import org.folio.edge.core.EdgeVerticle2;
import org.folio.edge.oaipmh.clients.aoipmh.OaiPmhOkapiClientFactory;

import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;

public class MainVerticle extends EdgeVerticle2 {

  @Override
  public Router defineRoutes() {
    String okapiURL = config().getString(SYS_OKAPI_URL);
    int reqTimeoutMs = config().getInteger(SYS_REQUEST_TIMEOUT_MS);
    // first call to mod-oai-pmh is supposed to take significant time
    // if the timeout is not set via env vars, it will be 2 hours
    if (reqTimeoutMs == Constants.DEFAULT_REQUEST_TIMEOUT_MS) {
      reqTimeoutMs = 7200000;
    }

    OaiPmhOkapiClientFactory ocf = new OaiPmhOkapiClientFactory(vertx, okapiURL, reqTimeoutMs);
    OaiPmhHandler oaiPmhHandler = new OaiPmhHandler(secureStore, ocf);

    Router router = Router.router(vertx);
    router.route().handler(BodyHandler.create());

    router.route(HttpMethod.GET, "/admin/health").handler(this::handleHealthCheck);
    router.route(HttpMethod.GET, "/oai").handler(oaiPmhHandler::handle);
    router.route(HttpMethod.GET, "/oai/:apiKeyPath").handler(oaiPmhHandler::handle);
    router.route(HttpMethod.POST, "/oai").handler(oaiPmhHandler::handle);
    router.route(HttpMethod.POST, "/oai/:apiKeyPath").handler(oaiPmhHandler::handle);

    return router;
  }
}
