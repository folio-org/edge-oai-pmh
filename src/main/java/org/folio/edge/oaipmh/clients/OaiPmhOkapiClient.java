package org.folio.edge.oaipmh.clients;

import static io.vertx.core.http.HttpHeaders.ACCEPT;
import static io.vertx.core.http.HttpHeaders.CONTENT_LENGTH;
import static java.util.stream.Collectors.joining;
import static org.folio.edge.oaipmh.utils.Constants.MOD_OAI_PMH_ACCEPTED_TYPES;

import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpResponse;
import org.folio.edge.core.utils.OkapiClient;

import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class OaiPmhOkapiClient extends OkapiClient {

  private static final String OAI_PMH_ENDPOINT = "/oai/records";

  public OaiPmhOkapiClient(OkapiClient client) {
    super(client);
    fixDefaultHeaders();
  }

  OaiPmhOkapiClient(Vertx vertx, String okapiURL, String tenant, int timeout) {
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
    Handler<HttpResponse<Buffer>> responseHandler,
    Handler<Throwable> exceptionHandler) {
    String url = getUrl(parameters);
    // "Content-Length" header appearing from POST request to edge-oai-pmh API should be removed as unnecessary
    // for GET request to mod-oai-pmh
    headers.remove(CONTENT_LENGTH);
    // EDGOAIPMH-39
    headers.remove(ACCEPT);
    headers.add(ACCEPT, MOD_OAI_PMH_ACCEPTED_TYPES);
    log.debug("Url after getting Url: {}", url);
    get(
      url,
      tenant,
      headers,
      responseHandler,
      exceptionHandler);
  }

  private String getParametersAsString(MultiMap parameters) {
    return parameters.entries().stream()
      .filter(e -> !e.getKey().equals("apiKeyPath"))
      .map(e -> e.getKey() + "=" + e.getValue())
      .collect(joining("&"));
  }

  /**
   * This method construct string representation of HTTP GET request with path '/oai/verb'.
   *
   * @return URL for corresponding 'verb'
   */
  private String getUrl(MultiMap parameters) {
    String params = getParametersAsString(parameters);
    if(params.length() > 0) {
      return String.format("%s%s?%s", okapiURL, OAI_PMH_ENDPOINT, params);
    } else {
      return String.format("%s%s", okapiURL, OAI_PMH_ENDPOINT);
    }
  }
}
