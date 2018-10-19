package org.folio.edge.oaipmh;

import static org.folio.edge.core.Constants.SYS_LOG_LEVEL;
import static org.folio.edge.core.Constants.SYS_OKAPI_URL;
import static org.folio.edge.core.Constants.SYS_PORT;
import static org.folio.edge.core.Constants.SYS_REQUEST_TIMEOUT_MS;
import static org.folio.edge.core.Constants.SYS_SECURE_STORE_PROP_FILE;
import static org.folio.edge.core.Constants.TEXT_PLAIN;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.spy;

import com.jayway.restassured.RestAssured;
import com.jayway.restassured.response.Response;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.apache.http.HttpHeaders;
import org.apache.log4j.Logger;
import org.folio.edge.core.utils.ApiKeyUtils;
import org.folio.edge.core.utils.test.TestUtils;
import org.folio.edge.oaipmh.utils.Constants;
import org.folio.edge.oaipmh.utils.OaiPmhMockOkapi;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class MainVerticleTest {

  private static final Logger logger = Logger.getLogger(MainVerticleTest.class);

  private static final String apiKey = "Z1luMHVGdjNMZl9kaWt1X2Rpa3U=";
  private static final String badApiKey = "ZnMwMDAwMDAwMA==0000";

  private static final long requestTimeoutMs = 3000L;

  private static final String invalidApiKeyExpectedResponseBody
    = "Invalid API Key: ZnMwMDAwMDAwMA==0000";

  private static Vertx vertx;
  private static OaiPmhMockOkapi mockOkapi;

  @BeforeClass
  public static void setUpOnce(TestContext context) throws Exception {
    int okapiPort = TestUtils.getPort();
    int serverPort = TestUtils.getPort();

    List<String> knownTenants = new ArrayList<>();
    knownTenants.add(ApiKeyUtils.parseApiKey(apiKey).tenantId);

    mockOkapi = spy(new OaiPmhMockOkapi(okapiPort, knownTenants));
    mockOkapi.start(context);

    vertx = Vertx.vertx();

    System.setProperty(SYS_PORT, String.valueOf(serverPort));
    System.setProperty(SYS_OKAPI_URL, "http://localhost:" + okapiPort);
    System.setProperty(SYS_SECURE_STORE_PROP_FILE, "src/main/resources/ephemeral.properties");
    System.setProperty(SYS_LOG_LEVEL, "DEBUG");
    System.setProperty(SYS_REQUEST_TIMEOUT_MS, String.valueOf(requestTimeoutMs));

    final DeploymentOptions opt = new DeploymentOptions();
    vertx.deployVerticle(MainVerticle.class.getName(), opt, context.asyncAssertSuccess());

    RestAssured.baseURI = "http://localhost:" + serverPort;
    RestAssured.port = serverPort;
    RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
  }

  @AfterClass
  public static void tearDownOnce(TestContext context) {
    logger.info("Shutting down server");
    vertx.close(res -> {
      if (res.failed()) {
        logger.error("Failed to shut down edge-orders server", res.cause());
        fail(res.cause().getMessage());
      } else {
        logger.info("Successfully shut down edge-orders server");
      }

      logger.info("Shutting down mock Okapi");
      mockOkapi.close();
    });
  }


  @Test
  public void testAdminHealth(TestContext context) {
    logger.info("=== Test the health check endpoint ===");

    final Response resp = RestAssured
      .get("/admin/health")
      .then()
      .contentType(TEXT_PLAIN)
      .statusCode(200)
      .header(HttpHeaders.CONTENT_TYPE, TEXT_PLAIN)
      .extract()
      .response();

    assertEquals("\"OK\"", resp.body().asString());
  }

  @Test
  public void testGetRecordNotFoundHttpGet(TestContext context) {
    logger.info("=== Test GetRecord OAI-PMH error - not found (HTTP GET)===");

    Path expectedMockPath = Paths.get(OaiPmhMockOkapi.PATH_TO_GET_RECORDS_ERROR_MOCK);
    String expectedMockBody = OaiPmhMockOkapi.getOaiPmhResponseAsXml(expectedMockPath);
    int expectedHttpStatusCode = 404;

    final Response resp = RestAssured
      .get(String.format("/oai?verb=GetRecord" +
        "&identifier=oai:arXiv.org:quant-ph/02131001&metadataPrefix=oai_dc&apiKey=%s", apiKey))
      .then()
      .contentType(Constants.TEXT_XML_TYPE)
      .statusCode(expectedHttpStatusCode)
      .header(HttpHeaders.CONTENT_TYPE, Constants.TEXT_XML_TYPE)
      .extract()
      .response();

    String actualBody = resp.body().asString();
    assertEquals(expectedMockBody, actualBody);
  }

  @Test
  public void testGetRecordNotFoundHttpPost(TestContext context) {
    logger.info("=== Test GetRecord OAI-PMH error - not found (HTTP POST)===");

    Path expectedMockPath = Paths.get(OaiPmhMockOkapi.PATH_TO_GET_RECORDS_ERROR_MOCK);
    String expectedMockBody = OaiPmhMockOkapi.getOaiPmhResponseAsXml(expectedMockPath);
    int expectedHttpStatusCode = 404;

    final Response resp = RestAssured.given()
      .parameters("verb", "GetRecord",
        "identifier", "oai:arXiv.org:quant-ph/02131001",
        "metadataPrefix", "oai_dc",
        "apikey", apiKey)
      .post("/oai")
      .then()
      .contentType(Constants.TEXT_XML_TYPE)
      .statusCode(expectedHttpStatusCode)
      .header(HttpHeaders.CONTENT_TYPE, Constants.TEXT_XML_TYPE)
      .extract()
      .response();

    String actualBody = resp.body().asString();
    assertEquals(expectedMockBody, actualBody);
  }

  @Test
  public void testGetRecordSuccessfulHttpGet(TestContext context) {
    logger.info("=== Test successful GetRecord OAI-PMH (HTTP GET) ===");

    Path expectedMockPath = Paths.get(OaiPmhMockOkapi.PATH_TO_GET_RECORDS_MOCK);
    String expectedMockBody = OaiPmhMockOkapi.getOaiPmhResponseAsXml(expectedMockPath);
    int expectedHttpStatusCode = 200;
    final Response resp = RestAssured
      .get(String.format("/oai?verb=GetRecord"
        + "&identifier=oai:arXiv.org:cs/0112017&metadataPrefix=oai_dc&apikey=%s", apiKey))
      .then()
      .contentType(Constants.TEXT_XML_TYPE)
      .statusCode(expectedHttpStatusCode)
      .header(HttpHeaders.CONTENT_TYPE, Constants.TEXT_XML_TYPE)
      .extract()
      .response();

    String actualBody = resp.body().asString();
    assertEquals(expectedMockBody, actualBody);
  }

  @Test
  public void testGetRecordSuccessfulHttpPost(TestContext context) {
    logger.info("=== Test successful GetRecord OAI-PMH (HTTP POST) ===");

    Path expectedMockPath = Paths.get(OaiPmhMockOkapi.PATH_TO_GET_RECORDS_MOCK);
    String expectedMockBody = OaiPmhMockOkapi.getOaiPmhResponseAsXml(expectedMockPath);
    int expectedHttpStatusCode = 200;
    final Response resp = RestAssured.given()
      .parameters("apikey", apiKey,
        "verb", "GetRecord",
        "metadataPrefix", "oai_dc",
        "identifier", "oai:arXiv.org:cs/0112017")
      .post("/oai")
      .then()
      .contentType(Constants.TEXT_XML_TYPE)
      .statusCode(expectedHttpStatusCode)
      .header(HttpHeaders.CONTENT_TYPE, Constants.TEXT_XML_TYPE)
      .extract()
      .response();

    String actualBody = resp.body().asString();
    assertEquals(expectedMockBody, actualBody);
  }

  @Test
  public void testIdentifySuccessfulHttpGet(TestContext context) {
    logger.info("=== Test successful Identify OAI-PMH (HTTP GET) ===");

    Path expectedMockPath = Paths.get(OaiPmhMockOkapi.PATH_TO_IDENTIFY_MOCK);
    String expectedMockBody = OaiPmhMockOkapi.getOaiPmhResponseAsXml(expectedMockPath);
    int expectedHttpStatusCode = 200;
    final Response resp = RestAssured
      .get(String.format("/oai?verb=Identify&apikey=%s", apiKey))
      .then()
      .contentType(Constants.TEXT_XML_TYPE)
      .statusCode(expectedHttpStatusCode)
      .header(HttpHeaders.CONTENT_TYPE, Constants.TEXT_XML_TYPE)
      .extract()
      .response();

    String actualBody = resp.body().asString();
    assertEquals(expectedMockBody, actualBody);
  }

  @Test
  public void testIdentifySuccessfulHttpPost(TestContext context) {
    logger.info("=== Test successful Identify OAI-PMH (HTTP POST) ===");

    Path expectedMockPath = Paths.get(OaiPmhMockOkapi.PATH_TO_IDENTIFY_MOCK);
    String expectedMockBody = OaiPmhMockOkapi.getOaiPmhResponseAsXml(expectedMockPath);
    int expectedHttpStatusCode = 200;
    final Response resp = RestAssured.given()
      .parameters("apikey", apiKey,
        "verb", "Identify")
      .post("/oai")
      .then()
      .contentType(Constants.TEXT_XML_TYPE)
      .statusCode(expectedHttpStatusCode)
      .header(HttpHeaders.CONTENT_TYPE, Constants.TEXT_XML_TYPE)
      .extract()
      .response();

    String actualBody = resp.body().asString();
    assertEquals(expectedMockBody, actualBody);
  }


  @Test
  public void testIdentifyBadApiKeyHttpGet(TestContext context) {
    logger.info("=== Test bad apikey Identify OAI-PMH (HTTP GET) ===");

    int expectedHttpStatusCode = 401;
    final Response resp = RestAssured
      .get(String.format("/oai?verb=Identify&apikey=%s", badApiKey))
      .then()
      .contentType(TEXT_PLAIN)
      .statusCode(expectedHttpStatusCode)
      .header(HttpHeaders.CONTENT_TYPE, TEXT_PLAIN)
      .extract()
      .response();

    String actualBody = resp.body().asString();
    assertEquals(invalidApiKeyExpectedResponseBody, actualBody);
  }

  @Test
  public void testGetRecordInvalidApiKeyHttpPost(TestContext context) {
    logger.info("=== Test successful GetRecord OAI-PMH (HTTP POST) ===");

    int expectedHttpStatusCode = 401;
    final Response resp = RestAssured.given()
      .parameters("apikey", badApiKey,
        "verb", "GetRecord",
        "metadataPrefix", "oai_dc",
        "identifier", "oai:arXiv.org:cs/0112017")
      .post("/oai")
      .then()
      .contentType(TEXT_PLAIN)
      .statusCode(expectedHttpStatusCode)
      .header(HttpHeaders.CONTENT_TYPE, TEXT_PLAIN)
      .extract()
      .response();

    String actualBody = resp.body().asString();
    assertEquals(invalidApiKeyExpectedResponseBody, actualBody);
  }

  @Test
  public void testGetRecordOkapiExceptionHttpGet(TestContext context) {
    logger.info("=== Test successful GetRecord OAI-PMH (HTTP GET) ===");

    String expectedMockBody = "Internal Server Error";
    int expectedHttpStatusCode = 500;
    final Response resp = RestAssured
      .get(String.format("/oai?verb=GetRecord"
        + "&identifier=exception&metadataPrefix=oai_dc&apikey=%s", apiKey))
      .then()
      .contentType(TEXT_PLAIN)
      .statusCode(expectedHttpStatusCode)
      .header(HttpHeaders.CONTENT_TYPE, TEXT_PLAIN)
      .extract()
      .response();

    String actualBody = resp.body().asString();
    assertEquals(expectedMockBody, actualBody);
  }

  @Test
  public void testGetRecordOkapiTimeoutHttpGet(TestContext context) {
    logger.info("=== Test timeout GetRecord OAI-PMH (HTTP GET) ===");

    int expectedHttpStatusCode = 408;
    RestAssured
      .get(String.format("/oai?verb=GetRecord"
        + "&identifier=timeout&metadataPrefix=oai_dc&apikey=%s", apiKey))
      .then()
      .contentType(TEXT_PLAIN)
      .statusCode(expectedHttpStatusCode)
      .header(HttpHeaders.CONTENT_TYPE, TEXT_PLAIN)
      .extract()
      .response();
  }
}
