package org.folio.edge.oaipmh;

import com.jayway.restassured.RestAssured;
import com.jayway.restassured.response.Response;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
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
import org.openarchives.oai._2.OAIPMH;
import org.openarchives.oai._2.OAIPMHerrorType;
import org.openarchives.oai._2.OAIPMHerrorcodeType;
import org.openarchives.oai._2.RequestType;
import org.openarchives.oai._2.VerbType;

import javax.xml.bind.JAXBException;
import java.io.UnsupportedEncodingException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.folio.edge.core.Constants.SYS_LOG_LEVEL;
import static org.folio.edge.core.Constants.SYS_OKAPI_URL;
import static org.folio.edge.core.Constants.SYS_PORT;
import static org.folio.edge.core.Constants.SYS_REQUEST_TIMEOUT_MS;
import static org.folio.edge.core.Constants.SYS_SECURE_STORE_PROP_FILE;
import static org.folio.edge.core.Constants.TEXT_PLAIN;
import static org.folio.edge.oaipmh.utils.OaiPmhMockOkapi.REQUEST_TIMEOUT_MS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.spy;
import static org.openarchives.oai._2.OAIPMHerrorcodeType.BAD_ARGUMENT;
import static org.openarchives.oai._2.OAIPMHerrorcodeType.BAD_VERB;
import static org.openarchives.oai._2.VerbType.LIST_IDENTIFIERS;

@RunWith(VertxUnitRunner.class)
public class MainVerticleTest {

  private static final Logger logger = Logger.getLogger(MainVerticleTest.class);

  private static final String apiKey = "Z1luMHVGdjNMZl9kaWt1X2Rpa3U=";
  private static final String badApiKey = "ZnMwMDAwMDAwMA==0000";

  private static final long requestTimeoutMs = REQUEST_TIMEOUT_MS;

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
  public static void tearDownOnce() {
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
  public void testAdminHealth() {
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
  public void testGetRecordNotFoundHttpGet() {
    logger.info("=== Test GetRecord OAI-PMH error - not found (HTTP GET)===");

    Path expectedMockPath = Paths.get(OaiPmhMockOkapi.PATH_TO_GET_RECORDS_ERROR_MOCK);
    String expectedMockBody = OaiPmhMockOkapi.getOaiPmhResponseAsXml(expectedMockPath);
    int expectedHttpStatusCode = 404;

    final Response resp = RestAssured
      .get(String.format("/oai?verb=GetRecord" +
        "&identifier=oai:arXiv.org:quant-ph/02131001&metadataPrefix=oai_dc&apikey=%s", apiKey))
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
  public void testGetRecordNotFoundHttpPost() {
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
  public void testGetRecordSuccessfulHttpGet() {
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
  public void testGetRecordSuccessfulHttpPost() {
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
  public void testIdentifySuccessfulHttpGet() {
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
  public void testIdentifySuccessfulHttpPost() {
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
  public void testIdentifyBadApiKeyHttpGet() {
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
  public void testIdentifyAccessDeniedApiKeyHttpGet() {
    logger.info("=== Test Access Denied apikey Identify OAI-PMH (HTTP POST) ===");

    final Response resp = RestAssured
      .post(String.format("/oai?verb=Identify&apikey=%s", apiKey.substring(0, apiKey.length() - 2)))
      .then()
      .contentType(TEXT_PLAIN)
      .statusCode(403)
      .header(HttpHeaders.CONTENT_TYPE, TEXT_PLAIN)
      .extract()
      .response();

    String actualBody = resp.body().asString();
    assertNotNull(actualBody);
  }

  @Test
  public void testGetRecordInvalidApiKeyHttpPost() {
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
  public void testGetRecordOkapiExceptionHttpGet() {
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
  public void testGetRecordOkapiTimeoutExceptionHttpGet() {
    logger.info("=== Test successful GetRecord OAI-PMH (HTTP GET) ===");

    final Response resp = RestAssured
      .get(String.format("/oai?verb=GetRecord"
        + "&identifier=TimeoutException&metadataPrefix=oai_dc&apikey=%s", apiKey))
      .then()
      .contentType(TEXT_PLAIN)
      .statusCode(408)
      .header(HttpHeaders.CONTENT_TYPE, TEXT_PLAIN)
      .extract()
      .response();

    String actualBody = resp.body().asString();
    assertNotNull(actualBody);
  }

  @Test
  public void testValidateBadVerb() throws UnsupportedEncodingException, JAXBException {
    logger.info("=== Test validate w/ invalid verb ===");

    final Response resp = RestAssured
      .get("/oai?verb=nastyVerb&apikey=" + apiKey)
      .then()
      .contentType("text/xml")
      .statusCode(400)
      .extract()
      .response();

    OAIPMH expectedResp =
      buildOAIPMHErrorResponse(null, BAD_VERB, "Bad verb. Verb 'nastyVerb' is not implemented");
    String expectedRespStr = ResponseHelper.getInstance().writeToString(expectedResp);

    verifyResponse(expectedRespStr, resp.body().asString());
  }

  @Test
  public void testValidateIllegalParams() throws UnsupportedEncodingException, JAXBException {
    logger.info("=== Test validate w/ illegal params ===");

    final Response resp = RestAssured
      .get("/oai?verb=ListIdentifiers&metadataPrefix=oai_dc&extraParam=Test&apikey=" + apiKey)
      .then()
      .contentType("text/xml")
      .statusCode(400)
      .extract()
      .response();

    OAIPMH expectedResp = buildOAIPMHErrorResponse(LIST_IDENTIFIERS, BAD_ARGUMENT,
        "Verb 'ListIdentifiers', illegal argument: extraParam");
    expectedResp.getRequest()
                .withMetadataPrefix("oai_dc");
    String expectedRespStr = ResponseHelper.getInstance().writeToString(expectedResp);

    verifyResponse(expectedRespStr, resp.body().asString());
  }

  @Test
  public void testValidateExclusiveParam() throws UnsupportedEncodingException, JAXBException {
    logger.info("=== Test validate w/ exclusive and other params ===");

    final Response resp = RestAssured
      .get("/oai?verb=ListIdentifiers&resumptionToken=123456789&metadataPrefix=oai_dc&apikey=" + apiKey)
      .then()
      .contentType("text/xml")
      .statusCode(400)
      .extract()
      .response();

    OAIPMH expectedResp = buildOAIPMHErrorResponse(LIST_IDENTIFIERS, BAD_ARGUMENT,
      "Verb 'ListIdentifiers', argument 'resumptionToken' is exclusive, no others maybe specified with it.");
    expectedResp.getRequest()
                .withResumptionToken("123456789")
                .withMetadataPrefix("oai_dc");
    String expectedRespStr = ResponseHelper.getInstance().writeToString(expectedResp);

    verifyResponse(expectedRespStr, resp.body().asString());
  }

  @Test
  public void testValidateBadFromUntilParams() throws UnsupportedEncodingException, JAXBException {
    logger.info("=== Test validate w/ bad 'from' and 'until' params ===");

    final Response resp = RestAssured
      .get("/oai?verb=ListIdentifiers&metadataPrefix=oai_dc&from=2002-05-01T14:15:00&until=2002-05-02T14:15:00.000Z&apikey=" + apiKey)
      .then()
      .contentType("text/xml")
      .statusCode(400)
      .extract()
      .response();

    OAIPMH expectedResp = buildOAIPMHErrorResponse(LIST_IDENTIFIERS, BAD_ARGUMENT,
      "Bad datestamp format for 'from' argument.")
        .withErrors(new OAIPMHerrorType()
          .withCode(BAD_ARGUMENT)
          .withValue("Bad datestamp format for 'until' argument."));
    expectedResp.getRequest()
                .withMetadataPrefix("oai_dc")
                .withFrom("2002-05-01T14:15:00")
                .withUntil("2002-05-02T14:15:00.000Z");
    String expectedRespStr = ResponseHelper.getInstance().writeToString(expectedResp);

    verifyResponse(expectedRespStr, resp.body().asString());
  }

  @Test
  public void testValidateMissingRequiredParams() throws UnsupportedEncodingException, JAXBException {
    logger.info("=== Test validate w/ missing required params ===");

    final Response resp = RestAssured
      .get("/oai?verb=ListIdentifiers&from=2002-05-01T14:15:00Z&apikey=" + apiKey)
      .then()
      .contentType("text/xml")
      .statusCode(400)
      .extract()
      .response();

    OAIPMH expectedResp = buildOAIPMHErrorResponse(LIST_IDENTIFIERS, BAD_ARGUMENT,
      "Missing required parameter: metadataPrefix");
    expectedResp.getRequest()
                .withFrom("2002-05-01T14:15:00Z");
    String expectedRespStr = ResponseHelper.getInstance().writeToString(expectedResp);

    verifyResponse(expectedRespStr, resp.body().asString());
  }

  private OAIPMH buildOAIPMHErrorResponse(VerbType verb, OAIPMHerrorcodeType errorCode, String message) {
    return new OAIPMH()
      .withRequest(new RequestType()
        .withVerb(verb)
        .withValue(RestAssured.baseURI + "/oai"))
      .withErrors(new OAIPMHerrorType()
        .withCode(errorCode)
        .withValue(message));
  }

  private void verifyResponse(String expectedResponse, String actualResponse)
    throws JAXBException, UnsupportedEncodingException {
    // Unmarshal string to OAIPMH, verify presence of volatile ResponseDate and then remove it to verify the rest of the data
    OAIPMH actualOaiResp = ResponseHelper.getInstance().stringToOaiPmh(actualResponse);

    assertNotNull(actualOaiResp.getResponseDate());
    actualOaiResp.setResponseDate(null);

    actualResponse = ResponseHelper.getInstance().writeToString(actualOaiResp);

    assertEquals(expectedResponse, actualResponse);
  }
}
