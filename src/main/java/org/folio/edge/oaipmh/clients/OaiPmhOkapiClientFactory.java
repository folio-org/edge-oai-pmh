package org.folio.edge.oaipmh.clients;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.edge.core.Constants;
import org.folio.edge.core.utils.OkapiClientFactory;

import static org.folio.edge.core.Constants.*;

public class OaiPmhOkapiClientFactory extends OkapiClientFactory {
  private static final Logger logger = LogManager.getLogger();

  private OaiPmhOkapiClientFactory(Vertx vertx, String okapiURL, int reqTimeoutMs) {
    super(vertx, okapiURL, reqTimeoutMs);
  }

  public static OkapiClientFactory createInstance(Vertx vertx, JsonObject config) {
    String okapiUrl = config.getString(Constants.SYS_OKAPI_URL);
    Integer requestTimeout = config.getInteger(Constants.SYS_REQUEST_TIMEOUT_MS);
    boolean isSslEnabled = config.getBoolean(Constants.SYS_SSL_ENABLED);
    if (isSslEnabled) {
      logger.info("Creating OkapiClientFactory with Enhance HTTP Endpoint Security and TLS mode enabled");
      String keystoreType = config.getString(SYS_KEYSTORE_TYPE);
      String keystoreProvider = config.getString(SYS_KEYSTORE_PROVIDER);
      String keystorePath = config.getString(SYS_KEYSTORE_PATH);
      String keystorePassword = config.getString(SYS_KEYSTORE_PASSWORD);
      String keyAlias = config.getString(SYS_KEY_ALIAS);
      String keyAliasPassword = config.getString(SYS_KEY_ALIAS_PASSWORD);
      return new OkapiClientFactory(vertx, okapiUrl, requestTimeout, keystoreType, keystoreProvider, keystorePath, keystorePassword, keyAlias, keyAliasPassword);
    } else {
      return new OkapiClientFactory(vertx, okapiUrl, requestTimeout);
    }
  }

  public static ConsortiaTenantClient getConsortiaTenantClient(String tenant, Vertx vertx, JsonObject config) {
    String okapiUrl = config.getString(SYS_OKAPI_URL);
    Integer requestTimeout = config.getInteger(SYS_REQUEST_TIMEOUT_MS);
    return new ConsortiaTenantClient(vertx, okapiUrl, tenant, requestTimeout);
  }
}
