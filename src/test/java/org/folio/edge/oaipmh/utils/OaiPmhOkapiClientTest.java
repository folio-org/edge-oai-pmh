package org.folio.edge.oaipmh.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpStatus;
import org.folio.edge.core.utils.OkapiClientFactory;
import org.folio.edge.core.utils.test.TestUtils;
import org.folio.edge.oaipmh.clients.OaiPmhOkapiClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.openarchives.oai._2.VerbType;

@Slf4j
@ExtendWith(VertxExtension.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OaiPmhOkapiClientTest {

  private static final String TENANT = "diku";
  private static final int REQUEST_TIMEOUT = 3000;

  private OaiPmhOkapiClient client;
  private OaiPmhMockOkapi mockOkapi;

  @BeforeEach
  void setUp(Vertx vertx, VertxTestContext context) {
    int okapiPort = TestUtils.getPort();

    List<String> knownTenants = new ArrayList<>();
    knownTenants.add(TENANT);

    client = new OaiPmhOkapiClient(new OkapiClientFactory(Vertx.vertx(),
      "http://localhost:" + okapiPort, REQUEST_TIMEOUT).getOkapiClient(TENANT));

    mockOkapi = new OaiPmhMockOkapi(vertx, okapiPort, knownTenants);
    mockOkapi.start(context);
  }

  @AfterEach
  void tearDown(Vertx vertx, VertxTestContext context) {
    log.info("Shutting down server");
    vertx.close(res -> {
      if (res.succeeded()) {
        log.info("Successfully shut down edge-oai-pmh server");
        context.completeNow();
      } else {
        log.error("Failed to shut down edge-oai-pmh server", res.cause());
        context.failNow(res.cause().getMessage());
      }
    });
  }

  @Test
  void testGetRecord(VertxTestContext context) {
    log.info("=== Test successful OAI-PMH Request ===");

    // Request parameters
    MultiMap parameters = MultiMap.caseInsensitiveMultiMap();
    parameters.add(Constants.VERB, VerbType.GET_RECORD.value());
    parameters.add(Constants.IDENTIFIER, "oai:arXiv.org:cs/0112017");
    parameters.add(Constants.METADATA_PREFIX, "oai_dc");

    // Request headers - empty
    MultiMap headers = MultiMap.caseInsensitiveMultiMap();

    String expectedBody
          = OaiPmhMockOkapi.getOaiPmhResponseAsXml(
          Paths.get(OaiPmhMockOkapi.PATH_TO_GET_RECORDS_MOCK)
    );

    processRequest(context, parameters, headers, HttpStatus.SC_OK, expectedBody);
  }

  @Test
  void testGetRecordError(VertxTestContext context) {
    log.info("=== Test error GetRecord OAI-PMH request ===");

    // Request parameters with unknown identifier
    MultiMap parameters = MultiMap.caseInsensitiveMultiMap();
    parameters.add(Constants.VERB, VerbType.GET_RECORD.value());
    parameters.add(Constants.IDENTIFIER, "oai:arXiv.org:quant-ph/02131001");
    parameters.add(Constants.METADATA_PREFIX, "oai_dc");

    // Request headers - empty
    MultiMap headers = MultiMap.caseInsensitiveMultiMap();

    String expectedBody
          = OaiPmhMockOkapi.getOaiPmhResponseAsXml(
          Paths.get(OaiPmhMockOkapi.PATH_TO_GET_RECORDS_ERROR_MOCK)
    );

    processRequest(context, parameters, headers, HttpStatus.SC_NOT_FOUND, expectedBody);
  }

  @Test
  void testIdentify(VertxTestContext context) {
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

  private void processRequest(VertxTestContext context, MultiMap parameters, MultiMap headers,
      int expectedHttpStatusCode, String expected) {
    client.login("admin", "password")
          .thenAcceptAsync(v -> client.call(parameters, headers,
                response -> {
              assertEquals(expectedHttpStatusCode, response.statusCode());
              String body = response.bodyAsString();
              log.info("oai-pmh-mod response body: " + body);
              assertEquals(expected, body);
              context.completeNow();
            },
                      t -> context.failNow(t.getMessage())
        ));
  }
}
