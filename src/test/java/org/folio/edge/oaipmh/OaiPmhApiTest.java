package org.folio.edge.oaipmh;

import static org.folio.edge.oaipmh.TestUtils.specifyRandomRunnerId;

import com.intuit.karate.junit5.Karate;

public class OaiPmhApiTest {

  @Karate.Test
  Karate oaiTest() {
    specifyRandomRunnerId();
    return Karate.run("classpath:integration/oaipmh.feature");
  }
}
