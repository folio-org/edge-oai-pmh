package org.folio.edge.oaipmh;

import static com.jayway.restassured.config.DecoderConfig.decoderConfig;
import static org.folio.edge.core.Constants.SYS_LOG_LEVEL;
import static org.folio.edge.core.Constants.SYS_OKAPI_URL;
import static org.folio.edge.core.Constants.SYS_PORT;
import static org.folio.edge.core.Constants.SYS_REQUEST_TIMEOUT_MS;
import static org.folio.edge.core.Constants.SYS_RESPONSE_COMPRESSION;
import static org.folio.edge.core.Constants.SYS_SECURE_STORE_PROP_FILE;
import static org.folio.edge.core.Constants.TEXT_PLAIN;
import static org.folio.edge.oaipmh.utils.OaiPmhMockOkapi.REQUEST_TIMEOUT_MS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.spy;
import static org.openarchives.oai._2.OAIPMHerrorcodeType.BAD_ARGUMENT;
import static org.openarchives.oai._2.OAIPMHerrorcodeType.BAD_VERB;
import static org.openarchives.oai._2.VerbType.LIST_IDENTIFIERS;

import java.io.UnsupportedEncodingException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import javax.xml.bind.JAXBException;

import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.apache.log4j.Logger;
import org.folio.edge.core.utils.ApiKeyUtils;
import org.folio.edge.core.utils.test.TestUtils;
import org.folio.edge.oaipmh.utils.Constants;
import org.folio.edge.oaipmh.utils.OaiPmhMockOkapi;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.openarchives.oai._2.OAIPMH;
import org.openarchives.oai._2.OAIPMHerrorType;
import org.openarchives.oai._2.OAIPMHerrorcodeType;
import org.openarchives.oai._2.RequestType;
import org.openarchives.oai._2.VerbType;

import com.jayway.restassured.RestAssured;
import com.jayway.restassured.config.DecoderConfig;
import com.jayway.restassured.response.Header;
import com.jayway.restassured.response.Response;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

@RunWith(VertxUnitRunner.class)
public class OaiPmhHandlerTest {

  private static final Logger logger = Logger.getLogger(OaiPmhHandlerTest.class);

  private static final String API_KEY = ApiKeyUtils.generateApiKey(10, "diku", "user");
  private static final String ILLEGAL_API_KEY = "eyJzIjoiYmJaUnYyamt2ayIsInQiOiJkaWt1IiwidSI6ImRpa3VfYSJ9";
  private static final String BAD_API_KEY = "ZnMwMDAwMDAwMA==0000";

  private static final String INVALID_API_KEY_EXPECTED_RESPONSE_BODY = "Invalid API Key: ZnMwMDAwMDAwMA==0000";
  private static final String TEXT_XML = "text/xml";

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
    mockOkapi.setModConfigurationErrorsProcessingValue("500");
    mockOkapi.setModConfigurationEnableOaiServiceValue("true");
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

  @After
  public void tearDown(){
    mockOkapi.setModConfigurationErrorsProcessingValue("500");
    mockOkapi.setModConfigurationEnableOaiServiceValue("true");
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
      mockOkapi.close(context);
    });
  }

  @Test
  public void testAdminHealth() {
    logger.info("=== Test the health check endpoint ===");

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
    logger.info("=== Test GetRecord OAI-PMH error - not found (HTTP GET)===");

    Path expectedMockPath = Paths.get(OaiPmhMockOkapi.PATH_TO_GET_RECORDS_ERROR_MOCK);
    String expectedMockBody = OaiPmhMockOkapi.getOaiPmhResponseAsXml(expectedMockPath);

    final Response resp = RestAssured
      .get(String.format("/oai?verb=GetRecord" +
        "&identifier=oai:arXiv.org:quant-ph/02131001&metadataPrefix=oai_dc&apikey=%s", API_KEY))
      .then()
      .contentType(Constants.TEXT_XML_TYPE)
      .statusCode(HttpStatus.SC_NOT_FOUND)
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

    final Response resp = RestAssured.given()
      .parameters("verb", "GetRecord",
        "identifier", "oai:arXiv.org:quant-ph/02131001",
        "metadataPrefix", "oai_dc",
        "apikey", API_KEY)
      .post("/oai")
      .then()
      .contentType(Constants.TEXT_XML_TYPE)
      .statusCode(HttpStatus.SC_NOT_FOUND)
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

    final Response resp = RestAssured
      .get(String.format("/oai?verb=GetRecord"
        + "&identifier=oai:arXiv.org:cs/0112017&metadataPrefix=oai_dc&apikey=%s", API_KEY))
      .then()
      .contentType(Constants.TEXT_XML_TYPE)
      .statusCode(HttpStatus.SC_OK)
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

    final Response resp = RestAssured.given()
      .parameters("apikey", API_KEY,
        "verb", "GetRecord",
        "metadataPrefix", "oai_dc",
        "identifier", "oai:arXiv.org:cs/0112017")
      .post("/oai")
      .then()
      .contentType(Constants.TEXT_XML_TYPE)
      .statusCode(HttpStatus.SC_OK)
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

    final Response resp = RestAssured
      .get(String.format("/oai?verb=Identify&apikey=%s", API_KEY))
      .then()
      .contentType(Constants.TEXT_XML_TYPE)
      .statusCode(HttpStatus.SC_OK)
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

    final Response resp = RestAssured.given()
      .parameters("apikey", API_KEY,
        "verb", "Identify")
      .post("/oai")
      .then()
      .contentType(Constants.TEXT_XML_TYPE)
      .statusCode(HttpStatus.SC_OK)
      .header(HttpHeaders.CONTENT_TYPE, Constants.TEXT_XML_TYPE)
      .extract()
      .response();

    String actualBody = resp.body().asString();
    assertEquals(expectedMockBody, actualBody);
  }

  @Test
  public void testIdentifyBadApiKeyHttpGet() {
    logger.info("=== Test bad apikey Identify OAI-PMH (HTTP GET) ===");

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
    logger.info("=== Test ability to provide the api key on the path ===");

    Path expectedMockPath = Paths.get(OaiPmhMockOkapi.PATH_TO_IDENTIFY_MOCK);
    String expectedMockBody = OaiPmhMockOkapi.getOaiPmhResponseAsXml(expectedMockPath);

    final Response resp = RestAssured
      .get(String.format("/oai/%s?verb=Identify", API_KEY))
      .then()
      .contentType(Constants.TEXT_XML_TYPE)
      .statusCode(HttpStatus.SC_OK)
      .header(HttpHeaders.CONTENT_TYPE, Constants.TEXT_XML_TYPE)
      .extract()
      .response();

    String actualBody = resp.body().asString();
    assertEquals(expectedMockBody, actualBody);
  }

  @Test
  public void testIdentifyAccessDeniedApiKeyHttpGet() {
    logger.info("=== Test Access Denied apikey Identify OAI-PMH (HTTP POST) ===");

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
    logger.info("=== Test bad apikey GetRecord OAI-PMH (HTTP POST) ===");

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
    logger.info("=== Test exceptional GetRecord OAI-PMH (HTTP GET) ===");

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
    logger.info("=== Test timeout GetRecord OAI-PMH (HTTP GET) ===");

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
  public void testValidateBadVerb() throws UnsupportedEncodingException, JAXBException {
    logger.info("=== Test validate w/ invalid verb ===");

    final Response resp = RestAssured
      .get("/oai?verb=nastyVerb&apikey=" + API_KEY)
      .then()
      .contentType(TEXT_XML)
      .statusCode(HttpStatus.SC_BAD_REQUEST)
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
      .get("/oai?verb=ListIdentifiers&metadataPrefix=oai_dc&from=2002-05-21&extraParam=Test&apikey=" + API_KEY)
      .then()
      .contentType(TEXT_XML)
      .statusCode(HttpStatus.SC_BAD_REQUEST)
      .extract()
      .response();

    OAIPMH expectedResp = buildOAIPMHErrorResponse(LIST_IDENTIFIERS, BAD_ARGUMENT,
        "Verb 'ListIdentifiers', illegal argument: extraParam");
    expectedResp.getRequest()
                .withMetadataPrefix("oai_dc")
                .withFrom("2002-05-21");
    String expectedRespStr = ResponseHelper.getInstance().writeToString(expectedResp);

    verifyResponse(expectedRespStr, resp.body().asString());
  }

  @Test
  public void testValidateExclusiveParam() throws UnsupportedEncodingException, JAXBException {
    logger.info("=== Test validate w/ exclusive and other params ===");

    final Response resp = RestAssured
      .get("/oai?verb=ListIdentifiers&resumptionToken=123456789&metadataPrefix=oai_dc&apikey=" + API_KEY)
      .then()
      .contentType(TEXT_XML)
      .statusCode(HttpStatus.SC_BAD_REQUEST)
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
      .get("/oai?verb=ListIdentifiers&metadataPrefix=oai_dc&from=2002-05-01T14:15:00&until=2002-05-02T14:15:00.000Z&apikey=" + API_KEY)
      .then()
      .contentType(TEXT_XML)
      .statusCode(HttpStatus.SC_BAD_REQUEST)
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
      .get("/oai?verb=ListIdentifiers&from=2002-05-01T14:15:00Z&apikey=" + API_KEY)
      .then()
      .contentType(TEXT_XML)
      .statusCode(HttpStatus.SC_BAD_REQUEST)
      .extract()
      .response();

    OAIPMH expectedResp = buildOAIPMHErrorResponse(LIST_IDENTIFIERS, BAD_ARGUMENT,
      "Missing required parameter: metadataPrefix");
    expectedResp.getRequest()
                .withFrom("2002-05-01T14:15:00Z");
    String expectedRespStr = ResponseHelper.getInstance().writeToString(expectedResp);

    verifyResponse(expectedRespStr, resp.body().asString());
  }

  @Test
  public void testCompressionAlgorithms() {
    logger.info("=== Test response compression ===");

    Path expectedMockPath = Paths.get(OaiPmhMockOkapi.PATH_TO_GET_RECORDS_MOCK);
    String expectedMockBody = OaiPmhMockOkapi.getOaiPmhResponseAsXml(expectedMockPath);

    for (DecoderConfig.ContentDecoder type : DecoderConfig.ContentDecoder.values()) {
      final Response resp = RestAssured.given()
        .config(RestAssured.config().decoderConfig(decoderConfig().contentDecoders(type)))
        .get(String.format("/oai?verb=GetRecord&identifier=oai:arXiv.org:cs/0112017&metadataPrefix=oai_dc&apikey=%s", API_KEY))
        .then()
        .contentType(Constants.TEXT_XML_TYPE)
        .statusCode(HttpStatus.SC_OK)
        .header(HttpHeaders.CONTENT_TYPE, Constants.TEXT_XML_TYPE)
        .header(HttpHeaders.CONTENT_ENCODING, type.name().toLowerCase())
        .extract()
        .response();
      assertEquals(expectedMockBody, resp.body().asString());
    }
  }

  @Test
  public void testNoCompression() {
    logger.info("=== Test no response compression  ===");

    Path expectedMockPath = Paths.get(OaiPmhMockOkapi.PATH_TO_GET_RECORDS_MOCK);
    String expectedMockBody = OaiPmhMockOkapi.getOaiPmhResponseAsXml(expectedMockPath);
    final Response resp = RestAssured.given()
      .config(RestAssured.config().decoderConfig(decoderConfig().noContentDecoders()))
      .header(new Header(HttpHeaders.ACCEPT_ENCODING, "instance"))
      .get(String.format("/oai?verb=GetRecord&identifier=oai:arXiv.org:cs/0112017&metadataPrefix=oai_dc&apikey=%s", API_KEY))
      .then()
      .contentType(Constants.TEXT_XML_TYPE)
      .statusCode(HttpStatus.SC_OK)
      .header(HttpHeaders.CONTENT_TYPE, Constants.TEXT_XML_TYPE)
      .extract()
      .response();

    assertFalse(resp.headers().asList().stream()
      .collect(Collectors.toMap(Header::getName, Header::getValue)).containsKey(HttpHeaders.ACCEPT_ENCODING));

    String actualBody = resp.body().asString();
    assertEquals(expectedMockBody, actualBody);
  }

  @Test
  public void testAcceptHeader() {
    logger.info("=== Test handling of the Accept header ===");

    Path expectedMockPath = Paths.get(OaiPmhMockOkapi.PATH_TO_GET_RECORDS_MOCK);
    String expectedMockBody = OaiPmhMockOkapi.getOaiPmhResponseAsXml(expectedMockPath);

    final Response resp = RestAssured
      .given()
      .header(HttpHeaders.ACCEPT, TEXT_XML)
      .get(String.format("/oai?verb=GetRecord"
        + "&identifier=oai:arXiv.org:cs/0112017&metadataPrefix=oai_dc&apikey=%s", API_KEY))
      .then()
      .log().all()
      .contentType(Constants.TEXT_XML_TYPE)
      .statusCode(HttpStatus.SC_OK)
      .header(HttpHeaders.CONTENT_TYPE, Constants.TEXT_XML_TYPE)
      .extract()
      .response();

    String actualBody = resp.body().asString();
    assertEquals(expectedMockBody, actualBody);
  }

  @Test
  public void testNoAcceptHeader() {
    logger.info("=== Test handling of the Accept header ===");

    Path expectedMockPath = Paths.get(OaiPmhMockOkapi.PATH_TO_GET_RECORDS_MOCK);
    String expectedMockBody = OaiPmhMockOkapi.getOaiPmhResponseAsXml(expectedMockPath);

    final Response resp = RestAssured
      .get(String.format("/oai?verb=GetRecord"
        + "&identifier=oai:arXiv.org:cs/0112017&metadataPrefix=oai_dc&apikey=%s", API_KEY))
      .then()
      .log().all()
      .contentType(Constants.TEXT_XML_TYPE)
      .statusCode(HttpStatus.SC_OK)
      .header(HttpHeaders.CONTENT_TYPE, Constants.TEXT_XML_TYPE)
      .extract()
      .response();

    String actualBody = resp.body().asString();
    assertEquals(expectedMockBody, actualBody);
  }

  @Test
  public void testAcceptHeaderHasUnsupportedType() {
    logger.info("=== Test handling of unsupported type in Accept header ===");

    String unsupportedAcceptType = "application/json";

    final Response resp = RestAssured
      .given()
      .header(HttpHeaders.ACCEPT, unsupportedAcceptType)
      .get(String.format("/oai?verb=GetRecord"
        + "&identifier=oai:arXiv.org:cs/0112017&metadataPrefix=oai_dc&apikey=%s", API_KEY))
      .then()
      .log().all()
      .contentType(Constants.TEXT_XML_TYPE)
      .statusCode(HttpStatus.SC_NOT_ACCEPTABLE)
      .header(HttpHeaders.CONTENT_TYPE, Constants.TEXT_XML_TYPE)
      .extract()
      .response();

    String actualBody = resp.body().asString();
    String expectedBody = "Accept header must be \"text/xml\" for this request, but it is " +"\""+ unsupportedAcceptType
      +"\""+", can not send */*";
    assertEquals(expectedBody, actualBody);
  }

  @Test
  public void testAcceptHeaderIsAbsent() {
    logger.info("=== Test Accept header is absent ===");

    Path expectedMockPath = Paths.get(OaiPmhMockOkapi.PATH_TO_GET_RECORDS_MOCK);
    String expectedMockBody = OaiPmhMockOkapi.getOaiPmhResponseAsXml(expectedMockPath);

    final Response resp = RestAssured
      .given()
      .header(HttpHeaders.ACCEPT_CHARSET, "utf-8")
      .get(String.format("/oai?verb=GetRecord"
        + "&identifier=oai:arXiv.org:cs/0112017&metadataPrefix=oai_dc&apikey=%s", API_KEY))
      .then()
      .log().all()
      .contentType(Constants.TEXT_XML_TYPE)
      .statusCode(HttpStatus.SC_OK)
      .header(HttpHeaders.CONTENT_TYPE, Constants.TEXT_XML_TYPE)
      .extract()
      .response();

    String actualBody = resp.body().asString();
    assertEquals(expectedMockBody, actualBody);
  }

  @Test
  public void testAcceptHeaderIsEmpty() {
    logger.info("=== Test Accept header is empty ===");

    Path expectedMockPath = Paths.get(OaiPmhMockOkapi.PATH_TO_GET_RECORDS_MOCK);
    String expectedMockBody = OaiPmhMockOkapi.getOaiPmhResponseAsXml(expectedMockPath);

    final Response resp = RestAssured
      .given()
      .header(HttpHeaders.ACCEPT, "*/*")
      .get(String.format("/oai?verb=GetRecord"
        + "&identifier=oai:arXiv.org:cs/0112017&metadataPrefix=oai_dc&apikey=%s", API_KEY))
      .then()
      .log().all()
      .contentType(Constants.TEXT_XML_TYPE)
      .statusCode(HttpStatus.SC_OK)
      .header(HttpHeaders.CONTENT_TYPE, Constants.TEXT_XML_TYPE)
      .extract()
      .response();

    String actualBody = resp.body().asString();
    assertEquals(expectedMockBody, actualBody);
  }

  @Test
  public void testAcceptHeaderHasAllTextSybtypesSymbol() {
    logger.info("=== Test Accept header has all text sybtypes symbol ===");

    Path expectedMockPath = Paths.get(OaiPmhMockOkapi.PATH_TO_GET_RECORDS_MOCK);
    String expectedMockBody = OaiPmhMockOkapi.getOaiPmhResponseAsXml(expectedMockPath);

    final Response resp = RestAssured
      .given()
      .header(HttpHeaders.ACCEPT, "text/*")
      .get(String.format("/oai?verb=GetRecord"
        + "&identifier=oai:arXiv.org:cs/0112017&metadataPrefix=oai_dc&apikey=%s", API_KEY))
      .then()
      .log().all()
      .contentType(Constants.TEXT_XML_TYPE)
      .statusCode(HttpStatus.SC_OK)
      .header(HttpHeaders.CONTENT_TYPE, Constants.TEXT_XML_TYPE)
      .extract()
      .response();

    String actualBody = resp.body().asString();
    assertEquals(expectedMockBody, actualBody);
  }

  @Test
  public void testAcceptHeaderHasAllTextSybtypesSymbolWithParameterAndWithUnsupportedTypes() {
    logger.info("=== Test Accept header has all text sybtypes symbol with parameter and with unsupported types ===");

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
      .contentType(Constants.TEXT_XML_TYPE)
      .statusCode(HttpStatus.SC_OK)
      .header(HttpHeaders.CONTENT_TYPE, Constants.TEXT_XML_TYPE)
      .extract()
      .response();

    String actualBody = resp.body().asString();
    assertEquals(expectedMockBody, actualBody);
  }

  @Test
  public void testAcceptHeaderHasTextTypeXMLAndSomeUnsupportedTypes() {
    logger.info("=== Test Accept header has text type XML and some unsupported types ===");

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
      .contentType(Constants.TEXT_XML_TYPE)
      .statusCode(HttpStatus.SC_OK)
      .header(HttpHeaders.CONTENT_TYPE, Constants.TEXT_XML_TYPE)
      .extract()
      .response();

    String actualBody = resp.body().asString();
    assertEquals(expectedMockBody, actualBody);
  }

  @Test
  public void testAcceptHeaderHasAllTypesAndAllSubtypesSymbolAndSomeUnsupportedTypes() {
    logger.info("=== Test Accept header has all types and all subtypes symbol and some unsupported types ===");

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
      .contentType(Constants.TEXT_XML_TYPE)
      .statusCode(HttpStatus.SC_OK)
      .header(HttpHeaders.CONTENT_TYPE, Constants.TEXT_XML_TYPE)
      .extract()
      .response();

    String actualBody = resp.body().asString();
    assertEquals(expectedMockBody, actualBody);
  }

  @Test
  public void testAcceptHeaderHasAllTypesAndAllSubtypesSymbolAndAllTextSubtypesSymbolAndSomeUnsupportedTypes() {
    logger.info("=== Test Accept header has all types and all subtypes symbol and all text subtypes symbol and some unsupported types ===");

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
      .contentType(Constants.TEXT_XML_TYPE)
      .statusCode(HttpStatus.SC_OK)
      .header(HttpHeaders.CONTENT_TYPE, Constants.TEXT_XML_TYPE)
      .extract()
      .response();

    String actualBody = resp.body().asString();
    assertEquals(expectedMockBody, actualBody);
  }

  @Test
  public void testAcceptHeaderHasOnlyUnsupportedTypesWithParameter() {
    logger.info("=== Accept header has only unsupported types with parameter ===");

    String acceptHeader = "text/plain; q=0.5, text/html";

    final Response resp = RestAssured
      .given()
      .header(HttpHeaders.ACCEPT, acceptHeader)
      .get(String.format("/oai?verb=GetRecord"
        + "&identifier=oai:arXiv.org:cs/0112017&metadataPrefix=oai_dc&apikey=%s", API_KEY))
      .then()
      .log().all()
      .contentType(Constants.TEXT_XML_TYPE)
      .statusCode(HttpStatus.SC_NOT_ACCEPTABLE)
      .header(HttpHeaders.CONTENT_TYPE, Constants.TEXT_XML_TYPE)
      .extract()
      .response();

    String actualBody = resp.body().asString();
    String expectedBody = "Accept header must be \"text/xml\" for this request, but it is " +"\""+ acceptHeader
      +"\""+", can not send */*";
    assertEquals(expectedBody, actualBody);
  }

  @Test
  public void testAllRequestsReturnErrorsWith200HttpCode() {
    logger.info("=== Test case when all errors return 200 Http code ===");

    mockOkapi.setModConfigurationErrorsProcessingValue("200");

    String[] invalidURLs = {"/oai/" + API_KEY + "?verb=ListRecords", "/oai/" + API_KEY + "?verb=ListRecord",
      "/oai/" + API_KEY + "?verb=ListRecords" + "&resumptionToken=bWV0YWRhdGFQcmVmaXg9bWFyYzIxJmZyb209MjAyMC0wNC0wOVQxMjoyMjo",
      "/oai?verb=GetRecord" + "&identifier=oai:arXiv.org:test-env/98765400&metadataPrefix=oai&apikey=" + API_KEY,
      "/oai/" + API_KEY + "?verb=ListRecords&metadataPrefix=marc21&from=2020-04-15T00:00:00Z",
      "/oai?verb=GetRecord" + "&identifier=oai:arXiv.org:quant-ph/02131001&metadataPrefix=oai_dc&apikey=" + API_KEY
    };

    for (String invalidURL : invalidURLs) {
      RestAssured
        .get(invalidURL)
        .then()
        .log().all()
        .statusCode(HttpStatus.SC_OK);
    }
    //fix jenkins code smells
    assert true;
  }

  @Test
  public void testAllRequestsReturnErrorsWithTheirOriginalHttpCode() {
    logger.info("=== Test case when all errors return 4xx Http code ===");

    String[] invalidURLs = {"/oai/" + API_KEY + "?verb=ListRecords", "/oai/" + API_KEY + "?verb=ListRecord",
      "/oai/" + API_KEY + "?verb=ListRecords" + "&resumptionToken=bWV0YWRhdGFQcmVmaXg9bWFyYzIxJmZyb209MjAyMC0wNC0wOVQxMjoyMjo",
      "/oai?verb=GetRecord" + "&identifier=oai:arXiv.org:test-env/98765400&metadataPrefix=oai&apikey=" + API_KEY,
      "/oai/" + API_KEY + "?verb=ListRecords&metadataPrefix=marc21&from=2020-04-15T00:00:00Z",
      "/oai?verb=GetRecord" + "&identifier=oai:arXiv.org:quant-ph/02131001&metadataPrefix=oai_dc&apikey=" + API_KEY
    };

    int[] httpStatuses = {HttpStatus.SC_BAD_REQUEST, HttpStatus.SC_BAD_REQUEST, HttpStatus.SC_BAD_REQUEST,
      HttpStatus.SC_UNPROCESSABLE_ENTITY, HttpStatus.SC_NOT_FOUND, HttpStatus.SC_NOT_FOUND};

    for (int i=0; i < invalidURLs.length; ++i) {
      RestAssured
        .get(invalidURLs[i])
        .then()
        .log().all()
        .statusCode(httpStatuses[i]);
    }
    //fix jenkins code smells
    assert true;
  }

  @Test
  public void testInvalidAcceptHeaderReturns406IrrespectiveOfErrorsProcessingSetting() {
    mockOkapi.setModConfigurationErrorsProcessingValue("200");

    String url = "/oai/" + API_KEY + "?verb=ListRecords";
    RestAssured
      .given()
      .header(HttpHeaders.ACCEPT, "text/json")
      .get(url)
      .then()
      .log().all()
      .statusCode(HttpStatus.SC_NOT_ACCEPTABLE);
    //fix jenkins code smells
    assert true;
  }

  @Test
  public void testStatusCodeInResponseNotEquals200() {
    logger.info("=== Test status code in mod-configuration response not equals 200 ===");

    mockOkapi.setModConfigurationErrorsProcessingValue("");

    String[] invalidURLs = {"/oai/" + API_KEY + "?verb=ListRecords", "/oai/" + API_KEY + "?verb=ListRecord",
      "/oai/" + API_KEY + "?verb=ListRecords" + "&resumptionToken=bWV0YWRhdGFQcmVmaXg9bWFyYzIxJmZyb209MjAyMC0wNC0wOVQxMjoyMjo",
      "/oai?verb=GetRecord" + "&identifier=oai:arXiv.org:test-env/98765400&metadataPrefix=oai&apikey=" + API_KEY,
      "/oai/" + API_KEY + "?verb=ListRecords&metadataPrefix=marc21&from=2020-04-15T00:00:00Z",
      "/oai?verb=GetRecord" + "&identifier=oai:arXiv.org:quant-ph/02131001&metadataPrefix=oai_dc&apikey=" + API_KEY
    };

    int[] httpStatuses = {HttpStatus.SC_BAD_REQUEST, HttpStatus.SC_BAD_REQUEST, HttpStatus.SC_BAD_REQUEST,
      HttpStatus.SC_UNPROCESSABLE_ENTITY, HttpStatus.SC_NOT_FOUND, HttpStatus.SC_NOT_FOUND};

    for (int i=0; i < invalidURLs.length; ++i) {
      RestAssured
        .get(invalidURLs[i])
        .then()
        .log().all()
        .statusCode(httpStatuses[i]);
    }
    //fix jenkins code smells
    assert true;
}

  @Test
  public void testMakeRequestAndGetResponseWithEmptyBody(){
    logger.info("=== Test make request and give response with empty body ===");

    mockOkapi.setModConfigurationErrorsProcessingValue("emptyBody");

    final Response resp = RestAssured
      .get("/oai/" + API_KEY + "?verb=ListRecords")
      .then()
      .log().all()
      .statusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR)
      .extract()
      .response();

    String actualBody = resp.body().asString();
    assertTrue(actualBody.contains("Exception"));

  }

  @Test
  public void testEnableOaiServiceConfigSettingIsFalse(){
    logger.info("=== Test case when enableOaiService config setting is false ===");

    mockOkapi.setModConfigurationEnableOaiServiceValue("false");

    final Response resp = RestAssured
      .get(String.format("/oai?verb=GetRecord"
        + "&identifier=oai:arXiv.org:cs/0112017&metadataPrefix=oai_dc&apikey=%s", API_KEY))
      .then()
      .log().all()
      .statusCode(HttpStatus.SC_SERVICE_UNAVAILABLE)
      .extract()
      .response();

    String expectedBody = "OAI-PMH service is disabled";

    String actualBody = resp.body().asString();
    assertEquals(expectedBody, actualBody);
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
