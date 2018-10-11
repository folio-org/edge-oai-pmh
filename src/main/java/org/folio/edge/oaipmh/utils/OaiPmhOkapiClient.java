package org.folio.edge.oaipmh.utils;

import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import java.util.concurrent.CompletableFuture;
import me.escoffier.vertx.completablefuture.VertxCompletableFuture;
import org.folio.edge.core.utils.OkapiClient;

public class OaiPmhOkapiClient extends OkapiClient {

  public OaiPmhOkapiClient(OkapiClient client) {
    super(client);
  }

  protected OaiPmhOkapiClient(Vertx vertx, String okapiURL, String tenant, long timeout) {
    super(vertx, okapiURL, tenant, timeout);
  }

  public CompletableFuture<String> call(MultiMap parameters, MultiMap headers) {
    VertxCompletableFuture<String> future = new VertxCompletableFuture<>(vertx);
    //TODO
    return future;
  }
}
