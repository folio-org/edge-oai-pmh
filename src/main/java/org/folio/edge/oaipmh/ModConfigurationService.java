package org.folio.edge.oaipmh;

import static org.folio.edge.core.Constants.X_OKAPI_TENANT;
import static org.folio.edge.core.Constants.X_OKAPI_TOKEN;

import java.util.concurrent.CompletableFuture;

import org.apache.log4j.Logger;
import org.folio.rest.client.ConfigurationsClient;
import org.folio.rest.jaxrs.model.Configs;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import me.escoffier.vertx.completablefuture.VertxCompletableFuture;

public class ModConfigurationService {

  private static Logger logger = Logger.getLogger(ModConfigurationService.class);

  public static final String MODULE_NAME = "OAIPMH";
  public static final String BEHAVIOR_CONFIG = "behavior";
  public static final String ERRORS_PROCESSING = "errorsProcessing";
  public static final String GENERAL_CONFIG = "general";
  public static final String ENABLE_OAI_SERVICE = "enableOaiService";

  private String okapiURL;

  public ModConfigurationService(String okapiUrl) {
    this.okapiURL = okapiUrl;
  }

  /**
   * This method make request to mod-configuration module and receive config setting
   *
   * @param ctx        routing context
   * @param moduleName name of module
   * @param configName name of configuration (currently available general and behavior)
   * @param value      name of configuration setting in value section (currently available enableOaiService and errorsProcessing)
   * @return value of configuration setting
   */
  public CompletableFuture<String> getConfigSettingValue(RoutingContext ctx, String moduleName, String configName, String value) {
    final int configNumber = 0;
    CompletableFuture<String> future = new VertxCompletableFuture<>();
    String tenantId = ctx.request().headers().get(X_OKAPI_TENANT);
    String token = ctx.request().headers().get(X_OKAPI_TOKEN);
    try {
      new ConfigurationsClient(getOkapiURL(), tenantId, token).getConfigurationsEntries(buildQuery(moduleName, configName), 0, 100,
          null, null, responseConfig -> responseConfig.bodyHandler(body -> {
            try {
              if (responseConfig.statusCode() != 200) {
                logger.error("Error getting configuration " + responseConfig.statusMessage());
                future.complete(String.valueOf(responseConfig.statusCode()));
                return;
              }
              future.complete(new JsonObject(body.toJsonObject()
                .mapTo(Configs.class)
                .getConfigs()
                .get(configNumber)
                .getValue()).getString(value));
            } catch (Exception exc) {
              logger.error("Error when get configuration value from mod-configurations client response ", exc);
              future.completeExceptionally(exc);
            }
          }));
    } catch (Exception e) {
      logger.error("Error happened initializing mod-configurations client ", e);
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

  public String getOkapiURL() {
    return okapiURL;
  }
}
