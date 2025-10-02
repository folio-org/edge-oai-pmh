package org.folio.edge.oaipmh.utils;

import com.sun.xml.bind.marshaller.NamespacePrefixMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openarchives.oai._2.OAIPMH;

@SuppressWarnings("squid:S1191") // The com.sun.xml.bind.marshaller.NamespacePrefixMapper
// is part of jaxb logic
public class ResponseConverter {

  private static final Logger logger = LogManager.getLogger(ResponseConverter.class);
  private static final Map<String, String> NAMESPACE_PREFIX_MAP = new HashMap<>();
  private final NamespacePrefixMapper namespacePrefixMapper;

  private static final ResponseConverter ourInstance;

  static {
    NAMESPACE_PREFIX_MAP.put("http://www.loc.gov/MARC21/slim", "marc");
    NAMESPACE_PREFIX_MAP.put("http://purl.org/dc/elements/1.1/", "dc");
    NAMESPACE_PREFIX_MAP.put("http://www.openarchives.org/OAI/2.0/oai_dc/", "oai_dc");
    NAMESPACE_PREFIX_MAP.put("http://www.openarchives.org/OAI/2.0/oai-identifier",
          "oai-identifier");
    try {
      ourInstance = new ResponseConverter();
    } catch (JAXBException e) {
      logger.error("The jaxb context could not be initialized.");
      throw new IllegalStateException("Marshaller and unmarshaller are not available.", e);
    }
  }

  private final JAXBContext jaxbContext;

  public static ResponseConverter getInstance() {
    return ourInstance;
  }

  /**
   * The main purpose is to initialize JAXB Marshaller and Unmarshaller to use
   * the instances for business logic operations.
   */
  private ResponseConverter() throws JAXBException {
    jaxbContext = JAXBContext.newInstance(OAIPMH.class);
    namespacePrefixMapper = new NamespacePrefixMapper() {
      @Override
      public String getPreferredPrefix(String namespaceUri, String suggestion,
                                       boolean requirePrefix) {
        return NAMESPACE_PREFIX_MAP.getOrDefault(namespaceUri, suggestion);
      }
    };
  }

  /**
   * Marshals {@link OAIPMH} object and returns string representation.
   *
   * @param oaipmh {@link OAIPMH} object to marshal
   * @return marshaled {@link OAIPMH} object as string representation
   */
  public String convertToString(OAIPMH oaipmh) {
    try (var stream = new ByteArrayOutputStream()) {
      Marshaller jaxbMarshaller = jaxbContext.createMarshaller();
      jaxbMarshaller.setProperty(Marshaller.JAXB_SCHEMA_LOCATION,
          "http://www.openarchives.org/OAI/2.0/ http://www.openarchives.org/OAI/2.0/OAI-PMH.xsd");
      jaxbMarshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
      jaxbMarshaller.setProperty("com.sun.xml.bind.namespacePrefixMapper", namespacePrefixMapper);
      jaxbMarshaller.marshal(oaipmh, stream);
      return stream.toString();
    } catch (JAXBException | IOException e) {
      throw new IllegalStateException("The OAI-PMH response cannot be converted to "
            + "string representation.", e);
    }
  }

  /**
   * Unmarshals {@link OAIPMH} object based on passed string.
   *
   * @param oaipmhResponse the {@link OAIPMH} response in string representation
   * @return the {@link OAIPMH} object based on passed string
   */
  public OAIPMH toOaiPmh(String oaipmhResponse) {
    try (StringReader reader = new StringReader(oaipmhResponse)) {
      Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
      return (OAIPMH) jaxbUnmarshaller.unmarshal(reader);
    } catch (JAXBException e) {
      throw new IllegalStateException("The string cannot be converted to OAI-PMH response.", e);
    }
  }
}
