package org.folio.edge.oaipmh.utils;

import static org.folio.edge.core.utils.test.MockOkapi.X_DURATION;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import org.apache.log4j.Logger;
import org.folio.edge.core.utils.test.TestUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.openarchives.oai._2.VerbType;

@RunWith(VertxUnitRunner.class)
public class OaiPmhOkapiClientTest {

  private static final Logger logger = Logger.getLogger(OaiPmhOkapiClientTest.class);

  private static final String tenant = "diku";
  private static final long reqTimeout = 3000L;

  private OaiPmhOkapiClient client;
  private OaiPmhMockOkapi mockOkapi;

  @Before
  public void setUp(TestContext context) {
    int okapiPort = TestUtils.getPort();

    List<String> knownTenants = new ArrayList<>();
    knownTenants.add(tenant);

    mockOkapi = new OaiPmhMockOkapi(okapiPort, knownTenants);
    mockOkapi.start(context);

    client = new OaiPmhOkapiClientFactory(Vertx.vertx(),
      "http://localhost:" + okapiPort, reqTimeout)
      .getOaiPmhOkapiClient(tenant);
  }

  @After
  public void tearDown(TestContext context) {
    client.close();
    mockOkapi.close();
  }

  @Test
  public void testGetRecord(TestContext context) {
    logger.info("=== Test successful OAI-PMH Request ===");

    String expected
      = OaiPmhMockOkapi.getOaiPmhResponseAsXml(
        Paths.get(OaiPmhMockOkapi.PATH_TO_GET_RECORDS_MOCK)
    );

    // Request parameters
    MultiMap parameters = MultiMap.caseInsensitiveMultiMap();
    parameters.add(Constants.VERB, VerbType.GET_RECORD.value());
    parameters.add(Constants.IDENTIFIER, "oai:arXiv.org:cs/0112017");
    parameters.add(Constants.METADATA_PREFIX, "oai_dc");

    // Request headers - empty
    MultiMap headers = MultiMap.caseInsensitiveMultiMap();

    verifyEndpointCall(context, parameters, headers, expected);
  }

  @Test
  public void testGetRecordError(TestContext context) {
    logger.info("=== Test error OAI-PMH request ===");

    String expected
      = OaiPmhMockOkapi.getOaiPmhResponseAsXml(
        Paths.get(OaiPmhMockOkapi.PATH_TO_GET_RECORDS_ERROR_MOCK)
    );

    // Request parameters with unknown identifier
    MultiMap parameters = MultiMap.caseInsensitiveMultiMap();
    parameters.add(Constants.VERB, VerbType.GET_RECORD.value());
    parameters.add(Constants.IDENTIFIER, "oai:arXiv.org:quant-ph/02131001");
    parameters.add(Constants.METADATA_PREFIX, "oai_dc");

    // Request headers - empty
    MultiMap headers = MultiMap.caseInsensitiveMultiMap();

    verifyEndpointCall(context, parameters, headers, expected);
  }

  @Test
  public void testIdentify(TestContext context) {
    logger.info("=== Test error OAI-PMH request ===");

    String expected
      = OaiPmhMockOkapi.getOaiPmhResponseAsXml(
      Paths.get(OaiPmhMockOkapi.PATH_TO_IDENTIFY_MOCK)
    );

    // Request parameters with unknown identifier
    MultiMap parameters = MultiMap.caseInsensitiveMultiMap();
    parameters.add(Constants.VERB, VerbType.IDENTIFY.value());

    // Request headers - empty
    MultiMap headers = MultiMap.caseInsensitiveMultiMap();

    verifyEndpointCall(context, parameters, headers, expected);
  }

  @Test
  public void testOaiPmhTimeout(TestContext context) {
    logger.info("=== Test OAI-PMH timeout ===");

    MultiMap parameters = MultiMap.caseInsensitiveMultiMap();
    parameters.add(Constants.VERB, VerbType.GET_RECORD.value());
    parameters.add(Constants.IDENTIFIER, "oai:arXiv.org:quant-ph/02131001");
    parameters.add(Constants.METADATA_PREFIX, "oai_dc");
    MultiMap headers = MultiMap.caseInsensitiveMultiMap();

    headers.set(X_DURATION, String.valueOf(reqTimeout * 2));
    CompletableFuture<String> future = client.call(parameters, headers);

    try {
      future.get();
      fail("Expected a TimeoutException to be thrown");
    } catch (Exception e) {
      assertEquals(TimeoutException.class, e.getCause().getClass());
    }
  }

  private void verifyEndpointCall(TestContext context, MultiMap parameters, MultiMap headers,
    String expected) {
    Async async = context.async();
    client.login("admin", "password")
      .thenAcceptAsync(v -> client.call(parameters, headers)
        .thenAcceptAsync(body -> {
          logger.info("oai-pmh response body: " + body);
          assertEquals(expected, body);
          async.complete();
        }));
  }
}
