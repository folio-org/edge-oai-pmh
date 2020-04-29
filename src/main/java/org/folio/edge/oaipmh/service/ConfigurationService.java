package org.folio.edge.oaipmh.service;

import java.util.concurrent.CompletableFuture;

import org.folio.rest.client.ConfigurationsClient;

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
  CompletableFuture<String> getEnableOaiServiceConfigSetting(ConfigurationsClient client);

  /**
   * This method make request to mod-configuration module and get errorsProcessing configuration setting
   *
   * @param client configuration client which make request to mod-configuration
   * @return value of errorsProcessing configuration setting
   */
  CompletableFuture<String> getErrorsProcessingConfigSetting(ConfigurationsClient client);
}
