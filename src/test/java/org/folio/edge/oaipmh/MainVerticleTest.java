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

import javax.xml.bind.JAXBException;
import java.io.UnsupportedEncodingException;

import static org.folio.edge.core.Constants.SYS_PORT;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.openarchives.oai._2.OAIPMHerrorcodeType.BAD_ARGUMENT;
import static org.openarchives.oai._2.OAIPMHerrorcodeType.BAD_VERB;

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

    OAIPMH expectedResp = new OAIPMH()
      .withErrors(new OAIPMHerrorType()
        .withCode(BAD_VERB)
        .withValue("Bad verb. Verb 'nastyVerb' is not implemented"));

    assertEquals(ResponseHelper.getInstance().writeToString(expectedResp), resp.body().asString());
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

    OAIPMH expectedResp = new OAIPMH()
      .withErrors(new OAIPMHerrorType()
        .withCode(BAD_ARGUMENT)
        .withValue("Verb 'ListIdentifiers', illegal argument: extraParam"));

    assertEquals(ResponseHelper.getInstance().writeToString(expectedResp), resp.body().asString());
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

    OAIPMH expectedResp = new OAIPMH()
      .withErrors(new OAIPMHerrorType()
        .withCode(BAD_ARGUMENT)
        .withValue("Verb 'ListIdentifiers', argument 'resumptionToken' is exclusive, " +
          "no others maybe specified with it."));

    assertEquals(ResponseHelper.getInstance().writeToString(expectedResp), resp.body().asString());
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

    OAIPMH expectedResp = new OAIPMH()
      .withErrors(new OAIPMHerrorType()
        .withCode(BAD_ARGUMENT)
        .withValue("Bad datestamp format for 'from' argument."))
      .withErrors(new OAIPMHerrorType()
        .withCode(BAD_ARGUMENT)
        .withValue("Bad datestamp format for 'until' argument."));

    assertEquals(ResponseHelper.getInstance().writeToString(expectedResp), resp.body().asString());
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

    OAIPMH expectedResp = new OAIPMH()
      .withErrors(new OAIPMHerrorType()
        .withCode(BAD_ARGUMENT)
        .withValue("Missing required parameter: metadataPrefix"));

    assertEquals(ResponseHelper.getInstance().writeToString(expectedResp), resp.body().asString());
  }
}
