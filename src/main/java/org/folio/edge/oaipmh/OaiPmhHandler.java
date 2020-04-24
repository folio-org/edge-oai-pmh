package org.folio.edge.oaipmh;

import static com.google.common.collect.ImmutableSet.of;
import static io.vertx.core.http.HttpHeaders.ACCEPT;
import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.folio.edge.oaipmh.ModConfigurationService.ENABLE_OAI_SERVICE;
import static org.folio.edge.oaipmh.ModConfigurationService.GENERAL_CONFIG;
import static org.folio.edge.oaipmh.ModConfigurationService.MODULE_NAME;
import static org.folio.edge.oaipmh.utils.Constants.FROM;
import static org.folio.edge.oaipmh.utils.Constants.IDENTIFIER;
import static org.folio.edge.oaipmh.utils.Constants.METADATA_PREFIX;
import static org.folio.edge.oaipmh.utils.Constants.RESUMPTION_TOKEN;
import static org.folio.edge.oaipmh.utils.Constants.SET;
import static org.folio.edge.oaipmh.utils.Constants.TEXT_XML_TYPE;
import static org.folio.edge.oaipmh.utils.Constants.UNTIL;
import static org.folio.edge.oaipmh.utils.Constants.VERB;
import static org.openarchives.oai._2.OAIPMHerrorcodeType.BAD_ARGUMENT;
import static org.openarchives.oai._2.OAIPMHerrorcodeType.BAD_VERB;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.apache.log4j.Logger;
import org.folio.edge.core.Handler;
import org.folio.edge.core.model.ClientInfo;
import org.folio.edge.core.security.SecureStore;
import org.folio.edge.core.utils.ApiKeyUtils;
import org.folio.edge.core.utils.OkapiClient;
import org.folio.edge.core.utils.OkapiClientFactory;
import org.folio.edge.oaipmh.utils.Constants;
import org.folio.edge.oaipmh.utils.OaiPmhOkapiClient;
import org.folio.rest.client.ConfigurationsClient;
import org.folio.rest.jaxrs.model.Configs;
import org.openarchives.oai._2.OAIPMH;
import org.openarchives.oai._2.OAIPMHerrorType;
import org.openarchives.oai._2.OAIPMHerrorcodeType;
import org.openarchives.oai._2.RequestType;
import org.openarchives.oai._2.VerbType;

import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import me.escoffier.vertx.completablefuture.VertxCompletableFuture;

public class OaiPmhHandler extends Handler {

  private static Logger logger = Logger.getLogger(OaiPmhHandler.class);

  /** Expected valid http status codes to be returned by repository logic */
  private static final Set<Integer> EXPECTED_CODES = of(200, 400, 404, 422);

  private String errorsProcessingConfigSetting;

  private final ConfigurationService configurationService;

  public OaiPmhHandler(SecureStore secureStore, OkapiClientFactory ocf, ConfigurationService configurationService) {
    super(secureStore, ocf);
    this.configurationService = configurationService;
  }

  protected void handle(RoutingContext ctx) {
    HttpServerRequest request = ctx.request();
    if (logger.isDebugEnabled()) {
      logger.debug(request.method() + " " + request.absoluteURI());
      logger.debug("Client request parameters:");
      request.params()
             .forEach(param -> logger.debug(String.format("> %s: %s", param.getKey(), param.getValue())));
      logger.debug("Client request headers:");
      request.headers()
             .forEach(header -> logger.debug(String.format("> %s: %s", header.getKey(), header.getValue())));
    }

    modConfigurationService.getConfigSettingValue(ctx, MODULE_NAME, GENERAL_CONFIG, ENABLE_OAI_SERVICE)
      .thenAccept(enableOaiService -> {

        if (!Boolean.parseBoolean(enableOaiService)) {
          failureResponse(ctx, 503, "OAI-PMH service is disabled");
          return;
        }

        if (!supportedAcceptHeaders(request)) {
          handleNotAcceptableError(ctx, request);
          return;
        }
          handleCommon(ctx, null, null, (okapiClient, param) -> {

            final ConfigurationsClient configurationsClient = new ConfigurationsClient(okapiClient.okapiURL, okapiClient.tenant,
              okapiClient.getToken());

            getErrorsProcessingConfigSetting(configurationsClient).thenAccept(result -> {

              errorsProcessingConfigSetting = result;

              Verb verb = Verb.fromName(request.getParam(VERB));
              if (verb == null) {
                badRequest(ctx, "Bad verb. Verb '" + request.getParam(VERB) + "' is not implemented", null, BAD_VERB);
                return;
              }

              List<OAIPMHerrorType> errors = verb.validate(ctx);
              if (!errors.isEmpty()) {
                badRequest(ctx, verb.toString(), errors);
                return;
              }

              String[] requiredParams;
              if (verb.getExclusiveParam() != null && request.getParam(verb.getExclusiveParam()) != null) {
                requiredParams = new String[] { verb.getExclusiveParam() };
              } else {
                requiredParams = verb.getRequiredParams()
                  .toArray(new String[0]);
              }

              super.handleCommon(ctx,
                requiredParams,
                verb.getOptionalParams().toArray(new String[0]),
                (client, params) -> {
                  final OaiPmhOkapiClient oaiPmhClient = new OaiPmhOkapiClient(client);
                  oaiPmhClient
                    .call(request.params(),
                      request.headers(),
                      response -> handleProxyResponse(ctx, response),
                      t -> handleException(ctx, t));
                });
            })
              .exceptionally(exc -> {
                handleException(ctx, exc);
                return null;
              });
          });
        }
  }
  /**
   * EDGE-OAI-PMH supports only text/xml and all its derivatives in Accept header.
   * Empty Accept header implies any MIME type is accepted, same as Accept: *//*
   *
   * Valid examples: text/xml, text/*, *//*, *\xml
   * NOT Valid examples: application/xml, application/*, test/json
   *
   * @param request - http request to the module
   * @return - true if accept headers are supported
   */
  private boolean supportedAcceptHeaders(HttpServerRequest request) {
    if (request.headers().contains(ACCEPT)) {
      final String supportedHeadersRegexp = "(text|\\*)\\s*/\\s*(xml|\\*)";
      final Pattern pattern = Pattern.compile(supportedHeadersRegexp);
      return request.headers().getAll(ACCEPT).stream().anyMatch(h -> pattern.matcher(h).find());
    }
    return true;
  }

  /**
   * This method contains processing of oai-pmh-mod response in normal processing flow
   *
   * @param ctx routing context
   * @param response populated http-response
   */
  @Override
  protected void handleProxyResponse(RoutingContext ctx, HttpClientResponse response) {
    HttpServerResponse edgeResponse = ctx.response();

    int httpStatusCode = response.statusCode();

    setStatusCodeToResponse(edgeResponse, httpStatusCode);

    if (EXPECTED_CODES.contains(httpStatusCode)) {
      edgeResponse.putHeader(HttpHeaders.CONTENT_TYPE, Constants.TEXT_XML_TYPE);

      // In case the repository logic already compressed the response, lets transfer header to avoid potential doubled compression
      Optional<String> encodingHeader = Optional.ofNullable(response.getHeader(HttpHeaders.CONTENT_ENCODING));
      encodingHeader.ifPresent(value -> edgeResponse.putHeader(HttpHeaders.CONTENT_ENCODING, value));

      /* Using bodyHandler to wait for full response body to be read. Alternative option is to use endHandler and pumping i.e.
       * Pump.pump(response, ctx.response()).start()
       * but this requires chunked response (ctx.response().setChunked(true)) and there is no any guarantee
       * that all harvesters support such responses.
       */
      response.bodyHandler(buffer -> {
        edgeResponse.end(buffer);
        if (logger.isDebugEnabled()) {
          if (!encodingHeader.isPresent()) {
            logger.debug("Repository response body: " + buffer);
          }
          logger.debug("Edge response headers:");
          edgeResponse.headers()
                      .forEach(header -> logger.debug(String.format("< %s: %s", header.getKey(), header.getValue())));
        }
      });
    } else {
      logger.error(String.format("Error in the response from repository: (%d)", httpStatusCode));
      internalServerError(ctx, "Internal Server Error");
    }
  }

  @Override
  protected void accessDenied(RoutingContext ctx, String msg) {
    logger.error("accessDenied: " + msg);
    super.accessDenied(ctx, "The access to repository is denied. Please contact administrator(s).");
  }

  @Override
  protected void requestTimeout(RoutingContext ctx, String msg) {
    logger.error("requestTimeout: " + msg);
    super.requestTimeout(ctx, "The repository cannot process request. Please try later or contact administrator(s).");
  }

  @Override
  protected void handleCommon(RoutingContext ctx, String[] requiredParams, String[] optionalParams,
      Handler.TwoParamVoidFunction<OkapiClient, Map<String, String>> action) {
    String key = keyHelper.getApiKey(ctx);
    ClientInfo clientInfo;
    try {
      clientInfo = ApiKeyUtils.parseApiKey(key);
    } catch (ApiKeyUtils.MalformedApiKeyException e) {
      invalidApiKey(ctx, key);
      return;
    }

    final OkapiClient client = ocf.getOkapiClient(clientInfo.tenantId);

    iuHelper.getToken(client, clientInfo.salt, clientInfo.tenantId, clientInfo.username)
      .thenAcceptAsync(token -> {
        client.setToken(token);
        action.apply(client, null);
      })
      .exceptionally(t -> {
        if (t instanceof TimeoutException) {
          requestTimeout(ctx, t.getMessage());
        } else {
          accessDenied(ctx, t.getMessage());
        }
        return null;
      });
  }

  /**
   * This handler-method for processing exceptional flow
   *
   * @param ctx current routing context
   * @param t throwable object
   */
  private void handleException(RoutingContext ctx, Throwable t) {
    logger.error("Exception in OKAPI calling", t);
    if (t instanceof TimeoutException) {
      requestTimeout(ctx, t.getMessage());
    } else {
      internalServerError(ctx, t.getMessage());
    }
  }

  private void handleError(RoutingContext ctx, int status, OAIPMH respBody) {
    String xml = null;
    try {
      xml = ResponseHelper.getInstance().writeToString(respBody);
    } catch (Exception e) {
      logger.error("Exception marshalling XML", e);
    }

    setStatusCodeToResponse(ctx.response(), status);

    if (xml != null) {
      logger.warn("The request was invalid. The response returned with errors: " + xml);
      ctx.response()
        .putHeader(HttpHeaders.CONTENT_TYPE, TEXT_XML_TYPE)
        .end(xml);
    } else {
      ctx.response().end();
    }
  }

  private OAIPMH buildBaseResponse(RoutingContext ctx, String verb) {
    String baseUrl = StringUtils.substringBefore(ctx.request().absoluteURI(), "?");
    MultiMap params = ctx.request().params();
    return new OAIPMH()
      .withResponseDate(Instant.now().truncatedTo(ChronoUnit.SECONDS))
      .withRequest(new RequestType()
        .withVerb(isEmpty(verb) ? null : VerbType.fromValue(verb))
        .withSet(params.get(SET))
        .withResumptionToken(params.get(RESUMPTION_TOKEN))
        .withIdentifier(params.get(IDENTIFIER))
        .withMetadataPrefix(params.get(METADATA_PREFIX))
        .withFrom(params.get(FROM))
        .withUntil(params.get(UNTIL))
        .withValue(baseUrl));
  }

  @Override
  protected void badRequest(RoutingContext ctx, String body) {
    String verb = ctx.request().getParam(VERB);
    badRequest(ctx, body, verb, BAD_ARGUMENT);
  }

  private void badRequest(RoutingContext ctx, String body, String verb, OAIPMHerrorcodeType type) {
    OAIPMH resp = buildBaseResponse(ctx, verb)
      .withErrors(new OAIPMHerrorType().withCode(type).withValue(body));
    handleError(ctx, 400, resp);
  }

  private void badRequest(RoutingContext ctx, String verb, List<OAIPMHerrorType> errors) {
    OAIPMH resp = buildBaseResponse(ctx, verb)
      .withErrors(errors);
    handleError(ctx, 400, resp);
  }

  private void handleNotAcceptableError(RoutingContext ctx, HttpServerRequest request) {
    String unsupportedType = request.headers()
      .getAll(ACCEPT)
      .stream()
      .filter(value -> (!value.equals(TEXT_XML_TYPE)))
      .findFirst()
      .orElse("");
    notAcceptable(ctx,
        "Accept header must be \"text/xml\" for this request, but it is " + "\"" + unsupportedType + "\"" + ", can not send */*");
  }

  /**
   * This method make request to mod-configuration module and receive config setting associate with name behavior
   *
   * @param ctx routing context
   * @return value of field errorsProcessing
   */
  private CompletableFuture<String> getErrorsProcessingConfigSetting(RoutingContext ctx) {
    final String query = "module==OAIPMH and configName==behavior";
    final int configNumber = 0;
    CompletableFuture<String> future = new VertxCompletableFuture<>();
    String tenantId = ctx.request().headers().get(X_OKAPI_TENANT);
    String token = ctx.request().headers().get(X_OKAPI_TOKEN);
    try {
      new ConfigurationsClient(getOkapiURL(), tenantId, token).getConfigurationsEntries(query, 0, 100, null, null,
          responseConfig -> responseConfig.bodyHandler(body -> {
            try {
              if (responseConfig.statusCode() != 200) {
                logger.error("Error getting configuration " + responseConfig.statusMessage());
                future.complete(String.valueOf(responseConfig.statusCode()));
                return;
              }
              future.complete(new JsonObject(body.toJsonObject()
                .mapTo(Configs.class)
                .getConfigs()
                .get(configNumber)
                .getValue()).getString("errorsProcessing"));
            } catch (Exception exc) {
              logger.error("Error when get configuration value from mod-configurations client response ", exc);
              future.completeExceptionally(exc);
            }
          }));
    } catch (Exception e) {
      logger.error("Error happened initializing mod-configurations client ", e);
      future.completeExceptionally(e);
    }
    return future;
  }

  private void setStatusCodeToResponse(HttpServerResponse response, int status) {
    if (getErrorsProcessingConfigSetting().equals("200")) {
      response.setStatusCode(200);
    } else {
      response.setStatusCode(status);
    }
  }

  private void failureResponse(RoutingContext ctx, int code, String body) {
    ctx.response()
      .setStatusCode(code)
      .putHeader(HttpHeaders.CONTENT_TYPE, "text/plain")
      .end(body);
  }

  public String getErrorsProcessingConfigSetting() {
    return errorsProcessingConfigSetting;
  }
}
