package org.folio.edge.oaipmh.clients.modconfiguration.impl;

import org.apache.commons.lang3.StringUtils;
import org.folio.edge.core.utils.OkapiClient;
import org.folio.edge.oaipmh.clients.modconfiguration.ConfigurationService;
import org.folio.edge.oaipmh.clients.modconfiguration.ModConfigurationException;
import org.folio.rest.client.ConfigurationsClient;
import org.folio.rest.jaxrs.model.Configs;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ModConfigurationService implements ConfigurationService {

  private static final String MODULE_NAME = "OAIPMH";
  private static final String BEHAVIOR_CONFIG = "behavior";
  private static final String ERRORS_PROCESSING = "errorsProcessing";
  private static final String GENERAL_CONFIG = "general";
  private static final String ENABLE_OAI_SERVICE = "enableOaiService";
  private static final String CONFIG_ASSOCIATE_ERRORS_WITH_200 = "200";

  public Future<Boolean> getEnableOaiServiceConfigSetting(OkapiClient client) {
    return getConfigSettingValue(client, GENERAL_CONFIG, ENABLE_OAI_SERVICE).map(Boolean::parseBoolean)
      .otherwise(Boolean.TRUE);
  }

  public Future<Boolean> associateErrorsWith200Status(OkapiClient client) {
    return getConfigSettingValue(client, BEHAVIOR_CONFIG, ERRORS_PROCESSING)
      .map(setting -> StringUtils.isNotBlank(setting) && setting.equals(CONFIG_ASSOCIATE_ERRORS_WITH_200))
      .otherwise(Boolean.FALSE);
  }

  /**
   * This method make request to mod-configuration module and receive config setting
   *
   * @param okapiClient okapi client
   * @param configName  name of configuration (currently available general and behavior)
   * @param value       name of configuration setting in value section (currently available enableOaiService and errorsProcessing)
   * @return value of configuration setting
   */
  private Future<String> getConfigSettingValue(OkapiClient okapiClient, String configName, String value) {

    final ConfigurationsClient configurationsClient = new ConfigurationsClient(okapiClient.okapiURL, okapiClient.tenant,
        okapiClient.getToken());
    Future<String> future = Future.future();
    final String query = buildQuery(configName);
    final String msg = String.format("Querying setting: %s: %s?query=%s in tenant %s", value, okapiClient.okapiURL, query,
        okapiClient.tenant);
    log.debug(msg);
    try {
      configurationsClient.getConfigurationsEntries(query, 0, 3, null, null, response -> response.bodyHandler(body -> {
        if (response.statusCode() != 200) {
          String message = String.format("%s. Expected status code 200, got %d: %s", msg, response.statusCode(), body.toString());
          throw new ModConfigurationException(message);
        }
        String result = new JsonObject(body.toJsonObject()
          .mapTo(Configs.class)
          .getConfigs()
          .get(0)
          .getValue()).getString(value);
        if (result == null) {
          throw new ModConfigurationException("Could not find configuration setting " + msg);
        }
        future.complete(result);
      })
        .exceptionHandler(t -> {
          log.error("ERROR in communicating with mod-configuration", t);
          future.fail(msg);
        }));
    } catch (Exception e) {
      log.error("ERROR in communicating with mod-configuration", e);
      future.fail(msg);
    }
    return future;
  }

  private String buildQuery(String configName) {
    return String.format("module=%s and configName=%s", MODULE_NAME, configName);
  }
}
