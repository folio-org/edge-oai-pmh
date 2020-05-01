package org.folio.edge.oaipmh.clients.aoipmh;

import static io.vertx.core.http.HttpHeaders.ACCEPT;
import static io.vertx.core.http.HttpHeaders.CONTENT_LENGTH;
import static java.util.stream.Collectors.joining;
import static org.folio.edge.oaipmh.utils.Constants.MOD_OAI_PMH_ACCEPTED_TYPES;
import static org.folio.edge.oaipmh.utils.Constants.VERB;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;
import org.folio.edge.core.utils.OkapiClient;
import org.folio.edge.oaipmh.domain.Verb;
import org.folio.edge.oaipmh.utils.Constants;
import org.openarchives.oai._2.VerbType;

import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClientResponse;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class OaiPmhOkapiClient extends OkapiClient {

  private static final String URL_ENCODING_TYPE = "UTF-8";

  private static Logger logger = Logger.getLogger(OaiPmhOkapiClient.class);
  private static Map<String, String> endpointsMap = new HashMap<>();

  static {
    endpointsMap.put("GetRecord", "/oai/records");
    endpointsMap.put("Identify", "/oai/repository_info");
    endpointsMap.put("ListIdentifiers", "/oai/identifiers");
    endpointsMap.put("ListMetadataFormats", "/oai/metadata_formats");
    endpointsMap.put("ListRecords", "/oai/records");
    endpointsMap.put("ListSets", "/oai/sets");
  }

  public OaiPmhOkapiClient(OkapiClient client) {
    super(client);
    fixDefaultHeaders();
  }

  OaiPmhOkapiClient(Vertx vertx, String okapiURL, String tenant, long timeout) {
    super(vertx, okapiURL, tenant, timeout);
    fixDefaultHeaders();
  }

  // EDGOAIPMH-39 - the defaultHeaders map from OkapiClient (edge-common) contains
  // Accept: application/json, text/plain
  // so we need to replace it to "application/xml, text/xml"
  private void fixDefaultHeaders() {
    defaultHeaders.remove(ACCEPT);
    defaultHeaders.add(ACCEPT, MOD_OAI_PMH_ACCEPTED_TYPES);
  }

  /**
   * This method calls OAI-PMH-MOD to retrieve response for 'verb' from mod-oai-pmh
   *
   * @param parameters multimap of HTTP GET parameters
   * @param headers multimap of HTTP GET headers
   */
  public void call(MultiMap parameters, MultiMap headers,
    Handler<HttpClientResponse> responseHandler,
    Handler<Throwable> exceptionHandler) {
    String url = getUrlByVerb(parameters);

    log.debug("Requesting {}. call request headers: {}", url, headers);

    // "Content-Length" header appearing from POST request to edge-oai-pmh API should be removed as unnecessary
    // for GET request to mod-oai-pmh
    headers.remove(CONTENT_LENGTH);
    // EDGOAIPMH-39
    headers.remove(ACCEPT);
    headers.add(ACCEPT, MOD_OAI_PMH_ACCEPTED_TYPES);
    get(
      url,
      tenant,
      combineHeadersWithDefaults(headers),
      responseHandler,
      exceptionHandler);
  }

  /**
   * This method resolves endpoint for 'verb' from {@link VerbType} based on 'verb' value extracted
   * from HTTP GET parameters multimap
   *
   * @param parameters HTTP GET parameters multimap
   * @return endpoint for corresponding 'verb'
   */
  private String getEndpoint(MultiMap parameters) {
    String verb = parameters.get(VERB);
    String endpoint = endpointsMap.get(verb);
    // Special processing of GetRecord request with {id} to provide REST-styled call of mod-oai-pmh
    if (VerbType.GET_RECORD.value().equals(verb)) {
      try {
        String identifier = parameters.get(Constants.IDENTIFIER);
        endpoint = endpoint + "/" + URLEncoder.encode(identifier, URL_ENCODING_TYPE);
        parameters.remove(Constants.IDENTIFIER);
      } catch (UnsupportedEncodingException e) {
        logger.error(String.format("Error in identifier encoding: %s", e.getMessage()));
      }
    }
    return endpoint;
  }

  /**
   * This method transform MultiMap of HTTP GET request parameters to string representation
   *
   * @param parameters HTTP GET parameters multimap
   * @return string representation of GET request parameters
   */
  private String getParametersAsString(MultiMap parameters) {
    Set<String> excludedParams = Verb.fromName(parameters.get(VERB)).getExcludedParams();
    return parameters.entries().stream()
      .filter(e -> !excludedParams.contains(e.getKey()))
      .map(e -> e.getKey() + "=" + e.getValue())
      .collect(joining("&"));
  }

  /**
   * This method construct string representation of HTTP GET request for corresponding 'verb'
   * extracted from parameters multimap (it uses {@link OaiPmhOkapiClient#getEndpoint(MultiMap)} to
   * resolve endpoint and {@link OaiPmhOkapiClient#getParametersAsString(MultiMap)} to construct
   * parameters string)
   *
   * @param parameters HTTP GET parameters multimap
   * @return URL for corresponding 'verb'
   */
  private String getUrlByVerb(MultiMap parameters) {
    String params = getParametersAsString(parameters);
    if(params.length() > 0) {
      return String.format("%s%s?%s", okapiURL, getEndpoint(parameters), params);
    } else {
      return String.format("%s%s", okapiURL, getEndpoint(parameters));
    }
  }
}
