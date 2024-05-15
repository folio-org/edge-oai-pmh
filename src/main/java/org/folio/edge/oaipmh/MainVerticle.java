package org.folio.edge.oaipmh;

import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;

import io.vertx.core.json.jackson.DatabindCodec;
import lombok.extern.slf4j.Slf4j;
import org.folio.edge.core.EdgeVerticleHttp;
import org.folio.edge.core.utils.OkapiClientFactory;
import org.folio.edge.oaipmh.clients.OaiPmhOkapiClientFactory;

import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;

@Slf4j
public class MainVerticle extends EdgeVerticleHttp {

  @Override
  public Router defineRoutes() {
    DatabindCodec.mapper().configure(FAIL_ON_UNKNOWN_PROPERTIES, false);

    OkapiClientFactory ocf = OaiPmhOkapiClientFactory.createInstance(vertx, config());
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
