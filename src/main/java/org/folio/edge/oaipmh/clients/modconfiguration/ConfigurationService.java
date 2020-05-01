package org.folio.edge.oaipmh.clients.modconfiguration;

import org.folio.edge.core.utils.OkapiClient;

import io.vertx.core.Future;

/**
 *  Interface for retrieving oai-pmh configurations from mod-configuration.
 */
public interface ConfigurationService {

  /**
   * This method make request to mod-configuration module and get enableOaiService configuration setting
   *
   * @param client configuration client which make request to mod-configuration
   * @return value of enableOaiService configuration setting
   */
  Future<Boolean> getEnableOaiServiceConfigSetting(OkapiClient client);

  /**
   * This method make request to mod-configuration module and get errorsProcessing configuration setting
   *
   * @param client configuration client which make request to mod-configuration
   * @return value of errorsProcessing configuration setting
   */
  Future<Boolean> associateErrorsWith200Status(OkapiClient client);
}
