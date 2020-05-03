package org.folio.edge.oaipmh;

import static com.google.common.collect.ImmutableSet.of;
import static io.vertx.core.http.HttpHeaders.ACCEPT;
import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.apache.http.HttpStatus.SC_NOT_FOUND;
import static org.apache.http.HttpStatus.SC_OK;
import static org.apache.http.HttpStatus.SC_SERVICE_UNAVAILABLE;
import static org.apache.http.HttpStatus.SC_UNPROCESSABLE_ENTITY;
import static org.folio.edge.core.Constants.TEXT_XML;
import static org.folio.edge.oaipmh.utils.Constants.FROM;
import static org.folio.edge.oaipmh.utils.Constants.IDENTIFIER;
import static org.folio.edge.oaipmh.utils.Constants.METADATA_PREFIX;
import static org.folio.edge.oaipmh.utils.Constants.RESUMPTION_TOKEN;
import static org.folio.edge.oaipmh.utils.Constants.SET;
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
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.folio.edge.core.Handler;
import org.folio.edge.core.model.ClientInfo;
import org.folio.edge.core.security.SecureStore;
import org.folio.edge.core.utils.ApiKeyUtils;
import org.folio.edge.core.utils.OkapiClient;
import org.folio.edge.core.utils.OkapiClientFactory;
import org.folio.edge.oaipmh.clients.aoipmh.OaiPmhOkapiClient;
import org.folio.edge.oaipmh.clients.modconfiguration.ConfigurationService;
import org.folio.edge.oaipmh.domain.Verb;
import org.folio.edge.oaipmh.utils.ResponseHelper;
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
import io.vertx.ext.web.RoutingContext;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class OaiPmhHandler extends Handler {

  private final ConfigurationService configurationService;

  /** Expected valid http status codes to be returned by repository logic */
  private static final Set<Integer> EXPECTED_CODES = of(SC_OK, SC_BAD_REQUEST, SC_NOT_FOUND, SC_UNPROCESSABLE_ENTITY);
  private static final String ERRORS_PROCESSING_KEY = "responsestatus200";

  public OaiPmhHandler(SecureStore secureStore, OkapiClientFactory ocf, ConfigurationService configurationService) {
    super(secureStore, ocf);
    this.configurationService = configurationService;
  }

  protected void handle(RoutingContext ctx) {
    HttpServerRequest request = ctx.request();
    log.debug("Client request: {} {}", request.method(), request.absoluteURI());
    log.debug("Client request parameters: " + request.params());
    log.debug("Client request headers: " + request.headers());

    if (!supportedAcceptHeaders(request)) {
      notAcceptableResponse(ctx, request);
      return;
    }

    getOkapiClient(ctx, (okapiClient, param) -> configurationService.getEnableOaiServiceConfigSetting(okapiClient)
      .setHandler(oaiPmhEnabled -> {
        if (!oaiPmhEnabled.result()) {
          serviceUnavailableResponse(ctx);
          return;
        }
        configurationService.associateErrorsWith200Status(okapiClient)
          .setHandler(responseStatusShouldBe200 -> {
            ctx.put(ERRORS_PROCESSING_KEY, responseStatusShouldBe200.result());
            Verb verb = Verb.fromName(request.getParam(VERB));
            if (verb == null) {
              badRequest(ctx, String.format("Bad verb. Verb '%s' is not implemented", request.getParam(VERB)), null, BAD_VERB);
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
            super.handleCommon(ctx, requiredParams, verb.getOptionalParams()
              .toArray(new String[0]), (client, params) -> {
                final OaiPmhOkapiClient oaiPmhClient = new OaiPmhOkapiClient(client);
                oaiPmhClient.call(request.params(), request.headers(), response -> handleProxyResponse(ctx, response),
                    t -> oaiPmhFailureHandler(ctx, t));
              });
          });
      }));
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
    if (request.headers()
      .contains(ACCEPT)) {
      final String supportedHeadersRegexp = "(text|\\*)\\s*/\\s*(xml|\\*)";
      final Pattern pattern = Pattern.compile(supportedHeadersRegexp);
      return request.headers()
        .getAll(ACCEPT)
        .stream()
        .anyMatch(h -> pattern.matcher(h)
          .find());
    }
    return true;
  }

  /**
   * This method contains processing of oai-pmh-mod response in normal processing flow
   *
   * @param ctx      routing context
   * @param response populated http-response
   */
  @Override
  protected void handleProxyResponse(RoutingContext ctx, HttpClientResponse response) {
    HttpServerResponse edgeResponse = ctx.response();
    int httpStatusCode = response.statusCode();
    ctx.response()
      .setStatusCode(getErrorsProcessingConfigSetting(ctx) ? SC_OK : response.statusCode());
    if (EXPECTED_CODES.contains(httpStatusCode)) {
      edgeResponse.putHeader(HttpHeaders.CONTENT_TYPE, TEXT_XML);
      // In case the repository logic already compressed the response, lets transfer header to avoid potential doubled compression
      Optional<String> encodingHeader = Optional.ofNullable(response.getHeader(HttpHeaders.CONTENT_ENCODING));
      encodingHeader.ifPresent(value -> edgeResponse.putHeader(HttpHeaders.CONTENT_ENCODING, value));
      /*
       * Using bodyHandler to wait for full response body to be read. Alternative option is to use endHandler and pumping i.e.
       * Pump.pump(response, ctx.response()).start() but this requires chunked response (ctx.response().setChunked(true)) and there
       * is no any guarantee that all harvesters support such responses.
       */
      response.bodyHandler(buffer -> {
        edgeResponse.end(buffer);
        if (!encodingHeader.isPresent()) {
          log.debug("Response from oai-pmh response:{} \n {}",response.headers(), buffer);
        }
        log.debug("Edge response headers: {}", edgeResponse.headers());
      });
    } else {
      log.error(String.format("Error in the response from repository: (%d)", httpStatusCode));
      internalServerError(ctx, "Internal Server Error");
    }
  }

  @Override
  protected void badRequest(RoutingContext ctx, String body) {
    String verb = ctx.request()
      .getParam(VERB);
    badRequest(ctx, body, verb, BAD_ARGUMENT);
  }

  private void badRequest(RoutingContext ctx, String body, String verb, OAIPMHerrorcodeType type) {
    OAIPMH resp = buildBaseResponse(ctx, verb).withErrors(new OAIPMHerrorType().withCode(type)
      .withValue(body));
    writeResponse(ctx, resp);
  }

  private void badRequest(RoutingContext ctx, String verb, List<OAIPMHerrorType> errors) {
    OAIPMH resp = buildBaseResponse(ctx, verb).withErrors(errors);
    writeResponse(ctx, resp);
  }

  @Override
  protected void accessDenied(RoutingContext ctx, String msg) {
    log.error("accessDenied: " + msg);
    super.accessDenied(ctx, "The access to repository is denied. Please contact administrator(s).");
  }

  @Override
  protected void requestTimeout(RoutingContext ctx, String msg) {
    log.error("requestTimeout: " + msg);
    super.requestTimeout(ctx, "The repository cannot process request. Please try later or contact administrator(s).");
  }

  private void getOkapiClient(RoutingContext ctx, Handler.TwoParamVoidFunction<OkapiClient, Map<String, String>> action) {
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
   * @param t   throwable object
   */
  public void oaiPmhFailureHandler(RoutingContext ctx, Throwable t) {
    log.error("Exception in calling OKAPI", t);
    if (t instanceof TimeoutException) {
      requestTimeout(ctx, t.getMessage());
    } else {
      internalServerError(ctx, t != null? t.getMessage(): "");
    }
  }

  private void writeResponse(RoutingContext ctx, OAIPMH respBody) {
    String xml = null;
    try {
      xml = ResponseHelper.getInstance()
        .writeToString(respBody);
    } catch (Exception e) {
      log.error("Exception marshalling XML", e);
    }
    final int responseStatusCode = getErrorsProcessingConfigSetting(ctx) ? SC_OK : SC_BAD_REQUEST;
    ctx.response()
      .setStatusCode(responseStatusCode);

    if (xml != null) {
      log.warn("The request was invalid. The response returned with errors: " + xml);
      ctx.response()
        .putHeader(HttpHeaders.CONTENT_TYPE, TEXT_XML)
        .end(xml);
    } else {
      ctx.response()
        .end();
    }
  }

  private void notAcceptableResponse(RoutingContext ctx, HttpServerRequest request) {
    String unsupportedType = request.headers()
      .getAll(ACCEPT)
      .stream()
      .filter(value -> (!value.equals(TEXT_XML)))
      .findFirst()
      .orElse("");
    notAcceptable(ctx,
        "Accept header must be \"text/xml\" for this request, but it is " + "\"" + unsupportedType + "\"" + ", can not send */*");
  }

  private void serviceUnavailableResponse(RoutingContext ctx) {
    ctx.response()
      .setStatusCode(SC_SERVICE_UNAVAILABLE)
      .putHeader(HttpHeaders.CONTENT_TYPE, "text/plain")
      .end("OAI-PMH service is disabled");
  }

  public Boolean getErrorsProcessingConfigSetting(RoutingContext ctx) {
    return ctx.get(ERRORS_PROCESSING_KEY);
  }

  private OAIPMH buildBaseResponse(RoutingContext ctx, String verb) {
    String baseUrl = StringUtils.substringBefore(ctx.request()
      .absoluteURI(), "?");
    MultiMap params = ctx.request()
      .params();
    return new OAIPMH().withResponseDate(Instant.now()
      .truncatedTo(ChronoUnit.SECONDS))
      .withRequest(new RequestType().withVerb(isEmpty(verb) ? null : VerbType.fromValue(verb))
        .withSet(params.get(SET))
        .withResumptionToken(params.get(RESUMPTION_TOKEN))
        .withIdentifier(params.get(IDENTIFIER))
        .withMetadataPrefix(params.get(METADATA_PREFIX))
        .withFrom(params.get(FROM))
        .withUntil(params.get(UNTIL))
        .withValue(baseUrl));
  }
}
