package org.folio.edge.oaipmh;

import static com.google.common.collect.ImmutableSet.of;

import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpHeaders;
import io.vertx.ext.web.RoutingContext;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import org.apache.log4j.Logger;
import org.folio.edge.core.Handler;
import org.folio.edge.core.security.SecureStore;
import org.folio.edge.core.utils.OkapiClientFactory;
import org.folio.edge.oaipmh.utils.Constants;
import org.folio.edge.oaipmh.utils.OaiPmhOkapiClient;

public class OaiPmhHandler extends Handler {

  private static final Logger logger = Logger.getLogger(OaiPmhHandler.class);
  private static final Set<Integer> EXPECTED_CODES = of(200, 400, 404, 422);

  public OaiPmhHandler(SecureStore secureStore, OkapiClientFactory ocf) {
    super(secureStore, ocf);
  }

  void handle(RoutingContext ctx) {
    super.handleCommon(ctx,
      new String[]{},
      new String[]{},
      (client, params) -> {
        final OaiPmhOkapiClient oaiPmhClient = new OaiPmhOkapiClient(client);
        oaiPmhClient
          .call(ctx.request().params(),
            ctx.request().headers(),
            response -> handleResponse(ctx, response),
            t -> handleException(ctx, t));
      });
  }

  /**
   * This method contains processing of oai-pmh-mod response in normal processing flow
   *
   * @param ctx routing context
   * @param response populated http-response
   */
  private void handleResponse(RoutingContext ctx, HttpClientResponse response) {
    final StringBuilder sb = new StringBuilder();
    response.handler(sb::append)
      .endHandler(v -> {
        int httpStatusCode = response.statusCode();
        String body = sb.toString();
        ctx.response().setStatusCode(httpStatusCode);
        if (EXPECTED_CODES.contains(httpStatusCode)) {
          logger.info(String.format(
            "Retrieved info from oai-pmh-mod: (%s) %s",
            httpStatusCode, body));
          ctx.response()
            .putHeader(HttpHeaders.CONTENT_TYPE, Constants.TEXT_XML_TYPE)
            .end(body);
        } else {
          logger.error(String.format(
            "Error in the oai-pmh-mod response: (%s) %s",
            httpStatusCode, body));
          internalServerError(ctx, "Internal Server Error");
        }
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
}
