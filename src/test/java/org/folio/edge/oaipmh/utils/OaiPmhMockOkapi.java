package org.folio.edge.oaipmh.utils;

import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.folio.edge.core.utils.test.MockOkapi;
import org.openarchives.oai._2.VerbType;

public class OaiPmhMockOkapi extends MockOkapi {

  static final String PATH_TO_GET_RECORDS_MOCK
    = "src/test/resources/mocks/GetRecordErrorResponse.xml";
  static final String PATH_TO_GET_RECORDS_ERROR_MOCK
    = "src/test/resources/mocks/GetRecordErrorResponse.xml";
  static final String PATH_TO_IDENTIFY_MOCK
    = "src/test/resources/mocks/IdentifyResponse.xml";

  public OaiPmhMockOkapi(int port, List<String> knownTenants) {
    super(port, knownTenants);
  }

  @Override
  public Router defineRoutes() {
    Router router = super.defineRoutes();
    router.route(HttpMethod.GET, "/oai/records/*").handler(this::oaiPmhHandler);
    router.route(HttpMethod.GET, "/oai/repository_info").handler(this::oaiPmhHandler);
    return router;
  }

  private void oaiPmhHandler(RoutingContext ctx) {

    HttpServerRequest request = ctx.request();
    String verb = request.getParam(Constants.VERB);
    String path = request.path();

    if (VerbType.GET_RECORD.value().equals(verb) && path.endsWith("oai%3AarXiv.org%3Aquant-ph%2F02131001")) {
      ctx.response()
        .setStatusCode(404)
        .putHeader(HttpHeaders.CONTENT_TYPE, Constants.TEXT_XML_TYPE)
        .end(getOaiPmhResponseAsXml(Paths.get(PATH_TO_GET_RECORDS_MOCK)));
    } else if(VerbType.GET_RECORD.value().equals(verb) && path.endsWith("oai%3AarXiv.org%3Acs%2F0112017")) {
      ctx.response()
        .setStatusCode(200)
        .putHeader(HttpHeaders.CONTENT_TYPE, Constants.TEXT_XML_TYPE)
        .end(getOaiPmhResponseAsXml(Paths.get(PATH_TO_GET_RECORDS_ERROR_MOCK)));
    } else if(VerbType.IDENTIFY.value().equals(verb)) {
      ctx.response()
        .setStatusCode(200)
        .putHeader(HttpHeaders.CONTENT_TYPE, Constants.TEXT_XML_TYPE)
        .end(getOaiPmhResponseAsXml(Paths.get(PATH_TO_IDENTIFY_MOCK)));
    }
  }

  static String getOaiPmhResponseAsXml(Path pathToXmlFile) {
    String xml = null;
    try {
      xml = new String(Files.readAllBytes(pathToXmlFile));
    } catch (IOException e) {
      e.printStackTrace();
    }
    return xml;
  }
}




