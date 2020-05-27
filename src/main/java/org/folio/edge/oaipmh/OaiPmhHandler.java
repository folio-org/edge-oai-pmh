package org.folio.edge.oaipmh;

import static com.google.common.collect.ImmutableSet.of;
import static io.vertx.core.http.HttpHeaders.ACCEPT;
import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.apache.http.HttpStatus.SC_NOT_FOUND;
import static org.apache.http.HttpStatus.SC_OK;
import static org.apache.http.HttpStatus.SC_UNPROCESSABLE_ENTITY;
import static org.folio.edge.core.Constants.TEXT_PLAIN;
import static org.folio.edge.core.Constants.TEXT_XML;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

import org.folio.edge.core.Handler;
import org.folio.edge.core.security.SecureStore;
import org.folio.edge.core.utils.OkapiClientFactory;
import org.folio.edge.oaipmh.clients.aoipmh.OaiPmhOkapiClient;

import com.google.common.collect.Iterables;

import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class OaiPmhHandler extends Handler {

  /** Expected valid http status codes to be returned by repository logic */
  private static final Set<Integer> EXPECTED_CODES = of(SC_OK, SC_BAD_REQUEST, SC_NOT_FOUND, SC_UNPROCESSABLE_ENTITY);

  public OaiPmhHandler(SecureStore secureStore, OkapiClientFactory ocf) {
    super(secureStore, ocf);
  }

  protected void handle(RoutingContext ctx) {
    HttpServerRequest request = ctx.request();
    log.debug("Client request: {} {}", request.method(), request.absoluteURI());
    log.debug("Client request parameters: " + request.params());
    log.debug("Client request headers: " + Iterables.toString(request.headers()));

    if (!supportedAcceptHeaders(request)) {
      notAcceptableResponse(ctx, request);
      return;
    }

    handleCommon(ctx, new String[0], new String[0] , (okapiClient, params) -> {
      final OaiPmhOkapiClient oaiPmhClient = new OaiPmhOkapiClient(okapiClient);
      oaiPmhClient.call(request.params(), request.headers(), response -> handleProxyResponse(ctx, response), t -> oaiPmhFailureHandler(ctx, t));
    });
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
    ctx.response().setStatusCode(response.statusCode());
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
          log.debug("Response from oai-pmh headers:{} \n {}", Iterables.toString(response.headers()), buffer);
        }
        log.debug("Edge response headers: {}", Iterables.toString(edgeResponse.headers()));
      });
    } else {
      log.error(String.format("Error in the response from repository: (%d)", response.statusCode()));
      internalServerError(ctx, "Internal Server Error");
    }
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

}
