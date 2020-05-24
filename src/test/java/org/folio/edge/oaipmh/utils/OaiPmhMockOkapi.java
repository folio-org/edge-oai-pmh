package org.folio.edge.oaipmh.utils;

import static org.folio.edge.oaipmh.utils.Constants.MOD_OAI_PMH_ACCEPTED_TYPES;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.folio.edge.core.utils.test.MockOkapi;

import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class OaiPmhMockOkapi extends MockOkapi {

  public static final String PATH_TO_GET_RECORDS_MOCK
    = "src/test/resources/mocks/GetRecordResponse.xml";
  public static final String PATH_TO_GET_RECORDS_ERROR_MOCK
    = "src/test/resources/mocks/GetRecordErrorResponse.xml";
  public static final String PATH_TO_IDENTIFY_MOCK
    = "src/test/resources/mocks/IdentifyResponse.xml";
  private static final String GET_RECORD = "GetRecord";
  private static final String IDENTIFY = "Identify";

  public static final long REQUEST_TIMEOUT_MS = 1000L;

  public OaiPmhMockOkapi(int port, List<String> knownTenants) {
    super(port, knownTenants);
  }

  public static String getOaiPmhResponseAsXml(Path pathToXmlFile) {
    String xml = null;
    try {
      xml = new String(Files.readAllBytes(pathToXmlFile));
    } catch (IOException e) {
      log.error("Error in file reading: " + e.getMessage());
    }
    return xml;
  }

  @Override
  public void start(TestContext context) {

    // Setup Mock Okapi and enable compression
    HttpServer server = vertx.createHttpServer(new HttpServerOptions()
      .setCompressionSupported(true));

    final Async async = context.async();
    server.requestHandler(defineRoutes()::accept).listen(okapiPort, result -> {
      if (result.failed()) {
        log.warn(result.cause().getMessage());
      }
      context.assertTrue(result.succeeded());
      async.complete();
    });
  }

  @Override
  public Router defineRoutes() {
    Router router = super.defineRoutes();
    router.route(HttpMethod.GET, "/oai/verbs*").handler(this::oaiPmhHandler);
    return router;
  }

  private void oaiPmhHandler(RoutingContext ctx) {

    HttpServerRequest request = ctx.request();
    MultiMap requestParams = request.params();
    String path = request.path();
    String accept = request.getHeader(HttpHeaders.ACCEPT);

    if(accept != null &&
      !accept.equals(MOD_OAI_PMH_ACCEPTED_TYPES)) {
      log.debug("Unsupported MIME type requested: " + accept);
      ctx.response()
        .setStatusCode(400)
        .putHeader(HttpHeaders.CONTENT_TYPE, "text/plain")
        .end("Accept header must be [\"application/xml\",\"text/plain\"] for this request, but it is \"text/xml\", cannot send */*");
    } else if (paramsContainVerbWithName(requestParams, GET_RECORD)
      && paramsContainParamWithValue(requestParams, "oai:arXiv.org:cs/0112017")) {
      ctx.response()
        .setStatusCode(200)
        .putHeader(HttpHeaders.CONTENT_TYPE, Constants.TEXT_XML_TYPE)
        .end(getOaiPmhResponseAsXml(Paths.get(PATH_TO_GET_RECORDS_MOCK)));
    } else if (paramsContainVerbWithName(requestParams, GET_RECORD)
      && paramsContainParamWithValue(requestParams, "oai:arXiv.org:quant-ph/02131001")) {
      ctx.response()
        .setStatusCode(404)
        .putHeader(HttpHeaders.CONTENT_TYPE, Constants.TEXT_XML_TYPE)
        .end(getOaiPmhResponseAsXml(Paths.get(PATH_TO_GET_RECORDS_ERROR_MOCK)));
    } else if (paramsContainVerbWithName(requestParams, IDENTIFY)) {
      ctx.response()
        .setStatusCode(200)
        .putHeader(HttpHeaders.CONTENT_TYPE, Constants.TEXT_XML_TYPE)
        .end(getOaiPmhResponseAsXml(Paths.get(PATH_TO_IDENTIFY_MOCK)));
    } else if (paramsContainVerbWithName(requestParams, GET_RECORD)
      && paramsContainParamWithValue(requestParams, "exception")) {
      log.debug("Starting OKAPI exception...");
      throw new NullPointerException("NPE OKAPI mock emulation");
    } else if (path.contains("TimeoutException")) {
      vertx.setTimer(REQUEST_TIMEOUT_MS + 1L, event -> log.debug("OKAPI client should throw TimeoutException"));
    }
  }

  private static boolean paramsContainVerbWithName(MultiMap requestParams, String verbName) {
    return requestParams.get(Constants.VERB).equals(verbName);
  }

  private static boolean paramsContainParamWithValue(MultiMap requestParams, String value) {
    return requestParams.entries().stream()
      .anyMatch(entry -> entry.getValue().equals(value));
  }

}
