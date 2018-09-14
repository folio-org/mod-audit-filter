package org.folio.audit;

import org.folio.audit.service.AuditFilterService;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;

public class AuditFilterVerticle extends AbstractVerticle {

  private final Logger logger = LoggerFactory.getLogger(this.getClass());

  private int port;
  private AuditFilterService auditFilterService;

  @Override
  public void start(Future<Void> fut) throws Exception {

    port = Integer.parseInt(
      System.getProperty("port", System.getProperty("http.port", "" + context.config().getInteger("port", 8081))));

    auditFilterService = new AuditFilterService(vertx);

    Router router = Router.router(vertx);
    // root
    router.route("/").handler(rc -> rc.response().end("mod-audit-filter is up running"));
    // health checking
    router.route("/admin/health").handler(rc -> rc.response().end("OK"));
    // body mapping
    router.route("/*").handler(BodyHandler.create());
    // filter mapping
    router.route("/*").handler(auditFilterService::prePostHandler);

    vertx.createHttpServer().requestHandler(router::accept).listen(port, rs -> {
      if (rs.succeeded()) {
        fut.complete();
      } else {
        fut.fail(rs.cause());
      }
    });

    logger.info("HTTP server started on port " + port);
  }

}
