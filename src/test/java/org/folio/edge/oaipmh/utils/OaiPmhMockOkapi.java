package org.folio.edge.oaipmh.utils;

import static org.folio.edge.oaipmh.utils.Constants.MOD_OAI_PMH_ACCEPTED_TYPES;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.apache.log4j.Logger;
import org.folio.edge.core.utils.test.MockOkapi;

import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

public class OaiPmhMockOkapi extends MockOkapi {

  public static final String PATH_TO_GET_RECORDS_MOCK
    = "src/test/resources/mocks/GetRecordResponse.xml";
  public static final String PATH_TO_GET_RECORDS_ERROR_MOCK
    = "src/test/resources/mocks/GetRecordErrorResponse.xml";
  public static final String PATH_TO_IDENTIFY_MOCK
    = "src/test/resources/mocks/IdentifyResponse.xml";
  public static final String PATH_TO_ERROR_PROCESSING_CONFIG_SETTING_500
    = "src/test/resources/mocks/GetErrorsProcessingConfigSetting500.json";
  public static final String PATH_TO_GENERAL_CONFIGS
    = "src/test/resources/mocks/GetGeneralConfigs.json";

  public static final long REQUEST_TIMEOUT_MS = 1000L;

  private static Logger logger = Logger.getLogger(OaiPmhMockOkapi.class);

  private String modConfigurationErrorsProcessing;
  private String modConfigurationEnableOaiService;

  public OaiPmhMockOkapi(int port, List<String> knownTenants) {
    super(port, knownTenants);
  }

  public static String getOaiPmhResponseAsXml(Path pathToXmlFile) {
    String xml = null;
    try {
      xml = new String(Files.readAllBytes(pathToXmlFile));
    } catch (IOException e) {
      logger.error("Error in file reading: " + e.getMessage());
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
        logger.warn(result.cause());
      }
      context.assertTrue(result.succeeded());
      async.complete();
    });
  }

  @Override
  public Router defineRoutes() {
    Router router = super.defineRoutes();
    router.route(HttpMethod.GET, "/oai/records/*").handler(this::oaiPmhHandler);
    router.route(HttpMethod.GET, "/oai/repository_info").handler(this::oaiPmhHandler);
    router.route(HttpMethod.GET, "/configurations/entries").handler(this::handleConfigurationModuleResponse);
    return router;
  }

  private void oaiPmhHandler(RoutingContext ctx) {

    HttpServerRequest request = ctx.request();
    String path = request.path();
    String accept = request.getHeader(HttpHeaders.ACCEPT);

    if(accept != null &&
        !accept.equals(MOD_OAI_PMH_ACCEPTED_TYPES)) {
      logger.debug("Unsupported MIME type requested: " + accept);
      ctx.response()
        .setStatusCode(400)
        .putHeader(HttpHeaders.CONTENT_TYPE, "text/plain")
        .end("Accept header must be [\"application/xml\",\"text/plain\"] for this request, but it is \"text/xml\", cannot send */*");
    } else if (path.startsWith("/oai/records/")
      && path.contains("oai%3AarXiv.org%3Acs%2F0112017")) {
      ctx.response()
        .setStatusCode(200)
        .putHeader(HttpHeaders.CONTENT_TYPE, Constants.TEXT_XML_TYPE)
        .end(getOaiPmhResponseAsXml(Paths.get(PATH_TO_GET_RECORDS_MOCK)));
    } else if (path.startsWith("/oai/records/")
      && path.contains("oai%3AarXiv.org%3Aquant-ph%2F02131001")) {
      ctx.response()
        .setStatusCode(404)
        .putHeader(HttpHeaders.CONTENT_TYPE, Constants.TEXT_XML_TYPE)
        .end(getOaiPmhResponseAsXml(Paths.get(PATH_TO_GET_RECORDS_ERROR_MOCK)));
    } else if (path.startsWith("/oai/repository_info")) {
      ctx.response()
        .setStatusCode(200)
        .putHeader(HttpHeaders.CONTENT_TYPE, Constants.TEXT_XML_TYPE)
        .end(getOaiPmhResponseAsXml(Paths.get(PATH_TO_IDENTIFY_MOCK)));
    }else if (request.absoluteURI().contains("resumptionToken")) {
      ctx.response()
        .setStatusCode(400)
        .putHeader(HttpHeaders.CONTENT_TYPE, Constants.TEXT_XML_TYPE)
        .end(getOaiPmhResponseAsXml(Paths.get(PATH_TO_GET_RECORDS_ERROR_MOCK)));
    }else if (path.startsWith("/oai/records/") && path.contains("oai%3AarXiv.org%3Atest-env%2F98765400")){
      ctx.response()
        .setStatusCode(422)
        .putHeader(HttpHeaders.CONTENT_TYPE, Constants.TEXT_XML_TYPE)
        .end(getOaiPmhResponseAsXml(Paths.get(PATH_TO_GET_RECORDS_ERROR_MOCK)));
    }else if (request.absoluteURI().contains("metadataPrefix") && request.absoluteURI().contains("from")) {
      ctx.response()
        .setStatusCode(404)
        .putHeader(HttpHeaders.CONTENT_TYPE, Constants.TEXT_XML_TYPE)
        .end(getOaiPmhResponseAsXml(Paths.get(PATH_TO_GET_RECORDS_ERROR_MOCK)));
    } else if (path.startsWith("/oai/records/")
      && path.contains("exception")) {
      logger.debug("Starting OKAPI exception...");
      throw new NullPointerException("NPE OKAPI mock emulation");
    } else if (path.contains("TimeoutException")) {
      vertx.setTimer(REQUEST_TIMEOUT_MS + 1L, event -> logger.debug("OKAPI client should throw TimeoutException"));
    }
  }

  private void handleConfigurationModuleResponse(RoutingContext ctx){
    if (ctx.request().absoluteURI().contains("behavior")) {
      if (modConfigurationErrorsProcessing.equals("200")) {
        ctx.response()
          .setStatusCode(200)
          .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
          .end(getJsonObjectFromFile(Paths.get(PATH_TO_ERROR_PROCESSING_CONFIG_SETTING_500)).replace("500", "200"));
      } else if (modConfigurationErrorsProcessing.equals("500")) {
        ctx.response()
          .setStatusCode(200)
          .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
          .end(getJsonObjectFromFile(Paths.get(PATH_TO_ERROR_PROCESSING_CONFIG_SETTING_500)));
      } else if (modConfigurationErrorsProcessing.equals("emptyBody")) {
        ctx.response()
          .setStatusCode(200)
          .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
          .end();
      } else {
        ctx.response()
          .setStatusCode(404)
          .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
          .end();
      }
    } else {
      if (modConfigurationEnableOaiService.equals("true")) {
        ctx.response()
          .setStatusCode(200)
          .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
          .end(getJsonObjectFromFile(Paths.get(PATH_TO_GENERAL_CONFIGS)));
      } else {
        ctx.response()
          .setStatusCode(200)
          .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
          .end(getJsonObjectFromFile(Paths.get(PATH_TO_GENERAL_CONFIGS)).replace("true", "false"));
      }
    }
  }

  private String getJsonObjectFromFile(Path path) {
    String json = null;
    try {
      json = new String(Files.readAllBytes(path));
    } catch (IOException e) {
      logger.error("Unexpected error", e);
    }
    return json;
  }

  public void setModConfigurationErrorsProcessingValue(String errorsProcessing) {
    this.modConfigurationErrorsProcessing = errorsProcessing;
  }

  public void setModConfigurationEnableOaiServiceValue(String enableOaiService) {
    this.modConfigurationEnableOaiService = enableOaiService;
  }
}




