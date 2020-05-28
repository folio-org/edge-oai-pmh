package org.folio.edge.oaipmh;

import static com.jayway.restassured.config.DecoderConfig.decoderConfig;
import static org.folio.edge.core.Constants.SYS_LOG_LEVEL;
import static org.folio.edge.core.Constants.SYS_OKAPI_URL;
import static org.folio.edge.core.Constants.SYS_PORT;
import static org.folio.edge.core.Constants.SYS_REQUEST_TIMEOUT_MS;
import static org.folio.edge.core.Constants.SYS_RESPONSE_COMPRESSION;
import static org.folio.edge.core.Constants.SYS_SECURE_STORE_PROP_FILE;
import static org.folio.edge.core.Constants.TEXT_PLAIN;
import static org.folio.edge.core.Constants.TEXT_XML;
import static org.folio.edge.oaipmh.utils.OaiPmhMockOkapi.REQUEST_TIMEOUT_MS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.spy;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.folio.edge.core.utils.ApiKeyUtils;
import org.folio.edge.core.utils.test.TestUtils;
import org.folio.edge.oaipmh.utils.OaiPmhMockOkapi;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.jayway.restassured.RestAssured;
import com.jayway.restassured.config.DecoderConfig;
import com.jayway.restassured.response.Header;
import com.jayway.restassured.response.Response;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RunWith(VertxUnitRunner.class)
public class OaiPmhTest {

  private static final String API_KEY = ApiKeyUtils.generateApiKey(10, "diku", "user");
  private static final String ILLEGAL_API_KEY = "eyJzIjoiYmJaUnYyamt2ayIsInQiOiJkaWt1IiwidSI6ImRpa3VfYSJ9";
  private static final String BAD_API_KEY = "ZnMwMDAwMDAwMA==0000";

  private static final String INVALID_API_KEY_EXPECTED_RESPONSE_BODY = "Invalid API Key: ZnMwMDAwMDAwMA==0000";

  private static Vertx vertx;
  private static OaiPmhMockOkapi mockOkapi;

  @BeforeClass
  public static void setUpOnce(TestContext context) throws Exception {
    int okapiPort = TestUtils.getPort();
    int serverPort = TestUtils.getPort();

    List<String> knownTenants = new ArrayList<>();
    knownTenants.add(ApiKeyUtils.parseApiKey(API_KEY).tenantId);

    mockOkapi = spy(new OaiPmhMockOkapi(okapiPort, knownTenants));
    mockOkapi.start(context);
    vertx = Vertx.vertx();

    System.setProperty(SYS_PORT, String.valueOf(serverPort));
    System.setProperty(SYS_OKAPI_URL, "http://localhost:" + okapiPort);
    System.setProperty(SYS_SECURE_STORE_PROP_FILE, "src/main/resources/ephemeral.properties");
    System.setProperty(SYS_LOG_LEVEL, "TRACE");
    System.setProperty(SYS_REQUEST_TIMEOUT_MS, String.valueOf(REQUEST_TIMEOUT_MS));
    System.setProperty(SYS_RESPONSE_COMPRESSION, Boolean.toString(true));

    final DeploymentOptions opt = new DeploymentOptions();
    vertx.deployVerticle(MainVerticle.class.getName(), opt, context.asyncAssertSuccess());

    RestAssured.baseURI = "http://localhost:" + serverPort;
    RestAssured.port = serverPort;
    RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
  }

  @AfterClass
  public static void tearDownOnce(TestContext context) {
    log.info("Shutting down server");
    vertx.close(res -> {
      if (res.failed()) {
        log.error("Failed to shut down edge-orders server", res.cause());
        fail(res.cause().getMessage());
      } else {
        log.info("Successfully shut down edge-orders server");
      }

      log.info("Shutting down mock Okapi");
      mockOkapi.close(context);
    });
  }

  @Test
  public void testAdminHealth() {
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
  public void testGetRecordNotFoundHttpGet() {
    log.info("=== Test GetRecord OAI-PMH error - not found (HTTP GET)===");

    Path expectedMockPath = Paths.get(OaiPmhMockOkapi.PATH_TO_GET_RECORDS_ERROR_MOCK);
    String expectedMockBody = OaiPmhMockOkapi.getOaiPmhResponseAsXml(expectedMockPath);

    final Response resp = RestAssured
      .get(String.format("/oai?verb=GetRecord" +
        "&identifier=oai:arXiv.org:quant-ph/02131001&metadataPrefix=oai_dc&apikey=%s", API_KEY));
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
  public void testGetRecordNotFoundHttpPost() {
    log.info("=== Test GetRecord OAI-PMH error - not found (HTTP POST)===");

    Path expectedMockPath = Paths.get(OaiPmhMockOkapi.PATH_TO_GET_RECORDS_ERROR_MOCK);
    String expectedMockBody = OaiPmhMockOkapi.getOaiPmhResponseAsXml(expectedMockPath);

    final Response resp = RestAssured.given()
      .parameters("verb", "GetRecord",
        "identifier", "oai:arXiv.org:quant-ph/02131001",
        "metadataPrefix", "oai_dc",
        "apikey", API_KEY)
      .post("/oai")
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
  public void testGetRecordSuccessfulHttpGet() {
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
  public void testGetRecordSuccessfulHttpPost() {
    log.info("=== Test successful GetRecord OAI-PMH (HTTP POST) ===");

    Path expectedMockPath = Paths.get(OaiPmhMockOkapi.PATH_TO_GET_RECORDS_MOCK);
    String expectedMockBody = OaiPmhMockOkapi.getOaiPmhResponseAsXml(expectedMockPath);

    final Response resp = RestAssured.given()
      .parameters("apikey", API_KEY,
        "verb", "GetRecord",
        "metadataPrefix", "oai_dc",
        "identifier", "oai:arXiv.org:cs/0112017")
      .post("/oai")
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
  public void testIdentifySuccessfulHttpGet() {
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
  public void testIdentifySuccessfulHttpPost() {
    log.info("=== Test successful Identify OAI-PMH (HTTP POST) ===");

    Path expectedMockPath = Paths.get(OaiPmhMockOkapi.PATH_TO_IDENTIFY_MOCK);
    String expectedMockBody = OaiPmhMockOkapi.getOaiPmhResponseAsXml(expectedMockPath);

    final Response resp = RestAssured.given()
      .parameters("apikey", API_KEY,
        "verb", "Identify")
      .post("/oai")
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
  public void testIdentifyBadApiKeyHttpGet() {
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
  public void testApiKeyOnPath() {
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
  public void testIdentifyAccessDeniedApiKeyHttpGet() {
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
  public void testGetRecordInvalidApiKeyHttpPost() {
    log.info("=== Test bad apikey GetRecord OAI-PMH (HTTP POST) ===");

    final Response resp = RestAssured.given()
      .parameters("apikey", BAD_API_KEY,
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
  public void testGetRecordOkapiExceptionHttpGet() {
    log.info("=== Test exceptional GetRecord OAI-PMH (HTTP GET) ===");

    String expectedMockBody = "Internal Server Error";
    final Response resp = RestAssured
      .get(String.format("/oai?verb=GetRecord"
        + "&identifier=exception&metadataPrefix=oai_dc&apikey=%s", API_KEY))
      .then()
      .contentType(TEXT_PLAIN)
      .statusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR)
      .header(HttpHeaders.CONTENT_TYPE, TEXT_PLAIN)
      .extract()
      .response();

    String actualBody = resp.body().asString();
    assertEquals(expectedMockBody, actualBody);
  }

  @Test
  public void testGetRecordOkapiTimeoutExceptionHttpGet() {
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
  public void testCompressionAlgorithms() {
    log.info("=== Test response compression ===");

    Path expectedMockPath = Paths.get(OaiPmhMockOkapi.PATH_TO_GET_RECORDS_MOCK);
    String expectedMockBody = OaiPmhMockOkapi.getOaiPmhResponseAsXml(expectedMockPath);

    for (DecoderConfig.ContentDecoder type : DecoderConfig.ContentDecoder.values()) {
      final Response resp = RestAssured.given()
        .config(RestAssured.config().decoderConfig(decoderConfig().contentDecoders(type)))
        .get(String.format("/oai?verb=GetRecord&identifier=oai:arXiv.org:cs/0112017&metadataPrefix=oai_dc&apikey=%s", API_KEY))
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
  public void testNoCompression() {
    log.info("=== Test no response compression  ===");

    Path expectedMockPath = Paths.get(OaiPmhMockOkapi.PATH_TO_GET_RECORDS_MOCK);
    String expectedMockBody = OaiPmhMockOkapi.getOaiPmhResponseAsXml(expectedMockPath);
    final Response resp = RestAssured.given()
      .config(RestAssured.config().decoderConfig(decoderConfig().noContentDecoders()))
      .header(new Header(HttpHeaders.ACCEPT_ENCODING, "instance"))
      .get(String.format("/oai?verb=GetRecord&identifier=oai:arXiv.org:cs/0112017&metadataPrefix=oai_dc&apikey=%s", API_KEY))
      .then()
      .contentType(TEXT_XML)
      .statusCode(HttpStatus.SC_OK)
      .header(HttpHeaders.CONTENT_TYPE, TEXT_XML)
      .extract()
      .response();

    assertFalse(resp.headers().asList().stream()
      .collect(Collectors.toMap(Header::getName, Header::getValue)).containsKey(HttpHeaders.ACCEPT_ENCODING));

    String actualBody = resp.body().asString();
    assertEquals(expectedMockBody, actualBody);
  }

  @Test
  public void testAcceptHeader() {
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
  public void testNoAcceptHeader() {
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
  public void testAcceptHeaderHasUnsupportedType() {
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
    String expectedBody = "Accept header must be \"text/xml\" for this request, but it is " +"\""+ unsupportedAcceptType
      +"\""+", can not send */*";
    assertEquals(expectedBody, actualBody);
  }

  @Test
  public void testAcceptHeaderIsAbsent() {
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
  public void testAcceptHeaderIsEmpty() {
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

  @Test
  public void testAcceptHeaderHasAllTextSybtypesSymbol() {
    log.info("=== Test Accept header has all text sybtypes symbol ===");

    Path expectedMockPath = Paths.get(OaiPmhMockOkapi.PATH_TO_GET_RECORDS_MOCK);
    String expectedMockBody = OaiPmhMockOkapi.getOaiPmhResponseAsXml(expectedMockPath);

    final Response resp = RestAssured
      .given()
      .header(HttpHeaders.ACCEPT, "text/*")
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
  public void testAcceptHeaderHasAllTextSybtypesSymbolWithParameterAndWithUnsupportedTypes() {
    log.info("=== Test Accept header has all text sybtypes symbol with parameter and with unsupported types ===");

    Path expectedMockPath = Paths.get(OaiPmhMockOkapi.PATH_TO_GET_RECORDS_MOCK);
    String expectedMockBody = OaiPmhMockOkapi.getOaiPmhResponseAsXml(expectedMockPath);

    String acceptHeader = "text/*; q=0.2, application/xml, application/xhtml+xml";

    final Response resp = RestAssured
      .given()
      .header(HttpHeaders.ACCEPT, acceptHeader)
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
  public void testAcceptHeaderHasTextTypeXMLAndSomeUnsupportedTypes() {
    log.info("=== Test Accept header has text type XML and some unsupported types ===");

    Path expectedMockPath = Paths.get(OaiPmhMockOkapi.PATH_TO_GET_RECORDS_MOCK);
    String expectedMockBody = OaiPmhMockOkapi.getOaiPmhResponseAsXml(expectedMockPath);

    String acceptHeader = "text/html, application/xml, text/xml";

    final Response resp = RestAssured
      .given()
      .header(HttpHeaders.ACCEPT, acceptHeader)
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
  public void testAcceptHeaderHasAllTypesAndAllSubtypesSymbolAndSomeUnsupportedTypes() {
    log.info("=== Test Accept header has all types and all subtypes symbol and some unsupported types ===");

    Path expectedMockPath = Paths.get(OaiPmhMockOkapi.PATH_TO_GET_RECORDS_MOCK);
    String expectedMockBody = OaiPmhMockOkapi.getOaiPmhResponseAsXml(expectedMockPath);

    String acceptHeader = "text/html, text/html;level=1, */*";

    final Response resp = RestAssured
      .given()
      .header(HttpHeaders.ACCEPT, acceptHeader)
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
  public void testAcceptHeaderHasAllTypesAndAllSubtypesSymbolAndAllTextSubtypesSymbolAndSomeUnsupportedTypes() {
    log.info("=== Test Accept header has all types and all subtypes symbol and all text subtypes symbol and some unsupported types ===");

    Path expectedMockPath = Paths.get(OaiPmhMockOkapi.PATH_TO_GET_RECORDS_MOCK);
    String expectedMockBody = OaiPmhMockOkapi.getOaiPmhResponseAsXml(expectedMockPath);

    String acceptHeader = "text/*;q=0.3, text/html;level=1, */*;q=0.5";

    final Response resp = RestAssured
      .given()
      .header(HttpHeaders.ACCEPT, acceptHeader)
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
  public void testAcceptHeaderHasOnlyUnsupportedTypesWithParameter() {
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
    String expectedBody = "Accept header must be \"text/xml\" for this request, but it is " +"\""+ acceptHeader
      +"\""+", can not send */*";
    assertEquals(expectedBody, actualBody);
  }

  @Test
  public void testInvalidAcceptHeaderReturns406() {
    String url = "/oai/" + API_KEY + "?verb=ListRecords";
    RestAssured
      .given()
      .header(HttpHeaders.ACCEPT, "text/json")
      .get(url)
      .then()
      .log().all()
      .statusCode(HttpStatus.SC_NOT_ACCEPTABLE);
    //fix jenkins code smells
    assertTrue(true);
  }
}
