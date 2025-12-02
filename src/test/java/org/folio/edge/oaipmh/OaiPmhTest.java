package org.folio.edge.oaipmh;

import static io.restassured.config.DecoderConfig.decoderConfig;
import static org.folio.edge.core.Constants.SYS_LOG_LEVEL;
import static org.folio.edge.core.Constants.SYS_OKAPI_URL;
import static org.folio.edge.core.Constants.SYS_PORT;
import static org.folio.edge.core.Constants.SYS_REQUEST_TIMEOUT_MS;
import static org.folio.edge.core.Constants.SYS_RESPONSE_COMPRESSION;
import static org.folio.edge.core.Constants.SYS_SECURE_STORE_PROP_FILE;
import static org.folio.edge.core.Constants.TEXT_PLAIN;
import static org.folio.edge.core.Constants.TEXT_XML;
import static org.folio.edge.oaipmh.utils.OaiPmhMockOkapi.REQUEST_TIMEOUT_MS;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.spy;

import io.restassured.RestAssured;
import io.restassured.config.DecoderConfig;
import io.restassured.http.Header;
import io.restassured.response.Response;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.folio.edge.core.utils.ApiKeyUtils;
import org.folio.edge.core.utils.test.TestUtils;
import org.folio.edge.oaipmh.utils.OaiPmhMockOkapi;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

@Slf4j
@ExtendWith(VertxExtension.class)
class OaiPmhTest {

  private static final String API_KEY = ApiKeyUtils.generateApiKey(10, "diku",
        "user");
  private static final String ILLEGAL_API_KEY = "eyJzIjoiYmJaUnYyamt2ayIsInQiOiJkaWt1IiwidSI6"
        + "ImRpa3VfYSJ9";
  private static final String BAD_API_KEY = "ZnMwMDAwMDAwMA==0000";

  private static final String INVALID_API_KEY_EXPECTED_RESPONSE_BODY = "Invalid API Key: "
        + "ZnMwMDAwMDAwMA==0000";
  private static final String EXPECTED_ERROR_FORBIDDEN_MSG = "Error in the response from "
        + "repository: status code - 403, response status message - Forbidden Access requires "
        + "permission: oai-pmh.records.collection.get";
  private static final String EXPECTED_ERROR_INTERNAL_SERVER_ERROR_MSG = "Error in the response "
        + "from repository: status code - 500, response status message - Internal Server Error";

  private static OaiPmhMockOkapi mockOkapi;

  @BeforeAll
  static void setUpOnce(Vertx vertx, VertxTestContext context) throws Exception {
    int serverPort = TestUtils.getPort();

    RestAssured.baseURI = "http://localhost:" + serverPort;
    RestAssured.port = serverPort;
    RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();

    List<String> knownTenants = new ArrayList<>();
    knownTenants.add(ApiKeyUtils.parseApiKey(API_KEY).tenantId);
    knownTenants.add("central");
    knownTenants.add("central2");
    knownTenants.add("tenant1");
    knownTenants.add("tenant2");
    knownTenants.add("tenant3");
    knownTenants.add("tenant4");
    knownTenants.add("tenant5");
    knownTenants.add("tenant6");

    int okapiPort = TestUtils.getPort();

    System.setProperty(SYS_PORT, String.valueOf(serverPort));
    System.setProperty(SYS_OKAPI_URL, "http://localhost:" + okapiPort);
    System.setProperty(SYS_SECURE_STORE_PROP_FILE, "src/main/resources/ephemeral.properties");
    System.setProperty(SYS_LOG_LEVEL, "TRACE");
    System.setProperty(SYS_REQUEST_TIMEOUT_MS, String.valueOf(REQUEST_TIMEOUT_MS));
    System.setProperty(SYS_RESPONSE_COMPRESSION, Boolean.toString(true));

    final DeploymentOptions opt = new DeploymentOptions();
    vertx
      .deployVerticle(MainVerticle.class.getName(), opt)
      .onSuccess(id -> {
        mockOkapi = spy(new OaiPmhMockOkapi(vertx, okapiPort, knownTenants));
        mockOkapi.start(context);
      })
      .onFailure(context::failNow);
  }

  @AfterAll
  static void tearDownOnce(Vertx vertx, VertxTestContext context) {
    log.info("Shutting down server");
    vertx.close()
        .onSuccess(res -> {
          log.info("Successfully shut down edge-oai-pmh server");
          context.completeNow();
        })
        .onFailure(err -> {
          log.error("Failed to shut down edge-oai-pmh server", err.getMessage());
          context.failNow(err.getMessage());
        });
  }

  @Test
  void testAdminHealth() {
    log.info("=== Test the health check endpoint ===");

    final Response resp = RestAssured
          .get("/admin/health")
          .then()
          .contentType(TEXT_PLAIN)
          .statusCode(HttpStatus.SC_OK)
          .header(HttpHeaders.CONTENT_TYPE, TEXT_PLAIN)
          .extract()
          .response();

    assertEquals("\"OK\"", resp.body().asString());
  }

  @Test
  void testGetRecordNotFoundHttpGet() {
    log.info("=== Test GetRecord OAI-PMH error - not found (HTTP GET)===");

    Path expectedMockPath = Paths.get(OaiPmhMockOkapi.PATH_TO_GET_RECORDS_ERROR_MOCK);
    String expectedMockBody = OaiPmhMockOkapi.getOaiPmhResponseAsXml(expectedMockPath);

    final Response resp = RestAssured
          .get(String.format("/oai?verb=GetRecord&identifier=oai:arXiv.org:quant-ph/"
                + "02131001&metadataPrefix=oai_dc&apikey=%s", API_KEY));
    resp.then()
          .contentType(TEXT_XML)
          .statusCode(HttpStatus.SC_NOT_FOUND)
          .header(HttpHeaders.CONTENT_TYPE, TEXT_XML)
          .extract()
          .response();

    String actualBody = resp.body().asString();
    assertEquals(expectedMockBody, actualBody);
  }

  @Test
  void testGetRecordNotFoundHttpPost() {
    log.info("=== Test GetRecord OAI-PMH error - not found (HTTP POST)===");

    Path expectedMockPath = Paths.get(OaiPmhMockOkapi.PATH_TO_GET_RECORDS_ERROR_MOCK);
    String expectedMockBody = OaiPmhMockOkapi.getOaiPmhResponseAsXml(expectedMockPath);

    final Response resp = RestAssured.given()
          .headers(HttpHeaders.CONTENT_TYPE, TEXT_PLAIN)
          .post(String.format("/oai?verb=GetRecord"
                + "&identifier=oai:arXiv.org:quant-ph/02131001&metadataPrefix=oai_dc&apikey=%s",
                API_KEY))
          .then()
          .contentType(TEXT_XML)
          .statusCode(HttpStatus.SC_NOT_FOUND)
          .header(HttpHeaders.CONTENT_TYPE, TEXT_XML)
          .extract()
          .response();

    String actualBody = resp.body().asString();
    assertEquals(expectedMockBody, actualBody);
  }

  @Test
  void testGetRecordSuccessfulHttpGet() {
    log.info("=== Test successful GetRecord OAI-PMH (HTTP GET) ===");

    Path expectedMockPath = Paths.get(OaiPmhMockOkapi.PATH_TO_GET_RECORDS_MOCK);
    String expectedMockBody = OaiPmhMockOkapi.getOaiPmhResponseAsXml(expectedMockPath);

    final Response resp = RestAssured
          .get(String.format("/oai?verb=GetRecord"
                + "&identifier=oai:arXiv.org:cs/0112017&metadataPrefix=oai_dc&apikey=%s", API_KEY))
          .then()
          .contentType(TEXT_XML)
          .statusCode(HttpStatus.SC_OK)
          .header(HttpHeaders.CONTENT_TYPE, TEXT_XML)
          .extract()
          .response();

    String actualBody = resp.body().asString();
    assertEquals(expectedMockBody, actualBody);
  }

  @Test
  void testGetRecordSuccessfulHttpPost() {
    log.info("=== Test successful GetRecord OAI-PMH (HTTP POST) ===");

    Path expectedMockPath = Paths.get(OaiPmhMockOkapi.PATH_TO_GET_RECORDS_MOCK);
    String expectedMockBody = OaiPmhMockOkapi.getOaiPmhResponseAsXml(expectedMockPath);

    final Response resp = RestAssured.given()
          .headers(HttpHeaders.CONTENT_TYPE, TEXT_PLAIN)
          .post(String.format("/oai?verb=GetRecord"
                + "&identifier=oai:arXiv.org:cs/0112017&metadataPrefix=oai_dc&apikey=%s", API_KEY))
          .then()
          .contentType(TEXT_XML)
          .statusCode(HttpStatus.SC_OK)
          .header(HttpHeaders.CONTENT_TYPE, TEXT_XML)
          .extract()
          .response();

    String actualBody = resp.body().asString();
    assertEquals(expectedMockBody, actualBody);
  }

  @Test
  void testIdentifySuccessfulHttpGet() {
    log.info("=== Test successful Identify OAI-PMH (HTTP GET) ===");

    Path expectedMockPath = Paths.get(OaiPmhMockOkapi.PATH_TO_IDENTIFY_MOCK);
    String expectedMockBody = OaiPmhMockOkapi.getOaiPmhResponseAsXml(expectedMockPath);

    final Response resp = RestAssured
          .get(String.format("/oai?verb=Identify&apikey=%s", API_KEY))
          .then()
          .contentType(TEXT_XML)
          .statusCode(HttpStatus.SC_OK)
          .header(HttpHeaders.CONTENT_TYPE, TEXT_XML)
          .extract()
          .response();

    String actualBody = resp.body().asString();
    assertEquals(expectedMockBody, actualBody);
  }

  @Test
  void testIdentifySuccessfulHttpPost() {
    log.info("=== Test successful Identify OAI-PMH (HTTP POST) ===");

    Path expectedMockPath = Paths.get(OaiPmhMockOkapi.PATH_TO_IDENTIFY_MOCK);
    String expectedMockBody = OaiPmhMockOkapi.getOaiPmhResponseAsXml(expectedMockPath);

    final Response resp = RestAssured.given()
          .headers(HttpHeaders.CONTENT_TYPE, TEXT_PLAIN)
          .post(String.format("/oai?verb=Identify&apikey=%s", API_KEY))
          .then()
          .contentType(TEXT_XML)
          .statusCode(HttpStatus.SC_OK)
          .header(HttpHeaders.CONTENT_TYPE, TEXT_XML)
          .extract()
          .response();

    String actualBody = resp.body().asString();
    assertEquals(expectedMockBody, actualBody);
  }

  @Test
  void testIdentifyBadApiKeyHttpGet() {
    log.info("=== Test bad apikey Identify OAI-PMH (HTTP GET) ===");

    final Response resp = RestAssured
          .get(String.format("/oai?verb=Identify&apikey=%s", BAD_API_KEY))
          .then()
          .contentType(TEXT_PLAIN)
          .statusCode(HttpStatus.SC_UNAUTHORIZED)
          .header(HttpHeaders.CONTENT_TYPE, TEXT_PLAIN)
          .extract()
          .response();

    String actualBody = resp.body().asString();
    assertEquals(INVALID_API_KEY_EXPECTED_RESPONSE_BODY, actualBody);
  }

  @Test
  void testApiKeyOnPath() {
    log.info("=== Test ability to provide the api key on the path ===");

    Path expectedMockPath = Paths.get(OaiPmhMockOkapi.PATH_TO_IDENTIFY_MOCK);
    String expectedMockBody = OaiPmhMockOkapi.getOaiPmhResponseAsXml(expectedMockPath);

    final Response resp = RestAssured
          .get(String.format("/oai/%s?verb=Identify", API_KEY))
          .then()
          .contentType(TEXT_XML)
          .statusCode(HttpStatus.SC_OK)
          .header(HttpHeaders.CONTENT_TYPE, TEXT_XML)
          .extract()
          .response();

    String actualBody = resp.body().asString();
    assertEquals(expectedMockBody, actualBody);
  }

  @Test
  void testIdentifyAccessDeniedApiKeyHttpGet() {
    log.info("=== Test Access Denied apikey Identify OAI-PMH (HTTP POST) ===");

    final Response resp = RestAssured
          .post(String.format("/oai?verb=Identify&apikey=%s", ILLEGAL_API_KEY))
          .then()
          .contentType(TEXT_PLAIN)
          .statusCode(HttpStatus.SC_FORBIDDEN)
          .header(HttpHeaders.CONTENT_TYPE, TEXT_PLAIN)
          .extract()
          .response();

    String actualBody = resp.body().asString();
    assertNotNull(actualBody);
  }

  @Test
  void testGetRecordInvalidApiKeyHttpPost() {
    log.info("=== Test bad apikey GetRecord OAI-PMH (HTTP POST) ===");

    final Response resp = RestAssured.given()
          .params("apikey", BAD_API_KEY,
                "verb", "GetRecord",
                "metadataPrefix", "oai_dc",
                "identifier", "oai:arXiv.org:cs/0112017")
          .post("/oai")
          .then()
          .contentType(TEXT_PLAIN)
          .statusCode(HttpStatus.SC_UNAUTHORIZED)
          .header(HttpHeaders.CONTENT_TYPE, TEXT_PLAIN)
          .extract()
          .response();

    String actualBody = resp.body().asString();
    assertEquals(INVALID_API_KEY_EXPECTED_RESPONSE_BODY, actualBody);
  }

  @Test
  void testGetRecordOkapiExceptionHttpGet() {
    log.info("=== Test exceptional GetRecord OAI-PMH (HTTP GET) ===");

    RestAssured
          .get(String.format("/oai?verb=GetRecord"
                + "&identifier=exception&metadataPrefix=oai_dc&apikey=%s", API_KEY))
          .then()
          .contentType(TEXT_PLAIN)
          .statusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR)
          .header(HttpHeaders.CONTENT_TYPE, TEXT_PLAIN)
          .body(containsString(EXPECTED_ERROR_INTERNAL_SERVER_ERROR_MSG));
  }

  @Test
  void testGetRecordOkapiTimeoutExceptionHttpGet() {
    log.info("=== Test timeout GetRecord OAI-PMH (HTTP GET) ===");

    final Response resp = RestAssured
          .get(String.format("/oai?verb=GetRecord"
                + "&identifier=TimeoutException&metadataPrefix=oai_dc&apikey=%s", API_KEY))
          .then()
          .contentType(TEXT_PLAIN)
          .statusCode(HttpStatus.SC_REQUEST_TIMEOUT)
          .header(HttpHeaders.CONTENT_TYPE, TEXT_PLAIN)
          .extract()
          .response();

    String actualBody = resp.body().asString();
    assertNotNull(actualBody);
  }

  @Test
  void testCompressionAlgorithms() {
    log.info("=== Test response compression ===");

    Path expectedMockPath = Paths.get(OaiPmhMockOkapi.PATH_TO_GET_RECORDS_MOCK);
    String expectedMockBody = OaiPmhMockOkapi.getOaiPmhResponseAsXml(expectedMockPath);

    for (DecoderConfig.ContentDecoder type : DecoderConfig.ContentDecoder.values()) {
      final Response resp = RestAssured.given()
            .config(RestAssured.config().decoderConfig(decoderConfig().contentDecoders(type)))
            .get(String.format("/oai?verb=GetRecord&identifier=oai:arXiv.org:cs/0112017&"
                  + "metadataPrefix=oai_dc&apikey=%s", API_KEY))
            .then()
            .contentType(TEXT_XML)
            .statusCode(HttpStatus.SC_OK)
            .header(HttpHeaders.CONTENT_TYPE, TEXT_XML)
            .header(HttpHeaders.CONTENT_ENCODING, type.name().toLowerCase())
            .extract()
            .response();
      assertEquals(expectedMockBody, resp.body().asString());
    }
  }

  @Test
  void testNoCompression() {
    log.info("=== Test no response compression  ===");

    Path expectedMockPath = Paths.get(OaiPmhMockOkapi.PATH_TO_GET_RECORDS_MOCK);
    String expectedMockBody = OaiPmhMockOkapi.getOaiPmhResponseAsXml(expectedMockPath);
    final Response resp = RestAssured.given()
          .config(RestAssured.config().decoderConfig(decoderConfig().noContentDecoders()))
          .header(HttpHeaders.ACCEPT_ENCODING, "instance")
          .get(String.format("/oai?verb=GetRecord&identifier=oai:arXiv.org:cs/0112017"
                + "&metadataPrefix=oai_dc&apikey=%s", API_KEY))
          .then()
          .contentType(TEXT_XML)
          .statusCode(HttpStatus.SC_OK)
          .header(HttpHeaders.CONTENT_TYPE, TEXT_XML)
          .extract()
          .response();

    assertFalse(resp.headers().asList().stream()
          .collect(Collectors.toMap(Header::getName, Header::getValue)).containsKey(
                HttpHeaders.ACCEPT_ENCODING));

    String actualBody = resp.body().asString();
    assertEquals(expectedMockBody, actualBody);
  }

  @Test
  void testAcceptHeader() {
    log.info("=== Test handling of the Accept header ===");

    Path expectedMockPath = Paths.get(OaiPmhMockOkapi.PATH_TO_GET_RECORDS_MOCK);
    String expectedMockBody = OaiPmhMockOkapi.getOaiPmhResponseAsXml(expectedMockPath);

    final Response resp = RestAssured
          .given()
          .header(HttpHeaders.ACCEPT, TEXT_XML)
          .get(String.format("/oai?verb=GetRecord"
                + "&identifier=oai:arXiv.org:cs/0112017&metadataPrefix=oai_dc&apikey=%s", API_KEY))
          .then()
          .log().all()
          .contentType(TEXT_XML)
          .statusCode(HttpStatus.SC_OK)
          .header(HttpHeaders.CONTENT_TYPE, TEXT_XML)
          .extract()
          .response();

    String actualBody = resp.body().asString();
    assertEquals(expectedMockBody, actualBody);
  }

  @Test
  void testNoAcceptHeader() {
    log.info("=== Test handling of the Accept header ===");

    Path expectedMockPath = Paths.get(OaiPmhMockOkapi.PATH_TO_GET_RECORDS_MOCK);
    String expectedMockBody = OaiPmhMockOkapi.getOaiPmhResponseAsXml(expectedMockPath);

    final Response resp = RestAssured
          .get(String.format("/oai?verb=GetRecord"
                + "&identifier=oai:arXiv.org:cs/0112017&metadataPrefix=oai_dc&apikey=%s", API_KEY))
          .then()
          .log().all()
          .contentType(TEXT_XML)
          .statusCode(HttpStatus.SC_OK)
          .header(HttpHeaders.CONTENT_TYPE, TEXT_XML)
          .extract()
          .response();

    String actualBody = resp.body().asString();
    assertEquals(expectedMockBody, actualBody);
  }

  @Test
  void testAcceptHeaderHasUnsupportedType() {
    log.info("=== Test handling of unsupported type in Accept header ===");

    String unsupportedAcceptType = "application/json";

    final Response resp = RestAssured
          .given()
          .header(HttpHeaders.ACCEPT, unsupportedAcceptType)
          .get(String.format("/oai?verb=GetRecord"
                + "&identifier=oai:arXiv.org:cs/0112017&metadataPrefix=oai_dc&apikey=%s", API_KEY))
          .then()
          .log().all()
          .contentType(TEXT_XML)
          .statusCode(HttpStatus.SC_NOT_ACCEPTABLE)
          .header(HttpHeaders.CONTENT_TYPE, TEXT_XML)
          .extract()
          .response();

    String actualBody = resp.body().asString();
    String expectedBody = "Accept header must be \"text/xml\" for this request, but it is " + "\""
          + unsupportedAcceptType
          + "\"" + ", can not send */*";
    assertEquals(expectedBody, actualBody);
  }

  @Test
  void testAcceptHeaderIsAbsent() {
    log.info("=== Test Accept header is absent ===");

    Path expectedMockPath = Paths.get(OaiPmhMockOkapi.PATH_TO_GET_RECORDS_MOCK);
    String expectedMockBody = OaiPmhMockOkapi.getOaiPmhResponseAsXml(expectedMockPath);

    final Response resp = RestAssured
          .given()
          .header(HttpHeaders.ACCEPT_CHARSET, "utf-8")
          .get(String.format("/oai?verb=GetRecord"
                + "&identifier=oai:arXiv.org:cs/0112017&metadataPrefix=oai_dc&apikey=%s", API_KEY))
          .then()
          .log().all()
          .contentType(TEXT_XML)
          .statusCode(HttpStatus.SC_OK)
          .header(HttpHeaders.CONTENT_TYPE, TEXT_XML)
          .extract()
          .response();

    String actualBody = resp.body().asString();
    assertEquals(expectedMockBody, actualBody);
  }

  @Test
  void testAcceptHeaderIsEmpty() {
    log.info("=== Test Accept header is empty ===");

    Path expectedMockPath = Paths.get(OaiPmhMockOkapi.PATH_TO_GET_RECORDS_MOCK);
    String expectedMockBody = OaiPmhMockOkapi.getOaiPmhResponseAsXml(expectedMockPath);

    final Response resp = RestAssured
          .given()
          .header(HttpHeaders.ACCEPT, "*/*")
          .get(String.format("/oai?verb=GetRecord"
                + "&identifier=oai:arXiv.org:cs/0112017&metadataPrefix=oai_dc&apikey=%s", API_KEY))
          .then()
          .log().all()
          .contentType(TEXT_XML)
          .statusCode(HttpStatus.SC_OK)
          .header(HttpHeaders.CONTENT_TYPE, TEXT_XML)
          .extract()
          .response();

    String actualBody = resp.body().asString();
    assertEquals(expectedMockBody, actualBody);
  }

  @ParameterizedTest
  @ValueSource(strings =
        {
    "text/*",
    "text/*; q=0.2, application/xml, application/xhtml+xml",
    "text/html, application/xml, text/xml",
    "text/html, text/html;level=1, */*",
    "text/*;q=0.3, text/html;level=1, */*;q=0.5"
        })
  void testAcceptHeadersHasDifferentTypos(String header) {
    Path expectedMockPath = Paths.get(OaiPmhMockOkapi.PATH_TO_GET_RECORDS_MOCK);
    String expectedMockBody = OaiPmhMockOkapi.getOaiPmhResponseAsXml(expectedMockPath);

    final Response resp = RestAssured
          .given()
          .header(HttpHeaders.ACCEPT, header)
          .get(String.format("/oai?verb=GetRecord"
                + "&identifier=oai:arXiv.org:cs/0112017&metadataPrefix=oai_dc&apikey=%s", API_KEY))
          .then()
          .log().all()
          .contentType(TEXT_XML)
          .statusCode(HttpStatus.SC_OK)
          .header(HttpHeaders.CONTENT_TYPE, TEXT_XML)
          .extract()
          .response();

    String actualBody = resp.body().asString();
    assertEquals(expectedMockBody, actualBody);
  }

  @Test
  void testAcceptHeaderHasOnlyUnsupportedTypesWithParameter() {
    log.info("=== Accept header has only unsupported types with parameter ===");

    String acceptHeader = "text/plain; q=0.5, text/html";

    final Response resp = RestAssured
          .given()
          .header(HttpHeaders.ACCEPT, acceptHeader)
          .get(String.format("/oai?verb=GetRecord"
                + "&identifier=oai:arXiv.org:cs/0112017&metadataPrefix=oai_dc&apikey=%s", API_KEY))
          .then()
          .log().all()
          .contentType(TEXT_XML)
          .statusCode(HttpStatus.SC_NOT_ACCEPTABLE)
          .header(HttpHeaders.CONTENT_TYPE, TEXT_XML)
          .extract()
          .response();

    String actualBody = resp.body().asString();
    String expectedBody = "Accept header must be \"text/xml\" for this request, but it is "
          + "\"" + acceptHeader
          + "\"" + ", can not send */*";
    assertEquals(expectedBody, actualBody);
  }

  @Test
  void testInvalidAcceptHeaderReturns406() {
    String url = "/oai/" + API_KEY + "?verb=ListRecords";
    RestAssured
          .given()
          .header(HttpHeaders.ACCEPT, "text/json")
          .get(url)
          .then()
          .log().all()
          .statusCode(HttpStatus.SC_NOT_ACCEPTABLE);
    // fix jenkins code smells
    assertTrue(true);
  }

  @Test
  void shouldResendRequestUsingResumptionTokenWhenListRecordsResponseHasEmptyRecordsList() {
    log.info("=== Test successful ListRecords with empty records response ===");

    Path expectedMockPath = Paths.get(OaiPmhMockOkapi.PATH_TO_LIST_RECORDS_MOCK);
    String expectedMockBody = OaiPmhMockOkapi.getOaiPmhResponseAsXml(expectedMockPath);

    final Response resp = RestAssured
          .get(String.format("/oai?verb=ListRecords&metadataPrefix=oai_dc&apikey=%s", API_KEY))
          .then()
          .contentType(TEXT_XML)
          .statusCode(HttpStatus.SC_OK)
          .header(HttpHeaders.CONTENT_TYPE, TEXT_XML)
          .extract()
          .response();

    String actualBody = resp.body().asString();
    assertEquals(expectedMockBody, actualBody);
  }

  @Test
  void shouldReturnForbidden_whenUserHasMissingPermission() {
    log.info("=== Test successful ListRecords with empty records response ===");

    RestAssured
          .get(String.format("/oai?verb=GetRecord&metadataPrefix=marc21&identifier="
                + "recordIdForbiddenResponse&apikey=%s", API_KEY))
          .then()
          .contentType(TEXT_PLAIN)
          .statusCode(HttpStatus.SC_FORBIDDEN)
          .body(containsString(EXPECTED_ERROR_FORBIDDEN_MSG));
  }

  @Test
  void shouldStartHarvestingForFirstConsortiaTenantOnFirstCall() {
    log.info("=== Test successful ListRecords for the first tenant on first call ===");

    Path expectedMockPath = Paths.get(OaiPmhMockOkapi.PATH_TO_LIST_RECORDS_CONSORTIA_MOCK);
    String expectedMockBody = OaiPmhMockOkapi.getOaiPmhResponseAsXml(expectedMockPath);

    final Response resp = RestAssured
          .get(String.format("/oai?verb=ListRecords&metadataPrefix=oai_dc&apikey=%s",
                ApiKeyUtils.generateApiKey(10, "central", "user")))
          .then()
          .contentType(TEXT_XML)
          .statusCode(HttpStatus.SC_OK)
          .header(HttpHeaders.CONTENT_TYPE, TEXT_XML)
          .extract()
          .response();

    String actualBody = resp.body().asString();
    assertEquals(expectedMockBody, actualBody);
  }

  @Test
  void shouldTriggerHarvestingForNextConsortiaTenantWhenPreviousHasFinished() {
    log.info("=== Test successful ListRecords for the next tenant when previous has finished ===");

    Path expectedMockPath = Paths.get(OaiPmhMockOkapi.PATH_TO_LIST_RECORDS_CONSORTIA_MOCK2);
    String expectedMockBody = OaiPmhMockOkapi.getOaiPmhResponseAsXml(expectedMockPath);

    final Response resp = RestAssured
          .get(String.format("/oai?verb=ListRecords&resumptionToken="
                + "bWV0YWRhdGFQcmVmaXg9b2FpX2RjJnRlbmFudElkPXRlbmFudDI&apikey=%s",
                ApiKeyUtils.generateApiKey(10, "central", "user")))
          .then()
          .contentType(TEXT_XML)
          .statusCode(HttpStatus.SC_OK)
          .header(HttpHeaders.CONTENT_TYPE, TEXT_XML)
          .extract()
          .response();

    String actualBody = resp.body().asString();
    assertEquals(expectedMockBody, actualBody);
  }

  @Test
  void shouldContinueHarvestingForCurrentConsortiaTenantIfResumptionTokenIsPresent() {
    log.info("=== Test successful ListRecords for current tenant when resumption token "
          + "is present ===");

    Path expectedMockPath = Paths.get(OaiPmhMockOkapi.PATH_TO_LIST_RECORDS_CONSORTIA_MOCK3);
    String expectedMockBody = OaiPmhMockOkapi.getOaiPmhResponseAsXml(expectedMockPath);

    final Response resp = RestAssured
          .get(String.format("/oai?verb=ListRecords&resumptionToken="
                + "bWV0YWRhdGFQcmVmaXg9b2FpX2RjJnRlbmFudElkPXRlbmFudDMmcGFyYW09cGFyYW0&apikey=%s",
                ApiKeyUtils.generateApiKey(10, "central", "user")))
          .then()
          .contentType(TEXT_XML)
          .statusCode(HttpStatus.SC_OK)
          .header(HttpHeaders.CONTENT_TYPE, TEXT_XML)
          .extract()
          .response();

    String actualBody = resp.body().asString();
    assertEquals(expectedMockBody, actualBody);
  }

  @ParameterizedTest
  @CsvSource({"ListRecords,0", "ListIdentifiers,1"})
  void shouldAddResumptionTokenForLastResponseWhenNextTenantIsPresent(String verb, int mockIndex) {
    log.info("=== Test successful add resumption token if next tenant is present ===");

    var pathsToMockFiles = List.of(OaiPmhMockOkapi.PATH_TO_LIST_RECORDS_WITH_TOKEN_MOCK,
          OaiPmhMockOkapi.PATH_TO_LIST_IDENTIFIERS_WITH_TOKEN_MOCK);

    Path expectedMockPath = Paths.get(pathsToMockFiles.get(mockIndex));
    String expectedMockBody = OaiPmhMockOkapi.getOaiPmhResponseAsXml(expectedMockPath);

    final Response resp = RestAssured
          .get(String.format("/oai?verb=%s&resumptionToken="
                      + "bWV0YWRhdGFQcmVmaXg9b2FpX2RjJnRlbmFudElkPXRlbmFudDQmcGFyYW09cGFyYW0"
                      + "&apikey=%s",
                verb, ApiKeyUtils.generateApiKey(10, "central", "user")))
          .then()
          .contentType(TEXT_XML)
          .statusCode(HttpStatus.SC_OK)
          .header(HttpHeaders.CONTENT_TYPE, TEXT_XML)
          .extract()
          .response();

    String actualBody = resp.body().asString();
    assertEquals(expectedMockBody, actualBody);
  }

  @ParameterizedTest
  @CsvSource({"ListRecords,0", "ListIdentifiers,1"})
  void shouldAddNewResumptionTokenIfNotPresentForLastResponseWhenNextTenantIsPresent(
        String verb, int mockIndex) {
    log.info("=== Test successful add new resumption token if next tenant is present ===");

    var pathsToMockFiles = List.of(OaiPmhMockOkapi.PATH_TO_LIST_RECORDS_WITH_NEW_TOKEN_MOCK,
          OaiPmhMockOkapi.PATH_TO_LIST_IDENTIFIERS_WITH_NEW_TOKEN_MOCK);

    Path expectedMockPath = Paths.get(pathsToMockFiles.get(mockIndex));
    String expectedMockBody = OaiPmhMockOkapi.getOaiPmhResponseAsXml(expectedMockPath);

    final Response resp = RestAssured
          .get(String.format("/oai?verb=%s&metadataPrefix=oai_dc&apiKey=%s", verb,
                ApiKeyUtils.generateApiKey(10, "central2", "user")))
          .then()
          .contentType(TEXT_XML)
          .statusCode(HttpStatus.SC_OK)
          .header(HttpHeaders.CONTENT_TYPE, TEXT_XML)
          .extract()
          .response();

    String actualBody = resp.body().asString();
    assertEquals(expectedMockBody, actualBody);
  }

  @ParameterizedTest
  @CsvSource({"ListRecords,0", "ListIdentifiers,1"})
  void shouldContinueHarvestingOnErrorResponseWhenNextTenantIsPresent(String verb, int mockIndex) {
    log.info("=== Test successful continue harvesting on error response if next "
          + "tenant is present ===");

    var pathsToMockFiles = List.of(OaiPmhMockOkapi.PATH_TO_LIST_RECORDS_TOKEN_WITH_DATES_MOCK,
          OaiPmhMockOkapi.PATH_TO_LIST_IDENTIFIERS_TOKEN_WITH_DATES_MOCK);

    Path expectedMockPath = Paths.get(pathsToMockFiles.get(mockIndex));
    String expectedMockBody = OaiPmhMockOkapi.getOaiPmhResponseAsXml(expectedMockPath);

    final Response resp = RestAssured
          .get(String.format("/oai?verb=%s&metadataPrefix=oai_dc&apiKey=%s&from="
                + "2023-08-30&until=2023-08-31", verb, ApiKeyUtils.generateApiKey(10,
                "central2", "user")))
          .then()
          .contentType(TEXT_XML)
          .statusCode(HttpStatus.SC_OK)
          .header(HttpHeaders.CONTENT_TYPE, TEXT_XML)
          .extract()
          .response();

    String actualBody = resp.body().asString();
    assertEquals(expectedMockBody, actualBody);
  }

  @Test
  void shouldReturnResponseWhenNextTenantIsNotPresent() {
    log.info("=== Test successful return response if next tenant is not present ===");

    Path expectedMockPath = Paths.get(OaiPmhMockOkapi.PATH_TO_LIST_RECORDS_MOCK);
    String expectedMockBody = OaiPmhMockOkapi.getOaiPmhResponseAsXml(expectedMockPath);

    final Response resp = RestAssured
          .get(String.format("/oai?verb=ListRecords&resumptionToken="
                + "bWV0YWRhdGFQcmVmaXg9b2FpX2RjJnRlbmFudElkPXRlbmFudDImcGFyYW09cGFyYW0&apikey=%s",
                ApiKeyUtils.generateApiKey(10, "central", "user")))
          .then()
          .contentType(TEXT_XML)
          .statusCode(HttpStatus.SC_OK)
          .header(HttpHeaders.CONTENT_TYPE, TEXT_XML)
          .extract()
          .response();

    String actualBody = resp.body().asString();
    assertEquals(expectedMockBody, actualBody);
  }
}
