package org.folio.edge.oaipmh;

import org.openarchives.oai._2.OAIPMH;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;

import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Helper class to marshal {@link OAIPMH} object to string representation.
 * The class is made singleton to avoid multiple heavy JAXBContext initializations.
 */
public class ResponseHelper {
  private static final Logger logger = LoggerFactory.getLogger(ResponseHelper.class);
  private static ResponseHelper ourInstance = new ResponseHelper();

  private Marshaller jaxbMarshaller;

  public static ResponseHelper getInstance() {
    return ourInstance;
  }

  private ResponseHelper() {
    try {
      JAXBContext jaxbContext = JAXBContext.newInstance(OAIPMH.class);
      jaxbMarshaller = jaxbContext.createMarshaller();

      // Specifying xsi:schemaLocation (which will trigger xmlns:xsi being added to RS as well)
      jaxbMarshaller.setProperty(Marshaller.JAXB_SCHEMA_LOCATION,
        "http://www.openarchives.org/OAI/2.0/ http://www.openarchives.org/OAI/2.0/OAI-PMH.xsd");

      // Specifying if output should be formatted
      jaxbMarshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.parseBoolean(System.getProperty("jaxb.marshaller.formattedOutput")));
    } catch (JAXBException e) {
      logger.error("Unable to create ResponseWriter", e);
      jaxbMarshaller = null;
    }
  }

  /**
   * Marshal given Jaxb OAI-PMH object to string.
   *
   * @param response {@link OAIPMH} object to marshal
   * @return marshaled {@link OAIPMH} object as string representation
   * @throws UnsupportedEncodingException we are using UTF-8 so the exception should never be thrown
   * @throws JAXBException can be thrown, for example, if the {@link OAIPMH} object is invalid
   */
  public String writeToString(OAIPMH response) throws UnsupportedEncodingException, JAXBException {
    if (jaxbMarshaller == null) {
      throw new IllegalStateException("Marshaller is not available");
    }

    final ByteArrayOutputStream baos = new ByteArrayOutputStream();
    jaxbMarshaller.marshal(response, baos);

    return baos.toString(UTF_8.toString());
  }
}
