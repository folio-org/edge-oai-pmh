package org.folio.edge.oaipmh;

import static io.vertx.core.http.HttpHeaders.ACCEPT;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static java.util.Optional.ofNullable;
import static org.apache.commons.lang3.ObjectUtils.isEmpty;
import static org.apache.commons.lang3.ObjectUtils.isNotEmpty;
import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.apache.http.HttpStatus.SC_NOT_FOUND;
import static org.apache.http.HttpStatus.SC_OK;
import static org.apache.http.HttpStatus.SC_SERVICE_UNAVAILABLE;
import static org.apache.http.HttpStatus.SC_UNPROCESSABLE_ENTITY;
import static org.folio.edge.core.Constants.TEXT_XML;
import static org.folio.edge.oaipmh.utils.Constants.CENTRAL_TENANT_ID;
import static org.folio.edge.oaipmh.utils.Constants.FROM;
import static org.folio.edge.oaipmh.utils.Constants.LIST_IDENTIFIERS;
import static org.folio.edge.oaipmh.utils.Constants.LIST_RECORDS;
import static org.folio.edge.oaipmh.utils.Constants.METADATA_PREFIX;
import static org.folio.edge.oaipmh.utils.Constants.RESUMPTION_TOKEN;
import static org.folio.edge.oaipmh.utils.Constants.TENANT_ID;
import static org.folio.edge.oaipmh.utils.Constants.UNTIL;
import static org.folio.edge.oaipmh.utils.Constants.VERB;
import static org.folio.edge.oaipmh.utils.ResumptionTokenUtils.buildNewResumptionToken;
import static org.folio.edge.oaipmh.utils.ResumptionTokenUtils.parseResumptionToken;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.buffer.impl.BufferImpl;
import io.vertx.ext.web.client.HttpResponse;
import org.folio.edge.core.Handler;
import org.folio.edge.core.cache.Cache;
import org.folio.edge.core.model.ClientInfo;
import org.folio.edge.core.security.SecureStore;
import org.folio.edge.core.utils.ApiKeyUtils;
import org.folio.edge.core.utils.OkapiClient;
import org.folio.edge.core.utils.OkapiClientFactory;
import org.folio.edge.oaipmh.clients.ConsortiaTenantClient;
import org.folio.edge.oaipmh.clients.OaiPmhOkapiClient;

import com.google.common.collect.Iterables;

import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import lombok.extern.slf4j.Slf4j;
import org.folio.edge.oaipmh.utils.ResponseConverter;
import org.openarchives.oai._2.ListRecordsType;
import org.openarchives.oai._2.OAIPMH;
import org.openarchives.oai._2.ResumptionTokenType;

@Slf4j
public class OaiPmhHandler extends Handler {
  /** Expected valid http status codes to be returned by repository logic */
  private static final Set<Integer> EXPECTED_CODES = Set.of(SC_OK, SC_BAD_REQUEST, SC_NOT_FOUND, SC_UNPROCESSABLE_ENTITY, SC_SERVICE_UNAVAILABLE);
  private static final String ERROR_FROM_REPOSITORY = "Error in the response from repository: status code - %s, response status message - %s %s";

  private Cache<List<String>> tenantsCache;

  public OaiPmhHandler(SecureStore secureStore, OkapiClientFactory ocf) {
    super(secureStore, ocf);
    tenantsCache = new Cache.Builder<List<String>>()
      .withTTL(TimeUnit.HOURS.toMillis(1))
      .withNullValueTTL(0)
      .withCapacity(100)
      .build();
  }

  protected void handle(RoutingContext ctx) {
    HttpServerRequest request = ctx.request();
    log.debug("Client request: {} {}", request.method(), request.absoluteURI());
    log.debug("Client request parameters: " + request.params());
    log.debug("Client request headers: " + Iterables.toString(request.headers()));

    if (!supportedAcceptHeaders(request)) {
      notAcceptableResponse(ctx, request);
      log.error("Provided accept headers are unsupported");
      return;
    }

    handleCommon(ctx, new String[0], new String[0] , (okapiClient, params) -> {
      if (isListRequest(ctx)) {
        getTenants(okapiClient)
          .thenAccept(list -> {
            if (isEmpty(list)) {
              notFound(ctx, "Tenants list is absent or empty");
            } else if (isSingleTenantHarvesting(list)) {
              new OaiPmhOkapiClient(okapiClient).call(request.params(), request.headers(),
                response -> handleProxyResponse(ctx, response),
                throwable -> oaiPmhFailureHandler(ctx, throwable));
            } else {
              ctx.request().params().set(CENTRAL_TENANT_ID, okapiClient.tenant);
              performMultiTenantHarvesting(ctx, list.get(0));
            }
          });
      } else {
        new OaiPmhOkapiClient(okapiClient).call(request.params(), request.headers(),
          response -> handleProxyResponse(ctx, response),
          throwable -> oaiPmhFailureHandler(ctx, throwable));
      }
    });
  }

  private boolean isListRequest(RoutingContext ctx) {
    return Set.of(LIST_RECORDS, LIST_IDENTIFIERS).contains(ctx.request().getParam(VERB));
  }

  private CompletableFuture<List<String>> getTenants(OkapiClient okapiClient) {
    var res = tenantsCache.get(okapiClient.tenant);
    if (isEmpty(res)) {
      return new ConsortiaTenantClient(okapiClient).getConsortiaTenants(null)
        .toCompletionStage().toCompletableFuture()
        .thenApply(l -> tenantsCache.put(okapiClient.tenant, l).value)
        .exceptionally(throwable -> {
          log.info("Returning current tenant instead of tenants list, reason: {}", throwable.getMessage());
          return Collections.singletonList(okapiClient.tenant);
        });
    }
    return CompletableFuture.completedFuture(res);
  }

  private boolean isSingleTenantHarvesting(List<String> tenants) {
    return tenants.size() == 1;
  }

  private boolean shouldStartHarvestingForNextTenant(Map<String, String> params) {
    return (params.containsKey(METADATA_PREFIX) && params.containsKey(TENANT_ID))
      && (params.size() == 2
        || (params.size() == 3 && (params.containsKey(FROM) || params.containsKey(UNTIL)))
        || (params.size() == 4 && params.containsKey(FROM) && params.containsKey(UNTIL)));
  }

  private void performMultiTenantHarvesting(RoutingContext ctx, String tenantId) {
    var request = ctx.request();
    if (isFirstRequest(request)) {
      ctx.request().params().set(TENANT_ID, tenantId);
      callToTenant(ctx, tenantId);
    } else {
      var resumptionTokenParams = parseResumptionToken(request.params().get(RESUMPTION_TOKEN));
      if (shouldStartHarvestingForNextTenant(resumptionTokenParams)) {
        request.params().remove(RESUMPTION_TOKEN);
        request.params().set(METADATA_PREFIX, resumptionTokenParams.get(METADATA_PREFIX));
        ofNullable(resumptionTokenParams.get(FROM)).ifPresent(value -> request.params().set(FROM, value));
        ofNullable(resumptionTokenParams.get(UNTIL)).ifPresent(value -> request.params().set(UNTIL, value));
      }
      var nextTenantId = resumptionTokenParams.get(TENANT_ID);
      ctx.request().params().set(TENANT_ID, nextTenantId);
      callToTenant(ctx, nextTenantId);
    }
  }

  private void callToTenant(RoutingContext ctx, String tenant) {
    var request = ctx.request();
    getClient(ctx, tenant)
      .thenAccept(client -> new OaiPmhOkapiClient(client).call(request.params(), request.headers(),
        response -> handleProxyResponse(ctx, response),
        throwable -> oaiPmhFailureHandler(ctx, throwable)));
  }

  private Optional<String> getNextTenant(List<String> list, String currentTenantId) {
    log.info("Tenants list: {}", list);
    var nextTenantIndex = list.indexOf(currentTenantId) + 1;
    if (list.size() > nextTenantIndex) {
      log.info("Next tenant: {}", list.get(nextTenantIndex));
      return Optional.of(list.get(nextTenantIndex));
    }
    return Optional.empty();
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

    if (EXPECTED_CODES.contains(httpStatusCode)) {
      edgeResponse.putHeader(HttpHeaders.CONTENT_TYPE, TEXT_XML);
      // In case the repository logic already compressed the response, lets transfer header to avoid potential doubled compression
      Optional<String> encodingHeader = ofNullable(oaiPmhResponse.getHeader(String.valueOf(HttpHeaders.CONTENT_ENCODING)));
      encodingHeader.ifPresent(value -> edgeResponse.putHeader(HttpHeaders.CONTENT_ENCODING, value));
      Buffer buffer = oaiPmhResponse.body();
      var oaipmh = ResponseConverter.getInstance().toOaiPmh(buffer.toString());
      if (isListRecords(oaipmh) && isResumptionTokenOnly(oaipmh.getListRecords())) {
        processEmptyListResponse(ctx, oaipmh);
      } else if (isLastResponse(oaipmh)) {
        log.info("Processing last response for list verb...");
        processLastResponse(ctx, edgeResponse, oaipmh, buffer);
      } else if (isListRequest(ctx) && isErrorResponse(oaipmh)) {
        processErrorResponse(ctx, edgeResponse, buffer);
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

  private void processEmptyListResponse(RoutingContext ctx, OAIPMH oaipmh) {
    var request = ctx.request();
    var resumptionToken = oaipmh.getListRecords().getResumptionToken().getValue();
    request.params().set(RESUMPTION_TOKEN, resumptionToken);
    request.params().remove(METADATA_PREFIX);
    callToTenant(ctx, parseResumptionToken(resumptionToken).get(TENANT_ID));
  }

  private void processLastResponse(RoutingContext ctx, HttpServerResponse edgeResponse, OAIPMH oaipmh, Buffer buffer) {
    var requestParams = ctx.request().params();
    log.info("Last response, central tenant={}, current tenant={}", requestParams.get(CENTRAL_TENANT_ID), requestParams.get(TENANT_ID));
    if (isMultiTenantHarvesting(requestParams)) {
      getNextTenant(ctx, requestParams.get(CENTRAL_TENANT_ID), requestParams.get(TENANT_ID))
        .thenAccept(optionalNextTenant -> {
          if (optionalNextTenant.isPresent()) {
            updateResumptionTokenValue(oaipmh, optionalNextTenant.get());
            edgeResponse.end(BufferImpl.buffer(ResponseConverter.getInstance().convertToString(oaipmh)));
          } else {
            edgeResponse.end(buffer);
          }
        });
    } else {
      edgeResponse.end(buffer);
    }
  }

  private void processErrorResponse(RoutingContext ctx, HttpServerResponse edgeResponse, Buffer buffer) {
    var requestParams = ctx.request().params();
    log.info("Error response, central tenant={}, current tenant={}", requestParams.get(CENTRAL_TENANT_ID), requestParams.get(TENANT_ID));
    if (isMultiTenantHarvesting(requestParams)) {
      getNextTenant(ctx, requestParams.get(CENTRAL_TENANT_ID), requestParams.get(TENANT_ID))
        .thenAccept(optionalNextTenant -> {
          if (optionalNextTenant.isPresent()) {
            var nextTenant = optionalNextTenant.get();
            requestParams.set(TENANT_ID, nextTenant);
            callToTenant(ctx, nextTenant);
          } else {
            edgeResponse.end(buffer);
          }
        });
    } else {
      edgeResponse.end(buffer);
    }
  }

  private boolean isMultiTenantHarvesting(MultiMap requestParams) {
    return nonNull(requestParams.get(CENTRAL_TENANT_ID)) && nonNull(requestParams.get(TENANT_ID));
  }

  private CompletableFuture<Optional<String>> getNextTenant(RoutingContext ctx, String initialTenantId, String currentTenantId) {
    return getClient(ctx, initialTenantId)
      .thenCompose(this::getTenants)
      .thenApply(list -> getNextTenant(list, currentTenantId));
  }

  private void updateResumptionTokenValue(OAIPMH oaipmh, String nextTenant) {
    var newResumptionTokenValue = buildNewResumptionToken(oaipmh, nextTenant);
    if (isListRecords(oaipmh)) {
      var listRecords = oaipmh.getListRecords();
      listRecords.setResumptionToken(isNull(listRecords.getResumptionToken()) ?
        new ResumptionTokenType().withValue(newResumptionTokenValue) :
        listRecords.getResumptionToken().withValue(newResumptionTokenValue));
    } else {
      var listIdentifiers = oaipmh.getListIdentifiers();
      listIdentifiers.setResumptionToken(isNull(listIdentifiers.getResumptionToken()) ?
        new ResumptionTokenType().withValue(newResumptionTokenValue) :
        listIdentifiers.getResumptionToken().withValue(newResumptionTokenValue));
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

  private boolean isListRecords(OAIPMH oaipmh) {
    return nonNull(oaipmh.getListRecords());
  }

  private boolean isResumptionTokenOnly(ListRecordsType listRecordsType) {
    return listRecordsType.getRecords().isEmpty()
      && nonNull(listRecordsType.getResumptionToken())
      && !listRecordsType.getResumptionToken().getValue().isEmpty();
  }

  private boolean isFirstRequest(HttpServerRequest request) {
    return isNull(request.params().get(RESUMPTION_TOKEN));
  }

  private boolean isLastResponse(OAIPMH oaipmh) {
    return (nonNull(oaipmh.getListRecords()) && (isNull(oaipmh.getListRecords().getResumptionToken()) || oaipmh.getListRecords().getResumptionToken().getValue().isEmpty()))
      || (nonNull(oaipmh.getListIdentifiers()) && (isNull(oaipmh.getListIdentifiers().getResumptionToken()) || oaipmh.getListIdentifiers().getResumptionToken().getValue().isEmpty()));
  }

  private boolean isErrorResponse(OAIPMH oaipmh) {
    return isNotEmpty(oaipmh.getErrors());
  }

  private CompletableFuture<OkapiClient> getClient(RoutingContext ctx, String tenantId) {
    var key = keyHelper.getApiKey(ctx);
    ClientInfo clientInfo;
    try {
      clientInfo = ApiKeyUtils.parseApiKey(key);
    } catch (ApiKeyUtils.MalformedApiKeyException e) {
      return CompletableFuture.failedFuture(e);
    }
    final OkapiClient client = ocf.getOkapiClient(tenantId);
    return iuHelper.fetchToken(client, clientInfo.salt, tenantId, clientInfo.username)
      .map(token -> client).toCompletionStage().toCompletableFuture();
  }
}
