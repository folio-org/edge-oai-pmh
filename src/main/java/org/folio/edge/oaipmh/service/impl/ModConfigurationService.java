package org.folio.edge.oaipmh.service.impl;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.CompletableFuture;

import org.apache.http.HttpStatus;
import org.folio.edge.oaipmh.service.ConfigurationService;
import org.folio.rest.client.ConfigurationsClient;
import org.folio.rest.jaxrs.model.Configs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.json.JsonObject;
import me.escoffier.vertx.completablefuture.VertxCompletableFuture;

public class ModConfigurationService implements ConfigurationService {

  private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup()
    .lookupClass());

  private static final String MODULE_NAME = "OAIPMH";
  private static final String BEHAVIOR_CONFIG = "behavior";
  private static final String ERRORS_PROCESSING = "errorsProcessing";
  private static final String GENERAL_CONFIG = "general";
  private static final String ENABLE_OAI_SERVICE = "enableOaiService";

  public CompletableFuture<String> getEnableOaiServiceConfigSetting(ConfigurationsClient client) {
    return getConfigSettingValue(client, MODULE_NAME, GENERAL_CONFIG, ENABLE_OAI_SERVICE);
  }

  public CompletableFuture<String> getErrorsProcessingConfigSetting(ConfigurationsClient client) {
    return getConfigSettingValue(client, MODULE_NAME, BEHAVIOR_CONFIG, ERRORS_PROCESSING);
  }

  /**
   * This method make request to mod-configuration module and receive config setting
   *
   * @param client     configuration client which make request to mod-congiguration
   * @param moduleName name of module
   * @param configName name of configuration (currently available general and behavior)
   * @param value      name of configuration setting in value section (currently available enableOaiService and errorsProcessing)
   * @return value of configuration setting
   */
  private CompletableFuture<String> getConfigSettingValue(ConfigurationsClient client, String moduleName, String configName,
      String value) {
    final int configNumber = 0;
    CompletableFuture<String> future = new VertxCompletableFuture<>();
    try {
      client.getConfigurationsEntries(buildQuery(moduleName, configName), 0, 100, null, null,
          responseConfig -> responseConfig.bodyHandler(body -> {
            try {
              if (responseConfig.statusCode() != HttpStatus.SC_OK) {
                LOGGER.error("Error getting configuration " + responseConfig.statusMessage());
                future.complete(String.valueOf(responseConfig.statusCode()));
                return;
              }
              future.complete(new JsonObject(body.toJsonObject()
                .mapTo(Configs.class)
                .getConfigs()
                .get(configNumber)
                .getValue()).getString(value));
            } catch (Exception exc) {
              LOGGER.error("Error when get configuration value from mod-configurations client response ", exc);
              future.completeExceptionally(exc);
            }
          }));
    } catch (Exception e) {
      LOGGER.error("Error happened initializing mod-configurations client ", e);
      future.completeExceptionally(e);
    }
    return future;
  }

  private String buildQuery(String module, String configName) {
    StringBuilder stringBuilder = new StringBuilder();
    stringBuilder.append("module==");
    stringBuilder.append(module);
    stringBuilder.append(" and ");
    stringBuilder.append("configName==");
    stringBuilder.append(configName);
    return stringBuilder.toString();
  }
}
