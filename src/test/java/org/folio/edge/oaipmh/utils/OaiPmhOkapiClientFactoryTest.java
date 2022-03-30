package org.folio.edge.oaipmh.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.vertx.core.Vertx;

import org.folio.edge.oaipmh.clients.OaiPmhOkapiClient;
import org.folio.edge.oaipmh.clients.OaiPmhOkapiClientFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class OaiPmhOkapiClientFactoryTest {

  private static final int reqTimeout = 5000;

  private OaiPmhOkapiClientFactory ocf;

  @BeforeEach
  public void setUp() {
    Vertx vertx = Vertx.vertx();
    ocf = new OaiPmhOkapiClientFactory(vertx, "http://mocked.okapi:9130", reqTimeout);
  }

  @Test
  public void testGetOkapiClient() {
    OaiPmhOkapiClient client = ocf.getOaiPmhOkapiClient("tenant");
    assertNotNull(client);
    assertEquals(reqTimeout, client.reqTimeout);
  }
}
