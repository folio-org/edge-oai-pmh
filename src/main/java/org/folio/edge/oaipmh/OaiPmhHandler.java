package org.folio.edge.oaipmh;

import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.folio.edge.core.Handler;
import org.folio.edge.core.security.SecureStore;
import org.folio.edge.core.utils.OkapiClientFactory;
import org.folio.edge.oaipmh.utils.Constants;
import org.folio.edge.oaipmh.utils.OaiPmhOkapiClient;
import org.openarchives.oai._2.OAIPMH;
import org.openarchives.oai._2.OAIPMHerrorType;
import org.openarchives.oai._2.OAIPMHerrorcodeType;
import org.openarchives.oai._2.RequestType;
import org.openarchives.oai._2.VerbType;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeoutException;

import static com.google.common.collect.ImmutableSet.of;
import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.folio.edge.oaipmh.utils.Constants.FROM;
import static org.folio.edge.oaipmh.utils.Constants.IDENTIFIER;
import static org.folio.edge.oaipmh.utils.Constants.METADATA_PREFIX;
import static org.folio.edge.oaipmh.utils.Constants.RESUMPTION_TOKEN;
import static org.folio.edge.oaipmh.utils.Constants.SET;
import static org.folio.edge.oaipmh.utils.Constants.TEXT_XML;
import static org.folio.edge.oaipmh.utils.Constants.UNTIL;
import static org.folio.edge.oaipmh.utils.Constants.VERB;
import static org.openarchives.oai._2.OAIPMHerrorcodeType.BAD_ARGUMENT;
import static org.openarchives.oai._2.OAIPMHerrorcodeType.BAD_VERB;

public class OaiPmhHandler extends Handler {

  private static Logger logger = Logger.getLogger(OaiPmhHandler.class);

  /** Expected valid http status codes to be returned by repository logic */
  private static final Set<Integer> EXPECTED_CODES = of(200, 400, 404, 422);

  public OaiPmhHandler(SecureStore secureStore, OkapiClientFactory ocf) {
    super(secureStore, ocf);
  }

  protected void handle(RoutingContext ctx) {
    HttpServerRequest request = ctx.request();
    if (logger.isDebugEnabled()) {
      logger.debug(request.method() + " " + request.absoluteURI());
      logger.debug("Client request parameters:");
      request.params()
             .forEach(param -> logger.debug(param.getKey() + ": " + param.getValue()));
      logger.debug("Client request headers:");
      request.headers()
             .forEach(header -> logger.debug(header.getKey() + ": " + header.getValue()));
    }

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
      requiredParams = new String[]{verb.getExclusiveParam()};
    } else {
      requiredParams = verb.getRequiredParams().toArray(new String[0]);
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
    edgeResponse.setStatusCode(httpStatusCode);
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
        if (!encodingHeader.isPresent() && logger.isDebugEnabled()) {
          logger.debug("Repository response body: " + buffer);
        }
        edgeResponse.end(buffer);
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
    ctx.response().setStatusCode(status);

    if (xml != null) {
      ctx.response()
        .putHeader(HttpHeaders.CONTENT_TYPE, TEXT_XML)
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
}
