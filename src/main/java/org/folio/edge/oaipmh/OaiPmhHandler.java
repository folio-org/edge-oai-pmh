package org.folio.edge.oaipmh;

import static io.vertx.core.http.HttpHeaders.ACCEPT;
import static java.util.Objects.nonNull;
import static java.util.Optional.ofNullable;
import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.apache.http.HttpStatus.SC_NOT_FOUND;
import static org.apache.http.HttpStatus.SC_OK;
import static org.apache.http.HttpStatus.SC_SERVICE_UNAVAILABLE;
import static org.apache.http.HttpStatus.SC_UNPROCESSABLE_ENTITY;
import static org.folio.edge.core.Constants.TEXT_XML;
import static org.folio.edge.oaipmh.utils.Constants.METADATA_PREFIX;
import static org.folio.edge.oaipmh.utils.Constants.RESUMPTION_TOKEN;

import java.io.ByteArrayInputStream;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpResponse;
import org.folio.edge.core.Handler;
import org.folio.edge.core.security.SecureStore;
import org.folio.edge.core.utils.OkapiClientFactory;
import org.folio.edge.oaipmh.clients.OaiPmhOkapiClient;

import com.google.common.collect.Iterables;

import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import lombok.extern.slf4j.Slf4j;
import org.openarchives.oai._2.ListRecordsType;
import org.openarchives.oai._2.OAIPMH;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;

@Slf4j
public class OaiPmhHandler extends Handler {

  /** Expected valid http status codes to be returned by repository logic */
  private static final Set<Integer> EXPECTED_CODES = Set.of(SC_OK, SC_BAD_REQUEST, SC_NOT_FOUND, SC_UNPROCESSABLE_ENTITY, SC_SERVICE_UNAVAILABLE);
  private static final String ERROR_FROM_REPOSITORY = "Error in the response from repository: status code - %s, response status message - %s %s";

  private OaiPmhOkapiClient oaiPmhClient;

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
      log.warn("Provided accept headers are unsupported");
      return;
    }

    handleCommon(ctx, new String[0], new String[0] , (okapiClient, params) -> {
      oaiPmhClient = new OaiPmhOkapiClient(okapiClient);
      performCall(ctx, oaiPmhClient, null);
    });
  }

  private void performCall(RoutingContext ctx, OaiPmhOkapiClient client, String resumptionToken) {
    var request = ctx.request();
    log.debug("Client request: {} {}", request.method(), request.absoluteURI());

    ofNullable(resumptionToken).ifPresent(token -> {
      request.params().set(RESUMPTION_TOKEN, token);
      request.params().remove(METADATA_PREFIX);
    });
    client.call(request.params(), request.headers(),
      response -> handleProxyResponse(ctx, response),
      throwable -> oaiPmhFailureHandler(ctx, throwable));
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
   * @param oaiPmhResponse populated http-response
   */
  @Override
  protected void handleProxyResponse(RoutingContext ctx, HttpResponse<Buffer> oaiPmhResponse) {
    HttpServerResponse edgeResponse = ctx.response();
    int httpStatusCode = oaiPmhResponse.statusCode();
    ctx.response().setStatusCode(oaiPmhResponse.statusCode());
    log.debug("httpsStatusCode in handleProxyResponse: {}", httpStatusCode);

    if (EXPECTED_CODES.contains(httpStatusCode)) {
      edgeResponse.putHeader(HttpHeaders.CONTENT_TYPE, TEXT_XML);
      // In case the repository logic already compressed the response, lets transfer header to avoid potential doubled compression
      Optional<String> encodingHeader = ofNullable(oaiPmhResponse.getHeader(String.valueOf(HttpHeaders.CONTENT_ENCODING)));
      encodingHeader.ifPresent(value -> edgeResponse.putHeader(HttpHeaders.CONTENT_ENCODING, value));
      Buffer buffer = oaiPmhResponse.body();
      var oaipmh = readOAIPMH(buffer);
      if (isListRecords(oaipmh) && isResumptionTokenOnly(oaipmh.getListRecords())) {
        performCall(ctx, oaiPmhClient, oaipmh.getListRecords().getResumptionToken().getValue());
      } else {
        edgeResponse.end(buffer);
        if (encodingHeader.isEmpty()) {
          log.debug("Returned oai-pmh response doesn't contain encoding header.");
        }
      }
    } else {
      var message = String.format(ERROR_FROM_REPOSITORY, oaiPmhResponse.statusCode(), oaiPmhResponse.statusMessage(), oaiPmhResponse.bodyAsString());
      log.error(message);
      if (!ctx.response().ended()) {
        ctx.response().setStatusCode(oaiPmhResponse.statusCode()).putHeader(HttpHeaders.CONTENT_TYPE, "text/plain").end(message);
      }
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
   * @param throwable   throwable object
   */
  private void oaiPmhFailureHandler(RoutingContext ctx, Throwable throwable) {
    log.error("Exception in calling OKAPI", throwable);
    if (throwable instanceof TimeoutException) {
      requestTimeout(ctx, throwable.getMessage());
    } else {
      internalServerError(ctx, throwable != null? throwable.getMessage(): "");
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

  private OAIPMH readOAIPMH(Buffer buffer) {
    try {
      return (OAIPMH) JAXBContext.newInstance(OAIPMH.class)
        .createUnmarshaller()
        .unmarshal(new ByteArrayInputStream(buffer.getBytes()));
    } catch (JAXBException e) {
      return new OAIPMH();
    }
  }

  private boolean isListRecords(OAIPMH oaipmh) {
    return nonNull(oaipmh.getListRecords());
  }

  private boolean isResumptionTokenOnly(ListRecordsType listRecordsType) {
    return listRecordsType.getRecords().isEmpty()
      && nonNull(listRecordsType.getResumptionToken())
      && !listRecordsType.getResumptionToken().getValue().isEmpty();
  }
}
