package org.folio.edge.oaipmh.utils;

import static org.folio.edge.core.Constants.SYS_OKAPI_URL;
import static org.folio.edge.core.Constants.SYS_REQUEST_TIMEOUT_MS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.vertx.core.Vertx;

import io.vertx.core.json.JsonObject;
import org.folio.edge.core.utils.OkapiClient;
import org.folio.edge.core.utils.OkapiClientFactory;
import org.folio.edge.oaipmh.clients.OaiPmhOkapiClientFactory;
import org.junit.jupiter.api.Test;

class OaiPmhOkapiClientFactoryTest {

  @Test
  void testGetOkapiClient() {
    Vertx vertx = Vertx.vertx();
    int reqTimeout = 5000;
    JsonObject config = new JsonObject()
      .put(SYS_OKAPI_URL, "http://mocked.okapi:9130")
      .put(SYS_REQUEST_TIMEOUT_MS, reqTimeout);
    OkapiClientFactory ocf = OaiPmhOkapiClientFactory.createInstance(vertx, config);
    OkapiClient client = ocf.getOkapiClient("tenant");
    assertNotNull(client);
    assertEquals(reqTimeout, client.reqTimeout);
  }

  @Test
  void testGetConsortiaTenantClient() {
    Vertx vertx = Vertx.vertx();
    int reqTimeout = 5000;
    JsonObject config = new JsonObject()
      .put(SYS_OKAPI_URL, "http://mocked.okapi:9130")
      .put(SYS_REQUEST_TIMEOUT_MS, reqTimeout);
    var client = OaiPmhOkapiClientFactory.getConsortiaTenantClient("tenant", vertx, config);
    assertNotNull(client);
  }
}
