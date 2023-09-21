package org.folio.edge.oaipmh;

import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;
import static org.folio.edge.core.Constants.SYS_OKAPI_URL;
import static org.folio.edge.core.Constants.SYS_REQUEST_TIMEOUT_MS;

import io.vertx.core.json.jackson.DatabindCodec;
import lombok.extern.slf4j.Slf4j;
import org.folio.edge.core.Constants;
import org.folio.edge.core.EdgeVerticleHttp;
import org.folio.edge.oaipmh.clients.OaiPmhOkapiClientFactory;

import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;

@Slf4j
public class MainVerticle extends EdgeVerticleHttp {

  @Override
  public Router defineRoutes() {
    String okapiURL = config().getString(SYS_OKAPI_URL);
    int reqTimeoutMs = config().getInteger(SYS_REQUEST_TIMEOUT_MS);
    // first call to mod-oai-pmh is supposed to take significant time
    // if the timeout is not set via env vars, it will be 2 hours
    if (reqTimeoutMs == Constants.DEFAULT_REQUEST_TIMEOUT_MS) {
      reqTimeoutMs = 7200000;
    }

    DatabindCodec.mapper().configure(FAIL_ON_UNKNOWN_PROPERTIES, false);

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
