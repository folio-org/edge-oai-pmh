package org.folio.edge.oaipmh.utils;

import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import lombok.extern.slf4j.Slf4j;
import org.folio.edge.core.utils.test.TestUtils;
import org.folio.edge.oaipmh.clients.OaiPmhOkapiClientFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Slf4j
@ExtendWith(VertxExtension.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ConsortiaTenantClientTest {

  private static final String TENANT_DIKU = "diku";
  private static final String TENANT_CENTRAL = "central";
  private static final String TENANT_CONSORTIA = "consortia";
  private static final String TENANT_EMPTY_CONSORTIA = "empty_consortia";
  private static final int REQUEST_TIMEOUT = 3000;

  private OaiPmhOkapiClientFactory factory;
  private OaiPmhMockOkapi mockOkapi;

  @BeforeEach
  void setUp(Vertx vertx, VertxTestContext context) {
    int okapiPort = TestUtils.getPort();

    List<String> knownTenants = new ArrayList<>();
    knownTenants.add(TENANT_DIKU);
    knownTenants.add(TENANT_CENTRAL);
    knownTenants.add(TENANT_CONSORTIA);
    knownTenants.add(TENANT_EMPTY_CONSORTIA);

    factory = new OaiPmhOkapiClientFactory(vertx, "http://localhost:" + okapiPort, REQUEST_TIMEOUT);

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
  void shouldReturnSingleTenantIfUserTenantsResponseIsEmpty(VertxTestContext context) {
    var expectedList = Collections.singletonList(TENANT_DIKU);
    processRequest(context, TENANT_DIKU, MultiMap.caseInsensitiveMultiMap(), expectedList);
  }

  @Test
  void shouldReturnSingleTenantIfConsortiaResponseIsEmpty(VertxTestContext context) {
    var expectedList = Collections.singletonList(TENANT_EMPTY_CONSORTIA);
    processRequest(context, TENANT_EMPTY_CONSORTIA, MultiMap.caseInsensitiveMultiMap(), expectedList);
  }

  @Test
  void shouldReturnSingleTenantIfTenantIsNotCentral(VertxTestContext context) {
    var expectedList = Collections.singletonList(TENANT_CONSORTIA);
    processRequest(context, TENANT_CONSORTIA, MultiMap.caseInsensitiveMultiMap(), expectedList);
  }

  @Test
  void shouldReturnTenantListWithoutCentralIfTenantIsCentral(VertxTestContext context) {
    var expectedList = List.of("tenant1", "tenant2");
    processRequest(context, TENANT_CENTRAL, MultiMap.caseInsensitiveMultiMap(), expectedList);
  }

  private void processRequest(VertxTestContext context, String tenant, MultiMap headers, List<String> expectedList) {
    var client = factory.getConsortiaTenantClient(tenant);
    client.login("admin", "password")
      .thenCompose(v -> client.getConsortiaTenants(headers).toCompletionStage())
      .thenAccept(list -> {
        if (Objects.equals(expectedList, list)) {
          context.completeNow();
        } else {
          context.failNow("Values does not match");
        }
      });
  }
}
