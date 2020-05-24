package org.folio.edge.oaipmh.utils;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;

import org.openarchives.oai._2.OAIPMH;

import lombok.extern.slf4j.Slf4j;

/**
 * Helper class to marshal {@link OAIPMH} object to string representation.
 * The class is made singleton to avoid multiple heavy JAXBContext initializations.
 */
@Slf4j
public class ResponseHelper {
  private static ResponseHelper ourInstance = new ResponseHelper();

  private Marshaller jaxbMarshaller;
  private Unmarshaller jaxbUnmarshaller;

  public static ResponseHelper getInstance() {
    return ourInstance;
  }

  private ResponseHelper() {
    try {
      JAXBContext jaxbContext = JAXBContext.newInstance(OAIPMH.class);
      jaxbMarshaller = jaxbContext.createMarshaller();
      jaxbUnmarshaller = jaxbContext.createUnmarshaller();

      // Specifying xsi:schemaLocation (which will trigger xmlns:xsi being added to RS as well)
      jaxbMarshaller.setProperty(Marshaller.JAXB_SCHEMA_LOCATION,
        "http://www.openarchives.org/OAI/2.0/ http://www.openarchives.org/OAI/2.0/OAI-PMH.xsd");

      // Specifying if output should be formatted
      jaxbMarshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.parseBoolean(System.getProperty("jaxb.marshaller.formattedOutput")));
    } catch (JAXBException e) {
      log.error("Unable to create ResponseWriter", e);
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

  /**
   * Unmarshals {@link OAIPMH} object based on passed string
   * @param oaipmhResponse the {@link OAIPMH} response in string representation
   * @return the {@link OAIPMH} object based on passed string
   * @throws JAXBException in case passed string is not valid OAIPMH representation
   */
  public OAIPMH stringToOaiPmh(String oaipmhResponse) throws JAXBException {
    return (OAIPMH) jaxbUnmarshaller.unmarshal(new StringReader(oaipmhResponse));
  }
}
