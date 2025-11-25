package org.folio.edge.oaipmh.clients;

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import lombok.extern.slf4j.Slf4j;
import org.folio.edge.core.utils.OkapiClient;
import org.folio.rest.jaxrs.model.ConfigurationSettings;
import org.folio.rest.jaxrs.model.ConfigurationSettingsCollection;

/**
 * Client for interacting with the OAI-PMH configuration-settings API.
 */
@Slf4j
public class ConfigurationSettingsClient extends OkapiClient {
  private static final String CONFIGURATION_SETTINGS_ENDPOINT = "/oai-pmh/configuration-settings";

  public ConfigurationSettingsClient(OkapiClient client) {
    super(client);
  }

  /**
   * Get all configuration settings.
   *
   * @param headers HTTP headers to include in the request
   * @return Future containing the configuration settings collection
   */
  public Future<ConfigurationSettingsCollection> getConfigurationSettings(MultiMap headers) {
    return get(okapiURL + CONFIGURATION_SETTINGS_ENDPOINT, tenant, headers)
          .map(resp -> resp.bodyAsJson(ConfigurationSettingsCollection.class));
  }

  /**
   * Get configuration settings filtered by name.
   *
   * @param name    configuration name to filter by
   * @param headers HTTP headers to include in the request
   * @return Future containing the configuration settings collection
   */
  public Future<ConfigurationSettingsCollection> getConfigurationSettingsByName(String name, MultiMap headers) {
    String url = String.format("%s%s?name=%s", okapiURL, CONFIGURATION_SETTINGS_ENDPOINT, name);
    return get(url, tenant, headers)
          .map(resp -> resp.bodyAsJson(ConfigurationSettingsCollection.class));
  }

  /**
   * Get a specific configuration setting by ID.
   *
   * @param id      configuration setting ID
   * @param headers HTTP headers to include in the request
   * @return Future containing the configuration setting
   */
  public Future<ConfigurationSettings> getConfigurationSettingById(String id, MultiMap headers) {
    String url = String.format("%s%s/%s", okapiURL, CONFIGURATION_SETTINGS_ENDPOINT, id);
    return get(url, tenant, headers)
          .map(resp -> resp.bodyAsJson(ConfigurationSettings.class));
  }


}
