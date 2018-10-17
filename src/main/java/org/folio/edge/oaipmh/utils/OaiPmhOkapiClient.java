package org.folio.edge.oaipmh.utils;

import static com.google.common.collect.ImmutableSet.of;
import static java.util.stream.Collectors.joining;
import static org.folio.edge.core.Constants.PARAM_API_KEY;
import static org.folio.edge.oaipmh.utils.Constants.VERB;

import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import me.escoffier.vertx.completablefuture.VertxCompletableFuture;
import org.apache.log4j.Logger;
import org.folio.edge.core.utils.OkapiClient;
import org.openarchives.oai._2.VerbType;

public class OaiPmhOkapiClient extends OkapiClient {

  private static Logger logger = Logger.getLogger(OaiPmhOkapiClient.class);
  private static final String URL_ENCODING_TYPE = "UTF-8";
  private static final Set<String> EXCLUDED_PARAMS = of(VERB, PARAM_API_KEY);

  public OaiPmhOkapiClient(OkapiClient client) {
    super(client);
  }

  private static Map<String, String> endpointsMap = new HashMap<>();

  static {
    endpointsMap.put("GetRecord", "/oai/records");
    endpointsMap.put("Identify", "/oai/repository_info");
    endpointsMap.put("ListIdentifiers", "/oai/identifiers");
    endpointsMap.put("ListMetadataFormats", "/oai/metadata_formats");
    endpointsMap.put("ListRecords", "/oai/records");
    endpointsMap.put("ListSets", "/oai/sets");
  }

  protected OaiPmhOkapiClient(Vertx vertx, String okapiURL, String tenant, long timeout) {
    super(vertx, okapiURL, tenant, timeout);
  }

  /**
   * This method calls OAI-PMH-MOD to retrieve response for 'verb' from nod-oai-pmh
   *
   * @param parameters multimap of HTTP GET parameters
   * @param headers multimap of HTTP GET headers
   * @return future with response body
   */
  public CompletableFuture<String> call(MultiMap parameters, MultiMap headers) {
    VertxCompletableFuture<String> future = new VertxCompletableFuture<>(vertx);
    String url = getUrlByVerb(parameters);
    get(
      url,
      tenant,
      combineHeadersWithDefaults(headers),
      response -> response.bodyHandler(body -> {
        int httpStatusCode = response.statusCode();
        if (httpStatusCode == 200) {
          String responseBody = body.toString();
          logger.info(String.format(
            "Successfully retrieved info from oai-pmh: (%s) %s",
            httpStatusCode,
            responseBody));
          future.complete(responseBody);
        } else {
          String err = String.format(
            "Failed to get info from oai-pmh: (%s) %s",
            response.statusCode(),
            body.toString());
          logger.error(err);
          future.complete(body.toString());
        }
      }),
      tr -> {
        logger.error("Exception in oai-pmh: " + tr.getMessage());
        future.completeExceptionally(tr);
      });
    return future;
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
        logger.error("Error in identifier encoding " + e.getMessage());
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
    String query = "?" + parameters.entries().stream()
      .filter(e -> !EXCLUDED_PARAMS.contains(e.getKey()))
      .map(e -> e.getKey() + "=" + e.getValue())
      .collect(joining("&"));
    return query;
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
    return okapiURL + getEndpoint(parameters) + getParametersAsString(parameters);
  }
}
