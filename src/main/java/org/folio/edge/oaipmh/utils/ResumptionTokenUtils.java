package org.folio.edge.oaipmh.utils;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.nonNull;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toMap;
import static org.folio.edge.oaipmh.utils.Constants.FROM;
import static org.folio.edge.oaipmh.utils.Constants.KEY_VALUE_DELIMITER;
import static org.folio.edge.oaipmh.utils.Constants.METADATA_PREFIX;
import static org.folio.edge.oaipmh.utils.Constants.PARAMETER_DELIMITER;
import static org.folio.edge.oaipmh.utils.Constants.TENANT_ID;
import static org.folio.edge.oaipmh.utils.Constants.UNTIL;

import lombok.experimental.UtilityClass;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.openarchives.oai._2.OAIPMH;
import org.openarchives.oai._2.RequestType;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@UtilityClass
public class ResumptionTokenUtils {
  public static String buildNewResumptionToken(OAIPMH oaipmh, String tenantId) {
    Map<String, String> params = new LinkedHashMap<>();
    params.put(TENANT_ID, tenantId);
    params.put(METADATA_PREFIX, fetchMetadataPrefix(oaipmh.getRequest()));
    ofNullable(fetchFrom(oaipmh.getRequest())).ifPresent(value -> params.put(FROM, value));
    ofNullable(fetchUntil(oaipmh.getRequest())).ifPresent(value -> params.put(UNTIL, value));
    return toResumptionToken(params);
  }

  public static Map<String, String> parseResumptionToken(String resumptionToken) {
    var decodedToken = new String(Base64.getUrlDecoder().decode(resumptionToken),
      StandardCharsets.UTF_8);
    return URLEncodedUtils
      .parse(decodedToken, UTF_8, PARAMETER_DELIMITER).stream()
      .collect(toMap(NameValuePair::getName, NameValuePair::getValue));
  }

  private static String toResumptionToken(Map<String, String> params) {
    var token = params.entrySet().stream()
      .map(e -> String.join(KEY_VALUE_DELIMITER, e.getKey(), e.getValue()))
      .collect(Collectors.joining(PARAMETER_DELIMITER.toString()));
    return Base64.getUrlEncoder().encodeToString(token.getBytes()).split(KEY_VALUE_DELIMITER)[0];
  }

  private static String fetchMetadataPrefix(RequestType requestType) {
    if (nonNull(requestType.getMetadataPrefix())) {
      return requestType.getMetadataPrefix();
    } else if (nonNull(requestType.getResumptionToken())) {
      return parseResumptionToken(requestType.getResumptionToken()).get(METADATA_PREFIX);
    }
    return null;
  }

  private static String fetchFrom(RequestType requestType) {
    if (nonNull(requestType.getFrom())) {
      return requestType.getFrom();
    } else if (nonNull(requestType.getResumptionToken())) {
      return parseResumptionToken(requestType.getResumptionToken()).get(FROM);
    }
    return null;
  }

  private static String fetchUntil(RequestType requestType) {
    if (nonNull(requestType.getUntil())) {
      return requestType.getUntil();
    } else if (nonNull(requestType.getResumptionToken())) {
      return parseResumptionToken(requestType.getResumptionToken()).get(UNTIL);
    }
    return null;
  }
}
