package org.folio.edge.oaipmh;

import com.jayway.restassured.RestAssured;
import com.jayway.restassured.response.Response;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.apache.log4j.Logger;
import org.folio.edge.core.utils.test.TestUtils;
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

import static org.folio.edge.core.Constants.SYS_PORT;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.openarchives.oai._2.OAIPMHerrorcodeType.BAD_ARGUMENT;
import static org.openarchives.oai._2.OAIPMHerrorcodeType.BAD_VERB;
import static org.openarchives.oai._2.VerbType.LIST_IDENTIFIERS;

@RunWith(VertxUnitRunner.class)
public class MainVerticleTest {

  private static final Logger logger = Logger.getLogger(MainVerticleTest.class);

  private static final String apiKey = "Z1luMHVGdjNMZl9kaWt1X2Rpa3U=";

  private static Vertx vertx;

  @BeforeClass
  public static void setUpOnce(TestContext context) {
    int serverPort = TestUtils.getPort();

    vertx = Vertx.vertx();

    System.setProperty(SYS_PORT, String.valueOf(serverPort));

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
    });
  }

  @Test
  public void testValidateBadVerb(TestContext context) throws UnsupportedEncodingException, JAXBException {
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
  public void testValidateIllegalParams(TestContext context) throws UnsupportedEncodingException, JAXBException {
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
    String expectedRespStr = ResponseHelper.getInstance().writeToString(expectedResp);

    verifyResponse(expectedRespStr, resp.body().asString());
  }

  @Test
  public void testValidateExclusiveParam(TestContext context) throws UnsupportedEncodingException, JAXBException {
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
    String expectedRespStr = ResponseHelper.getInstance().writeToString(expectedResp);

    verifyResponse(expectedRespStr, resp.body().asString());
  }

  @Test
  public void testValidateBadFromUntilParams(TestContext context) throws UnsupportedEncodingException, JAXBException {
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
    String expectedRespStr = ResponseHelper.getInstance().writeToString(expectedResp);

    verifyResponse(expectedRespStr, resp.body().asString());
  }

  @Test
  public void testValidateMissingRequiredParams(TestContext context) throws UnsupportedEncodingException, JAXBException {
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
    // Unmarshal string to OAIPMH to remove volatile fields such as ResponseDate
    OAIPMH actualOaiResp = ResponseHelper.getInstance().stringToOaiPmh(actualResponse);
    actualOaiResp.setResponseDate(null);

    actualResponse = ResponseHelper.getInstance().writeToString(actualOaiResp);

    assertEquals(expectedResponse, actualResponse);
  }
}
