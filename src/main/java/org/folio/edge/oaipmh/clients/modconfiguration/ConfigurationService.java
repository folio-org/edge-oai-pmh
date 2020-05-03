package org.folio.edge.oaipmh.clients.modconfiguration;

import org.folio.edge.core.utils.OkapiClient;

import io.vertx.core.Future;

/**
 *  Interface for retrieving oai-pmh configurations from mod-configuration.
 *  In order to preserve the "old" functionality if the configurations are not present,
 *  or there are any issues with mod-configuration,the implementations should
 *  provide default values in case of any errors.
 *
 */
public interface ConfigurationService {

  /**
   * This method make request to mod-configuration module and get enableOaiService configuration setting.
   * If any errors occur, the default fallback should be TRUE
   *
   * @param client configuration client which make request to mod-configuration
   * @return value of enableOaiService configuration setting
   */
  Future<Boolean> getEnableOaiServiceConfigSetting(OkapiClient client);

  /**
   * This method make request to mod-configuration module and get errorsProcessing configuration setting.
   * If any errors occur, the default fallback value should be FALSE
   *
   * @param client configuration client which make request to mod-configuration
   * @return value of errorsProcessing configuration setting.
   */
  Future<Boolean> associateErrorsWith200Status(OkapiClient client);
}
