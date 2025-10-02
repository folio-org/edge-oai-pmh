package org.folio.edge.oaipmh.utils;

import static org.folio.edge.oaipmh.utils.Constants.KEY_VALUE_DELIMITER;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Base64;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.openarchives.oai._2.RequestType;
import org.openarchives.oai._2.VerbType;

@Slf4j
class ResumptionTokeUtilsTest {
  private static final String TENANT = "test_tenant";
  private static final String OAI_DC = "oai_dc";
  private static final String DATE_FROM = "2023-08-30";
  private static final String DATE_UNTIL = "2023-08-31";

  @ParameterizedTest
  @MethodSource("provideRequestTypes")
  void testFetchMetadataPrefix(RequestType requestType) {
    assertEquals(OAI_DC, ResumptionTokenUtils.fetchMetadataPrefix(requestType));
  }

  @ParameterizedTest
  @MethodSource("provideRequestTypes")
  void testFetchFrom(RequestType requestType) {
    assertEquals(DATE_FROM,
          ResumptionTokenUtils.fetchFrom(requestType));
  }

  @ParameterizedTest
  @MethodSource("provideRequestTypes")
  void testFetchUntil(RequestType requestType) {
    assertEquals(DATE_UNTIL, ResumptionTokenUtils.fetchUntil(requestType));
  }

  private static Stream<RequestType> provideRequestTypes() {
    var token = String.format("tenantId=%st&metadataPrefix=%s&from=%s&until=%s",
          TENANT, OAI_DC, DATE_FROM, DATE_UNTIL);
    var encodedToken = Base64.getUrlEncoder().encodeToString(token.getBytes())
          .split(KEY_VALUE_DELIMITER)[0];

    return Stream.of(
          new RequestType()
                .withVerb(VerbType.LIST_IDENTIFIERS)
                .withMetadataPrefix(OAI_DC)
                .withFrom(DATE_FROM)
                .withUntil(DATE_UNTIL),
          new RequestType()
                .withVerb(VerbType.LIST_IDENTIFIERS)
                .withResumptionToken(encodedToken));
  }
}
