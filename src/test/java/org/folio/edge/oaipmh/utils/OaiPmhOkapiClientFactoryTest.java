package org.folio.edge.oaipmh.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import io.vertx.core.Vertx;

import org.folio.edge.oaipmh.clients.OaiPmhOkapiClient;
import org.folio.edge.oaipmh.clients.OaiPmhOkapiClientFactory;
import org.junit.Before;
import org.junit.Test;

public class OaiPmhOkapiClientFactoryTest {

  private static final long reqTimeout = 5000L;

  private OaiPmhOkapiClientFactory ocf;

  @Before
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
