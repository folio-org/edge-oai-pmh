package org.folio.edge.oaipmh.utils;

import static org.folio.edge.core.Constants.TEXT_PLAIN;
import static org.folio.edge.core.Constants.TEXT_XML;
import static org.folio.edge.oaipmh.utils.Constants.MOD_OAI_PMH_ACCEPTED_TYPES;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import lombok.SneakyThrows;
import org.folio.edge.core.utils.test.MockOkapi;

import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.junit5.VertxTestContext;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class OaiPmhMockOkapi extends MockOkapi {

  public static final String PATH_TO_GET_RECORDS_MOCK
    = "src/test/resources/mocks/GetRecordResponse.xml";
  public static final String PATH_TO_GET_RECORDS_ERROR_MOCK
    = "src/test/resources/mocks/GetRecordErrorResponse.xml";
  public static final String PATH_TO_IDENTIFY_MOCK
    = "src/test/resources/mocks/IdentifyResponse.xml";
  public static final String PATH_TO_LIST_RECORDS_EMPTY_MOCK
    = "src/test/resources/mocks/ListRecordsEmptyResponse.xml";
  public static final String PATH_TO_LIST_IDENTIFIERS_EMPTY_MOCK
    = "src/test/resources/mocks/ListRecordsEmptyResponse.xml";
  public static final String PATH_TO_LIST_RECORDS_NO_TOKEN_MOCK
    = "src/test/resources/mocks/ListRecordsNoTokenResponse.xml";
  public static final String PATH_TO_LIST_IDENTIFIERS_NO_TOKEN_MOCK
    = "src/test/resources/mocks/ListIdentifiersNoTokenResponse.xml";
  public static final String PATH_TO_LIST_RECORDS_ERROR_MOCK
    = "src/test/resources/mocks/ListRecordsErrorResponse.xml";
  public static final String PATH_TO_LIST_IDENTIFIERS_ERROR_MOCK
    = "src/test/resources/mocks/ListIdentifiersErrorResponse.xml";
  public static final String PATH_TO_LIST_RECORDS_MOCK
    = "src/test/resources/mocks/ListRecordsResponse.xml";
  public static final String PATH_TO_LIST_IDENTIFIERS_MOCK
    = "src/test/resources/mocks/ListIdentifiersResponse.xml";
  public static final String PATH_TO_LIST_RECORDS_CONSORTIA_MOCK
    = "src/test/resources/mocks/ListRecordsConsortiaResponse.xml";
  public static final String PATH_TO_LIST_RECORDS_CONSORTIA_MOCK2
    = "src/test/resources/mocks/ListRecordsConsortiaResponse2.xml";
  public static final String PATH_TO_LIST_RECORDS_CONSORTIA_MOCK3
    = "src/test/resources/mocks/ListRecordsConsortiaResponse3.xml";
  public static final String PATH_TO_LIST_RECORDS_WITH_TOKEN_MOCK
    = "src/test/resources/mocks/ListRecordsWithTokenResponse.xml";
  public static final String PATH_TO_LIST_IDENTIFIERS_WITH_TOKEN_MOCK
    = "src/test/resources/mocks/ListIdentifiersWithTokenResponse.xml";
  public static final String PATH_TO_LIST_RECORDS_WITH_NEW_TOKEN_MOCK
    = "src/test/resources/mocks/ListRecordsWithNewTokenResponse.xml";
  public static final String PATH_TO_LIST_IDENTIFIERS_WITH_NEW_TOKEN_MOCK
    = "src/test/resources/mocks/ListIdentifiersWithNewTokenResponse.xml";
  public static final String PATH_TO_LIST_RECORDS_TOKEN_WITH_DATES_MOCK
    = "src/test/resources/mocks/ListRecordsTokenWithDatesResponse.xml";
  public static final String PATH_TO_LIST_IDENTIFIERS_TOKEN_WITH_DATES_MOCK
    = "src/test/resources/mocks/ListIdentifiersTokenWithDatesResponse.xml";
  public static final String PATH_TO_EMPTY_USER_TENANTS_MOCK
    = "src/test/resources/mocks/emptyUserTenantsCollection.json";
  public static final String PATH_TO_EMPTY_CONSORTIA_USER_TENANTS_MOCK
    = "src/test/resources/mocks/userTenantsCollectionForEmptyConsortia.json";
  public static final String PATH_TO_USER_TENANTS_MOCK
    = "src/test/resources/mocks/userTenantsCollection.json";
  public static final String PATH_TO_USER_TENANTS_MOCK2
    = "src/test/resources/mocks/userTenantsCollection2.json";
  public static final String PATH_TO_CONSORTIUM_COLLECTION_MOCK
    = "src/test/resources/mocks/consortiumCollection.json";
  public static final String PATH_TO_CONSORTIUM_COLLECTION_MOCK2
    = "src/test/resources/mocks/consortiumCollection2.json";
  public static final String PATH_TO_EMPTY_CONSORTIUM_COLLECTION_MOCK
    = "src/test/resources/mocks/emptyConsortiumCollection.json";
  public static final String PATH_TO_CONSORTIA_TENANTS_MOCK
    = "src/test/resources/mocks/consortiaTenants.json";
  public static final String PATH_TO_CONSORTIA_TENANTS_MOCK2
    = "src/test/resources/mocks/consortiaTenants2.json";
  private static final String GET_RECORD = "GetRecord";
  private static final String IDENTIFY = "Identify";
  private static final String LIST_RECORDS = "ListRecords";
  private static final String LIST_IDENTIFIERS = "ListIdentifiers";

  private static final String ERROR_MSG_FORBIDDEN = "Access requires permission: oai-pmh.records.collection.get";

  public static final long REQUEST_TIMEOUT_MS = 1000L;
  private static final String FORBIDDEN_STATUS_MESSAGE = "Forbidden";

  private final Vertx vertx;

  public OaiPmhMockOkapi(Vertx vertx, int port, List<String> knownTenants) {
    super(port, knownTenants);
    this.vertx = vertx;
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

  public void start(VertxTestContext context) {
    // Setup Mock Okapi and enable compression
    HttpServer server = vertx.createHttpServer(new HttpServerOptions()
      .setCompressionSupported(true));

    server.requestHandler(defineRoutes())
      .listen(okapiPort, context.succeeding(result -> {
        log.info("The server has started.");
        context.completeNow();
      }));
  }

  @Override
  public Router defineRoutes() {
    Router router = super.defineRoutes();
    router.route(HttpMethod.GET, "/oai/records*").handler(this::oaiPmhHandler);
    router.route(HttpMethod.GET, "/user-tenants").handler(this::userTenantsHandler);
    router.route(HttpMethod.GET, "/consortia").handler(this::consortiaHandler);
    router.route(HttpMethod.GET, "/consortia/:id/tenants").handler(this::consortiaTenantsHandler);
    return router;
  }

  private void oaiPmhHandler(RoutingContext ctx) {

    HttpServerRequest request = ctx.request();
    MultiMap requestParams = request.params();
    String path = request.path();
    String accept = request.getHeader(HttpHeaders.ACCEPT);

    if (accept != null &&
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
        .putHeader(HttpHeaders.CONTENT_TYPE, TEXT_XML)
        .end(getOaiPmhResponseAsXml(Paths.get(PATH_TO_GET_RECORDS_MOCK)));
    } else if (paramsContainVerbWithName(requestParams, GET_RECORD)
      && paramsContainParamWithValue(requestParams, "oai:arXiv.org:quant-ph/02131001")) {
      ctx.response()
        .setStatusCode(404)
        .putHeader(HttpHeaders.CONTENT_TYPE, TEXT_XML)
        .end(getOaiPmhResponseAsXml(Paths.get(PATH_TO_GET_RECORDS_ERROR_MOCK)));
    } else if (paramsContainVerbWithName(requestParams, IDENTIFY)) {
      ctx.response()
        .setStatusCode(200)
        .putHeader(HttpHeaders.CONTENT_TYPE, TEXT_XML)
        .end(getOaiPmhResponseAsXml(Paths.get(PATH_TO_IDENTIFY_MOCK)));
    } else if (paramsContainVerbWithName(requestParams, GET_RECORD)
      && paramsContainParamWithValue(requestParams, "exception")) {
      ctx.response()
        .putHeader(HttpHeaders.CONTENT_TYPE, TEXT_PLAIN);
      log.debug("Starting OKAPI exception...");
      throw new NullPointerException("NPE OKAPI mock emulation");
    } else if (paramsContainVerbWithName(requestParams, LIST_RECORDS)) {
      var tenantId = ctx.request().getHeader("x-okapi-tenant");
      if (paramsContainParamWithValue(requestParams, "bWV0YXRkYXRhUHJlZml4PW9haV9kYyZ0ZW5hbnRJZD1kaWt1")
        || tenantId.equals("tenant4")
        || (tenantId.equals("tenant2") && paramsContainParamWithValue(requestParams, "bWV0YWRhdGFQcmVmaXg9b2FpX2RjJnRlbmFudElkPXRlbmFudDImcGFyYW09cGFyYW0"))) {
        ctx.response()
          .setStatusCode(200)
          .putHeader(HttpHeaders.CONTENT_TYPE, TEXT_XML)
          .end(getOaiPmhResponseAsXml(Paths.get(PATH_TO_LIST_RECORDS_MOCK)));
      } else if (tenantId.equals("tenant1")) {
        ctx.response()
          .setStatusCode(200)
          .putHeader(HttpHeaders.CONTENT_TYPE, TEXT_XML)
          .end(getOaiPmhResponseAsXml(Paths.get(PATH_TO_LIST_RECORDS_CONSORTIA_MOCK)));
      } else if (tenantId.equals("tenant2")) {
        ctx.response()
          .setStatusCode(200)
          .putHeader(HttpHeaders.CONTENT_TYPE, TEXT_XML)
          .end(getOaiPmhResponseAsXml(Paths.get(PATH_TO_LIST_RECORDS_CONSORTIA_MOCK2)));
      } else if (tenantId.equals("tenant3")) {
        ctx.response()
          .setStatusCode(200)
          .putHeader(HttpHeaders.CONTENT_TYPE, TEXT_XML)
          .end(getOaiPmhResponseAsXml(Paths.get(PATH_TO_LIST_RECORDS_CONSORTIA_MOCK3)));
      } else if (tenantId.equals("tenant5")) {
        if (paramsContainParamWithValue(requestParams, "2023-08-30")) {
          ctx.response()
            .setStatusCode(200)
            .putHeader(HttpHeaders.CONTENT_TYPE, TEXT_XML)
            .end(getOaiPmhResponseAsXml(Paths.get(PATH_TO_LIST_RECORDS_ERROR_MOCK)));
        } else {
          ctx.response()
            .setStatusCode(200)
            .putHeader(HttpHeaders.CONTENT_TYPE, TEXT_XML)
            .end(getOaiPmhResponseAsXml(Paths.get(PATH_TO_LIST_RECORDS_NO_TOKEN_MOCK)));
        }
      } else if (tenantId.equals("tenant6") && paramsContainParamWithValue(requestParams, "2023-08-30")) {
        ctx.response()
          .setStatusCode(200)
          .putHeader(HttpHeaders.CONTENT_TYPE, TEXT_XML)
          .end(getOaiPmhResponseAsXml(Paths.get(PATH_TO_LIST_RECORDS_TOKEN_WITH_DATES_MOCK)));
      } else {
        ctx.response()
          .setStatusCode(200)
          .putHeader(HttpHeaders.CONTENT_TYPE, TEXT_XML)
          .end(getOaiPmhResponseAsXml(Paths.get(PATH_TO_LIST_RECORDS_EMPTY_MOCK)));
      }
    } else if (paramsContainVerbWithName(requestParams, LIST_IDENTIFIERS)) {
      var tenantId = ctx.request().getHeader("x-okapi-tenant");
      if (tenantId.equals("tenant5")) {
        if (paramsContainParamWithValue(requestParams, "2023-08-30")) {
          ctx.response()
            .setStatusCode(200)
            .putHeader(HttpHeaders.CONTENT_TYPE, TEXT_XML)
            .end(getOaiPmhResponseAsXml(Paths.get(PATH_TO_LIST_IDENTIFIERS_ERROR_MOCK)));
        } else {
          ctx.response()
            .setStatusCode(200)
            .putHeader(HttpHeaders.CONTENT_TYPE, TEXT_XML)
            .end(getOaiPmhResponseAsXml(Paths.get(PATH_TO_LIST_IDENTIFIERS_NO_TOKEN_MOCK)));
        }
      } else if (tenantId.equals("tenant6") && paramsContainParamWithValue(requestParams, "2023-08-30")) {
        ctx.response()
          .setStatusCode(200)
          .putHeader(HttpHeaders.CONTENT_TYPE, TEXT_XML)
          .end(getOaiPmhResponseAsXml(Paths.get(PATH_TO_LIST_IDENTIFIERS_TOKEN_WITH_DATES_MOCK)));
      } else if (tenantId.equals("tenant4")) {
        ctx.response()
          .setStatusCode(200)
          .putHeader(HttpHeaders.CONTENT_TYPE, TEXT_XML)
          .end(getOaiPmhResponseAsXml(Paths.get(PATH_TO_LIST_IDENTIFIERS_MOCK)));
      }
    } else if (path.contains("TimeoutException")) {
      vertx.setTimer(REQUEST_TIMEOUT_MS + 1L, event -> log.debug("OKAPI client should throw TimeoutException"));
    } else if (paramsContainVerbWithName(requestParams, GET_RECORD) && paramsContainParamWithValue(requestParams, "recordIdForbiddenResponse")) {
      ctx.response()
        .setStatusCode(403)
        .setStatusMessage(FORBIDDEN_STATUS_MESSAGE)
        .putHeader(HttpHeaders.CONTENT_TYPE, TEXT_PLAIN)
        .end(ERROR_MSG_FORBIDDEN);
    }
  }

  private static boolean paramsContainVerbWithName(MultiMap requestParams, String verbName) {
    return requestParams.get(Constants.VERB).equals(verbName);
  }

  private static boolean paramsContainParamWithValue(MultiMap requestParams, String value) {
    return requestParams.entries().stream()
      .anyMatch(entry -> entry.getValue().equals(value));
  }

  @SneakyThrows
  private void userTenantsHandler(RoutingContext ctx) {
    var tenantId = ctx.request().getHeader("x-okapi-tenant");
    if ("central".equals(tenantId)) {
      ctx.response()
        .setStatusCode(200)
        .end(new String(Files.readAllBytes(Path.of(PATH_TO_USER_TENANTS_MOCK))));
    } else if ("central2".equals(tenantId)) {
      ctx.response()
        .setStatusCode(200)
        .end(new String(Files.readAllBytes(Path.of(PATH_TO_USER_TENANTS_MOCK2))));
    } else if ("empty_consortia".equals(tenantId)) {
      ctx.response()
        .setStatusCode(200)
        .end(new String(Files.readAllBytes(Path.of(PATH_TO_EMPTY_CONSORTIA_USER_TENANTS_MOCK))));
    } else {
      ctx.response()
        .setStatusCode(200)
        .end(new String(Files.readAllBytes(Path.of(PATH_TO_EMPTY_USER_TENANTS_MOCK))));
    }
  }

  @SneakyThrows
  private void consortiaHandler(RoutingContext ctx) {
    var tenantId = ctx.request().getHeader("x-okapi-tenant");
    if ("central".equals(tenantId)) {
      ctx.response()
        .setStatusCode(200)
        .end(new String(Files.readAllBytes(Path.of(PATH_TO_CONSORTIUM_COLLECTION_MOCK))));
    } else if ("central2".equals(tenantId)) {
      ctx.response()
        .setStatusCode(200)
        .end(new String(Files.readAllBytes(Path.of(PATH_TO_CONSORTIUM_COLLECTION_MOCK2))));
    } else {
      ctx.response()
        .setStatusCode(200)
        .end(new String(Files.readAllBytes(Path.of(PATH_TO_EMPTY_CONSORTIUM_COLLECTION_MOCK))));
    }
  }

  @SneakyThrows
  private void consortiaTenantsHandler(RoutingContext ctx) {
    var tenantId = ctx.request().getHeader("x-okapi-tenant");
    if ("central".equals(tenantId)) {
      ctx.response()
        .setStatusCode(200)
        .end(new String(Files.readAllBytes(Path.of(PATH_TO_CONSORTIA_TENANTS_MOCK))));
    } else {
      ctx.response()
        .setStatusCode(200)
        .end(new String(Files.readAllBytes(Path.of(PATH_TO_CONSORTIA_TENANTS_MOCK2))));
    }
  }
}
