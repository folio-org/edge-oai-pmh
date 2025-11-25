package org.folio.edge.oaipmh.clients;

import static org.folio.edge.core.Constants.FOLIO_CLIENT_TLS_ENABLED;
import static org.folio.edge.core.Constants.SYS_OKAPI_URL;
import static org.folio.edge.core.Constants.SYS_REQUEST_TIMEOUT_MS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.folio.edge.core.utils.OkapiClientFactory;
import org.folio.edge.core.utils.OkapiClientFactoryInitializer;
import org.folio.edge.core.utils.test.TestUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;

@Slf4j
@ExtendWith(VertxExtension.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ConfigurationSettingsClientTest {

  private static final String TENANT = "diku";
  private static final int REQUEST_TIMEOUT = 3000;
  private static final String CONFIG_SETTINGS_PATH = "/oai-pmh/configuration-settings";
  
  private OkapiClientFactory factory;
  private int okapiPort;
  private HttpServer mockServer;

  @BeforeEach
  void setUp(Vertx vertx, VertxTestContext context) {
    List<String> knownTenants = new ArrayList<>();
    knownTenants.add(TENANT);

    okapiPort = TestUtils.getPort();
    factory = OkapiClientFactoryInitializer.createInstance(vertx, getCommonConfig(okapiPort));

    // Start mock Okapi server
    mockServer = vertx.createHttpServer(new HttpServerOptions());
    Router router = Router.router(vertx);
    
    // Mock login endpoint
    router.route(HttpMethod.POST, "/authn/login").handler(ctx -> {
      ctx.response()
        .setStatusCode(201)
        .putHeader("x-okapi-token", "test-token")
        .putHeader("Content-Type", "application/json")
        .end(new JsonObject().put("token", "test-token").encode());
    });
    
    // Mock configuration-settings collection endpoint
    router.route(HttpMethod.GET, CONFIG_SETTINGS_PATH).handler(this::handleGetConfigurationSettings);
    
    // Mock configuration-settings by ID endpoint
    router.route(HttpMethod.GET, CONFIG_SETTINGS_PATH + "/:id").handler(this::handleGetConfigurationSettingById);

    mockServer.requestHandler(router)
      .listen(okapiPort, context.succeeding(result -> {
        log.info("Mock Okapi server started on port {}", okapiPort);
        context.completeNow();
      }));
  }

  @AfterEach
  void tearDown(Vertx vertx, VertxTestContext context) {
    log.info("Shutting down mock server");
    if (mockServer != null) {
      mockServer.close(res -> {
        if (res.succeeded()) {
          log.info("Successfully shut down mock server");
          context.completeNow();
        } else {
          log.error("Failed to shut down mock server", res.cause());
          context.failNow(res.cause());
        }
      });
    } else {
      context.completeNow();
    }
  }

  @Test
  void testGetConfigurationSettings(VertxTestContext context) {
    var client = new ConfigurationSettingsClient(factory.getOkapiClient(TENANT));
    
    client.login("admin", "password")
      .thenCompose(v -> client.getConfigurationSettings(MultiMap.caseInsensitiveMultiMap()).toCompletionStage())
      .thenAccept(collection -> {
        assertNotNull(collection, "Collection should not be null");
        assertEquals(2, collection.getTotalRecords(), "Total records should be 2");
        assertNotNull(collection.getConfigurationSettings(), "Configuration settings list should not be null");
        assertEquals(2, collection.getConfigurationSettings().size(), "Should have 2 configuration settings");
        
        var firstSetting = collection.getConfigurationSettings().get(0);
        assertEquals("suppress-discovery", firstSetting.getConfigName(), "First config name should match");
        assertEquals("123", firstSetting.getId(), "First config ID should match");
        
        context.completeNow();
      })
      .exceptionally(throwable -> {
        context.failNow(throwable);
        return null;
      });
  }

  @Test
  void testGetConfigurationSettingsByName(VertxTestContext context) {
    var client = new ConfigurationSettingsClient(factory.getOkapiClient(TENANT));
    String configName = "suppress-discovery";
    
    client.login("admin", "password")
      .thenCompose(v -> client.getConfigurationSettingsByName(configName, MultiMap.caseInsensitiveMultiMap()).toCompletionStage())
      .thenAccept(collection -> {
        assertNotNull(collection, "Collection should not be null");
        assertEquals(1, collection.getTotalRecords(), "Should have 1 filtered result");
        assertNotNull(collection.getConfigurationSettings(), "Configuration settings list should not be null");
        assertTrue(collection.getConfigurationSettings().size() >= 1, "Should have at least 1 configuration setting");
        
        var setting = collection.getConfigurationSettings().get(0);
        assertEquals(configName, setting.getConfigName(), "Config name should match filter");
        
        context.completeNow();
      })
      .exceptionally(throwable -> {
        context.failNow(throwable);
        return null;
      });
  }

  @Test
  void testGetConfigurationSettingById(VertxTestContext context) {
    var client = new ConfigurationSettingsClient(factory.getOkapiClient(TENANT));
    String id = "123";
    
    client.login("admin", "password")
      .thenCompose(v -> client.getConfigurationSettingById(id, MultiMap.caseInsensitiveMultiMap()).toCompletionStage())
      .thenAccept(setting -> {
        assertNotNull(setting, "Configuration setting should not be null");
        assertEquals(id, setting.getId(), "ID should match");
        assertEquals("suppress-discovery", setting.getConfigName(), "Config name should match");
        assertNotNull(setting.getConfigValue(), "Config value should not be null");
        
        context.completeNow();
      })
      .exceptionally(throwable -> {
        context.failNow(throwable);
        return null;
      });
  }

  private void handleGetConfigurationSettings(RoutingContext ctx) {
    String nameParam = ctx.request().getParam("name");
    
    JsonArray configSettings = new JsonArray();
    
    if (nameParam != null && nameParam.equals("suppress-discovery")) {
      // Return filtered result
      JsonObject setting1 = new JsonObject()
        .put("id", "123")
        .put("configName", "suppress-discovery")
        .put("configValue", new JsonObject()
          .put("rules", new JsonArray().add("rule1").add("rule2"))
          .put("enabled", true));
      configSettings.add(setting1);
    } else {
      // Return all results
      JsonObject setting1 = new JsonObject()
        .put("id", "123")
        .put("configName", "suppress-discovery")
        .put("configValue", new JsonObject()
          .put("rules", new JsonArray().add("rule1").add("rule2"))
          .put("enabled", true));
      
      JsonObject setting2 = new JsonObject()
        .put("id", "456")
        .put("configName", "metadata-prefix-config")
        .put("configValue", new JsonObject()
          .put("prefixes", new JsonArray().add("oai_dc").add("marc21"))
          .put("default", "oai_dc"));
      
      configSettings.add(setting1).add(setting2);
    }
    
    JsonObject response = new JsonObject()
      .put("configurationSettings", configSettings)
      .put("totalRecords", configSettings.size());
    
    ctx.response()
      .setStatusCode(200)
      .putHeader("Content-Type", "application/json")
      .end(response.encode());
  }

  private void handleGetConfigurationSettingById(RoutingContext ctx) {
    String id = ctx.pathParam("id");
    
    JsonObject setting = new JsonObject()
      .put("id", id)
      .put("configName", "suppress-discovery")
      .put("configValue", new JsonObject()
        .put("rules", new JsonArray().add("rule1").add("rule2"))
        .put("enabled", true));
    
    ctx.response()
      .setStatusCode(200)
      .putHeader("Content-Type", "application/json")
      .end(setting.encode());
  }

  private JsonObject getCommonConfig(int okapiPort) {
    return new JsonObject()
      .put(SYS_OKAPI_URL, "http://localhost:" + okapiPort)
      .put(SYS_REQUEST_TIMEOUT_MS, REQUEST_TIMEOUT)
      .put(FOLIO_CLIENT_TLS_ENABLED, false);
  }
}
