package org.folio.edge.oaipmh.clients.modconfiguration.impl;

import java.lang.invoke.MethodHandles;

import org.apache.commons.lang3.StringUtils;
import org.folio.edge.core.utils.OkapiClient;
import org.folio.edge.oaipmh.clients.modconfiguration.ConfigurationService;
import org.folio.rest.client.ConfigurationsClient;
import org.folio.rest.jaxrs.model.Configs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import lombok.extern.log4j.Log4j;

@Log4j
public class ModConfigurationService implements ConfigurationService {

  private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup()
    .lookupClass());

  private static final String MODULE_NAME = "OAIPMH";
  private static final String BEHAVIOR_CONFIG = "behavior";
  private static final String ERRORS_PROCESSING = "errorsProcessing";
  private static final String GENERAL_CONFIG = "general";
  private static final String ENABLE_OAI_SERVICE = "enableOaiService";
  private static final String CONFIG_ASSOSIATE_ERRORS_WITH_200 = "200";

  public Future<Boolean> getEnableOaiServiceConfigSetting(OkapiClient client) {
    return getConfigSettingValue(client, MODULE_NAME, GENERAL_CONFIG, ENABLE_OAI_SERVICE).map(Boolean::parseBoolean);
  }

  public Future<Boolean> associateErrorsWith200Status(OkapiClient client) {
    return getConfigSettingValue(client, MODULE_NAME, BEHAVIOR_CONFIG, ERRORS_PROCESSING)
      .map(setting -> StringUtils.isNotBlank(setting) && setting.equals(CONFIG_ASSOSIATE_ERRORS_WITH_200));
  }

  /**
   * This method make request to mod-configuration module and receive config setting
   *
   * @param okapiClient okapi client
   * @param moduleName  name of module
   * @param configName  name of configuration (currently available general and behavior)
   * @param value       name of configuration setting in value section (currently available enableOaiService and errorsProcessing)
   * @return value of configuration setting
   */
  private Future<String> getConfigSettingValue(OkapiClient okapiClient, String moduleName, String configName, String value) {

    final ConfigurationsClient configurationsClient = new ConfigurationsClient(okapiClient.okapiURL, okapiClient.tenant,
        okapiClient.getToken());
    Future<String> future = Future.future();
    final String s = buildQuery(moduleName, configName);

    try {
      final String msg = String.format("%s in tenant %s: Getting configuration: MODULE_NAME:%s, configName: %s, configValue: %s",
        okapiClient.okapiURL, okapiClient.tenant, moduleName, configName, value);
      log.debug(msg);
      configurationsClient.getConfigurationsEntries(s, 0, 3, null, null, response -> response.bodyHandler(body -> {
        if (response.statusCode() != 200) {
          future.fail(String.format("%s. Expected status code 200, got %d: %s", msg, response.statusCode(), body.toString()));
          return;
        }
        final String result = new JsonObject(body.toJsonObject()
          .mapTo(Configs.class)
          .getConfigs()
          .get(0)
          .getValue()).getString(value);

        future.complete(result);
      }));
    } catch (Exception e) {
      LOGGER.error("Error happened initializing mod-configurations client ", e);
      future.fail(e);
    }
    return future;
  }

  private String buildQuery(String module, String configName) {
    StringBuilder stringBuilder = new StringBuilder();
    stringBuilder.append("module=");
    stringBuilder.append(module);
    stringBuilder.append(" and ");
    stringBuilder.append("configName=");
    stringBuilder.append(configName);
    return stringBuilder.toString();
  }
}
