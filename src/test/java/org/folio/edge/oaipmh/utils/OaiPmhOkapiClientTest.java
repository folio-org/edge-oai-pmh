package org.folio.edge.oaipmh.utils;

import static org.junit.Assert.assertEquals;

import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.HttpStatus;
import org.apache.log4j.Logger;
import org.folio.edge.core.utils.test.TestUtils;
import org.folio.edge.oaipmh.clients.aoipmh.OaiPmhOkapiClient;
import org.folio.edge.oaipmh.clients.aoipmh.OaiPmhOkapiClientFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.openarchives.oai._2.VerbType;

@Slf4j
@RunWith(VertxUnitRunner.class)
public class OaiPmhOkapiClientTest {

  private static final String TENANT = "diku";
  private static final long REQUEST_TIMEOUT = 3000L;

  private OaiPmhOkapiClient client;
  private OaiPmhMockOkapi mockOkapi;

  @Before
  public void setUp(TestContext context) {
    int okapiPort = TestUtils.getPort();

    List<String> knownTenants = new ArrayList<>();
    knownTenants.add(TENANT);

    mockOkapi = new OaiPmhMockOkapi(okapiPort, knownTenants);
    mockOkapi.start(context);


    client = new OaiPmhOkapiClientFactory(Vertx.vertx(),
      "http://localhost:" + okapiPort, REQUEST_TIMEOUT)
      .getOaiPmhOkapiClient(TENANT);
  }

  @After
  public void tearDown(TestContext context) {
    client.close();
    mockOkapi.close(context);
  }

  @Test
  public void testGetRecord(TestContext context) {
    log.info("=== Test successful OAI-PMH Request ===");

    String expectedBody
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

    processRequest(context, parameters, headers, HttpStatus.SC_OK, expectedBody);
  }

  @Test
  public void testGetRecordError(TestContext context) {
    log.info("=== Test error GetRecord OAI-PMH request ===");

    String expectedBody
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

    processRequest(context, parameters, headers, HttpStatus.SC_NOT_FOUND, expectedBody);
  }

  @Test
  public void testIdentify(TestContext context) {
    log.info("=== Test Identify OAI-PMH request ===");

    String expectedBody
      = OaiPmhMockOkapi.getOaiPmhResponseAsXml(
      Paths.get(OaiPmhMockOkapi.PATH_TO_IDENTIFY_MOCK)
    );

    // Request parameters with unknown identifier
    MultiMap parameters = MultiMap.caseInsensitiveMultiMap();
    parameters.add(Constants.VERB, VerbType.IDENTIFY.value());

    // Request headers - empty
    MultiMap headers = MultiMap.caseInsensitiveMultiMap();

    processRequest(context, parameters, headers, HttpStatus.SC_OK, expectedBody);
  }

  private void processRequest(TestContext context, MultiMap parameters, MultiMap headers,
    int expectedHttpStatusCode, String expected) {
    Async async = context.async();
    client.login("admin", "password")
      .thenAcceptAsync(v -> client.call(parameters, headers,
        response -> {
          context.assertEquals(expectedHttpStatusCode, response.statusCode());
          response.bodyHandler(buffer -> {
            final StringBuilder body = new StringBuilder();
            body.append(buffer);
            log.info("oai-pmh-mod response body: " + body);
            assertEquals(expected, body.toString());
          });
          async.complete();
        },
        t -> context.fail(t.getMessage())
        )
      );
  }
}
