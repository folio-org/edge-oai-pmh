package org.folio.edge.oaipmh;

import io.vertx.core.http.HttpHeaders;
import io.vertx.ext.web.RoutingContext;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.folio.edge.core.Handler;
import org.folio.edge.core.security.SecureStore;
import org.folio.edge.core.utils.OkapiClientFactory;
import org.openarchives.oai._2.OAIPMH;
import org.openarchives.oai._2.OAIPMHerrorType;
import org.openarchives.oai._2.OAIPMHerrorcodeType;
import org.openarchives.oai._2.RequestType;
import org.openarchives.oai._2.VerbType;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.folio.edge.oaipmh.Constants.TEXT_XML;
import static org.folio.edge.oaipmh.Constants.VERB;
import static org.openarchives.oai._2.OAIPMHerrorcodeType.BAD_ARGUMENT;
import static org.openarchives.oai._2.OAIPMHerrorcodeType.BAD_VERB;

public class OaiPmhHandler extends Handler {

  private static final Logger logger = Logger.getLogger(OaiPmhHandler.class);

  public OaiPmhHandler(SecureStore secureStore, OkapiClientFactory ocf) {
    super(secureStore, ocf);
  }

  protected void handle(RoutingContext ctx) {
    Verb verb = Verb.fromName(ctx.request().getParam(VERB));
    if (verb == null) {
      badRequest(ctx,
        "Bad verb. Verb '" + ctx.request().getParam(VERB) + "' is not implemented", null, BAD_VERB);
      return;
    }

    List<OAIPMHerrorType> errors = verb.validate(ctx);
    if (!errors.isEmpty()) {
      badRequest(ctx, verb.toString(), errors);
      return;
    }

    String[] requiredParams;
    if (verb.getExclusiveParam() != null && ctx.request().getParam(verb.getExclusiveParam()) != null) {
      requiredParams = new String[]{verb.getExclusiveParam()};
    } else {
      requiredParams = verb.getRequiredParams().toArray(new String[verb.getRequiredParams().size()]);
    }

    super.handleCommon(ctx,
      requiredParams,
      verb.getOptionalParams().toArray(new String[verb.getOptionalParams().size()]),
      (client, params) -> {
        // business logic is here
      }
    );
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

  private OAIPMH buildBaseResponse(RoutingContext ctx, String verb) {
    String baseUrl = StringUtils.substringBefore(ctx.request().absoluteURI(), "?");
    return new OAIPMH()
      .withResponseDate(Instant.now().truncatedTo(ChronoUnit.SECONDS))
      .withRequest(new RequestType()
        .withVerb(isEmpty(verb) ? null : VerbType.fromValue(verb))
        .withValue(baseUrl));
  }
}
