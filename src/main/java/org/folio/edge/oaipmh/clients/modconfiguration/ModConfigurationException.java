package org.folio.edge.oaipmh.clients.modconfiguration;

/**
 * Indicates exception in communicating with the mod-configuration
 */
public class ModConfigurationException extends RuntimeException {

  public ModConfigurationException(String message) {
    super(message);
  }
}
