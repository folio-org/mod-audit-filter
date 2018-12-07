package org.folio.audit.service;

import static org.folio.audit.util.Constant.*;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;

import org.folio.audit.util.AuditUtil;
import org.folio.audit.util.Constant;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;

public class AuditFilterService {

  private static Logger logger = LoggerFactory.getLogger(AuditFilterService.class);

  private Vertx vertx;
  // prevent circular audit filter call
  private ConcurrentMap<String, Long> auditFilterIds = new ConcurrentHashMap<>();

  public AuditFilterService(Vertx vertx) {
    this.vertx = vertx;
  }

  /**
   * Implement Okapi PRE and POST filters.
   *
   * @param ctx
   */
  public void prePostHandler(RoutingContext ctx) {

    // async is only used at tests
    boolean async = ctx.request().headers().contains(AUDIT_FILTER_ASYNC);

    String phase = "" + ctx.request().headers().get(OKAPI_FILTER);
    if (phase.startsWith(PHASE_POST)) {
      JsonObject auditData = null;
      try {
        auditData = collectAuditData(ctx);
      } catch (Exception e) {
        logger.warn("Error to extract auditData: ", e);
        ctx.response().setStatusCode(500).end();
        return;
      }
      if (auditData != null) {
        try {
          saveAuditData(ctx, auditData, handler -> {
            if (async) {
              if (handler.failed()) {
                logger.warn("Error to save auditData: ", handler.cause());
                ctx.response().setStatusCode(500);
              }
              ctx.response().end();
              return;
            }
          });
        } catch (Exception e) {
          logger.warn("Error to save auditData: ", e);
          ctx.response().setStatusCode(500).close();
          return;
        }
      }
    }

    if (!async) {
      ctx.response().end();
    }
  }

  private void saveAuditData(RoutingContext ctx, JsonObject auditData, Handler<AsyncResult<Void>> handler) {
    MultiMap headers = ctx.request().headers();
    String auditPostUrl = headers.get(OKAPI_URL) + AUDIT_URL;
    HttpClient client = vertx.createHttpClient();
    String auditFilterId = UUID.randomUUID().toString();
    auditFilterIds.put(auditFilterId, System.currentTimeMillis());
    HttpClientRequest request = client.postAbs(auditPostUrl, response -> {
      response.exceptionHandler(th -> {
        logger.error(th);
        auditFilterIds.remove(auditFilterId);
        handler.handle(Future.failedFuture(th));
      });
      if (response.statusCode() == 201) {
        logger.debug("Created audit record: " + response.headers().get("location"));
        handler.handle(Future.succeededFuture());
      } else {
        logger.error("Failed to create audit record. Status code: " + response.statusCode());
        response.bodyHandler(b -> logger.error(b.toString()));
        handler.handle(Future.failedFuture("Failed to create audit record"));
      }
      auditFilterIds.remove(auditFilterId);
    });
    request.exceptionHandler(th -> {
      logger.error(th);
      auditFilterIds.remove(auditFilterId);
      handler.handle(Future.failedFuture(th));
    });
    request.putHeader(AUDIT_FILTER_ID, auditFilterId);
    if (headers.contains(HTTP_HEADER_TENANT)) {
      request.putHeader(HTTP_HEADER_TENANT, headers.get(HTTP_HEADER_TENANT));
    }
    if (headers.contains(HTTP_HEADER_TOKEN)) {
      request.putHeader(HTTP_HEADER_TOKEN, headers.get(HTTP_HEADER_TOKEN));
    }
    request.putHeader("Content-Type", "application/json");
    request.putHeader("Accept", "application/json");
    if (headers.contains(AUDIT_FILTER_TEST_CASE)) {
      request.putHeader(AUDIT_FILTER_TEST_CASE, headers.get(AUDIT_FILTER_TEST_CASE));
    }
    request.setChunked(true);
    request.write(Json.encode(auditData));
    request.end();
  }

  private JsonObject collectAuditData(RoutingContext ctx) {

    if (skipping(ctx)) {
      return null;
    }

    JsonObject auditData = new JsonObject();

    populateMainFields(auditData, ctx);

    JsonObject bodyJson = getBodyAsJsonObject(ctx);

    populateMainTarget(auditData, bodyJson, ctx);

    populateExtraFields(auditData, bodyJson, ctx);

    populateAllDataForDebugging(auditData, ctx);

    return auditData;
  }

  // skipping
  private boolean skipping(RoutingContext ctx) {
    MultiMap headers = ctx.request().headers();
    String method = headers.get(HTTP_HEADER_REQUEST_METHOD);
    // skip self-calling
    if (headers.contains(AUDIT_FILTER_ID)) {
      auditFilterIds.remove(headers.get(AUDIT_FILTER_ID));
      return true;
    }
    // skip GET
    return ("GET".equals(method) || "200".equals(headers.get(HTTP_HEADER_MODULE_RES)));
  }

  // populate main fields
  private void populateMainFields(JsonObject auditData, RoutingContext ctx) {
    HttpServerRequest req = ctx.request();
    MultiMap headers = req.headers();

    auditData.put("timestamp", headers.get(HTTP_HEADER_REQUEST_TIMESTAMP));
    auditData.put("tenant", headers.get(HTTP_HEADER_TENANT));
    auditData.put("user", headers.get(HTTP_HEADER_USER));
    String okapiToken = headers.get(HTTP_HEADER_TOKEN);
    if (okapiToken != null && !okapiToken.isEmpty()) {
      auditData.put("login", AuditUtil.decodeOkapiToken(okapiToken).getString("sub"));
    }
    auditData.put("method", headers.get(HTTP_HEADER_REQUEST_METHOD));
    auditData.put("uri", req.absoluteURI());
    auditData.put("path", ctx.normalisedPath());
    if (!req.params().isEmpty()) {
      auditData.put("params", AuditUtil.convertMultiMapToJsonObject(req.params()));
    }
    if (!ctx.pathParams().isEmpty()) {
      auditData.put("path_params",
        AuditUtil.convertMultiMapToJsonObject(MultiMap.caseInsensitiveMultiMap().addAll(ctx.pathParams())));
    }
    auditData.put("request_id", headers.get(HTTP_HEADER_REQUEST_ID));
    addResult(ctx, HTTP_HEADER_AUTH_RES, auditData, "auth");
    addResult(ctx, HTTP_HEADER_MODULE_RES, auditData, "module");
  }

  // populate main target type and id
  private void populateMainTarget(JsonObject auditData, JsonObject bodyJson, RoutingContext ctx) {
    HttpServerRequest req = ctx.request();
    MultiMap headers = req.headers();
    String method = headers.get(HTTP_HEADER_REQUEST_METHOD);
    String path = ctx.normalisedPath();

    if ("201".equals(headers.get(HTTP_HEADER_MODULE_RES)) && headers.contains(HTTP_HEADER_LOCATION)) {
      addTarget(auditData, headers.get(HTTP_HEADER_LOCATION), path);
    } else {
      switch (method) {
      case "POST":
        if (auditData.containsKey("auth_error") || auditData.containsKey("module_error")) {
          break;
        }
        if (bodyJson != null) {
          auditData.put(AUDIT_TARGET_TYPE, path.replaceFirst("/", ""));
          findId(bodyJson, auditData);
          if (!auditData.containsKey(AUDIT_TARGET_ID)) {
            auditData.put(AUDIT_TARGET_ID, bodyJson.iterator().next().getValue());
          }
        }
        break;
      case "PUT":
      case "DELETE":
        addTarget(auditData, ctx.normalisedPath(), ctx.normalisedPath());
        break;
      default:
        break;
      }
    }
  }

  // populate extra fields
  private void populateExtraFields(JsonObject auditData, JsonObject bodyJson, RoutingContext ctx) {
    MultiMap headers = ctx.request().headers();
    if (bodyJson != null) {
      JsonObject moreTargets = new JsonObject();
      findExtraIds(bodyJson, moreTargets, "");
      if (!moreTargets.isEmpty()) {
        auditData.put("extra_targets", moreTargets);
      }
    }

    auditData.put("ip", headers.get(HTTP_HEADER_REQUEST_IP));
    for (String header : HTTP_HEADER_EXTRA) {
      if (headers.contains(header)) {
        auditData.put(header, headers.get(header));
      }
    }
  }

  // capture all headers and trimmed body for debugging purpose
  private void populateAllDataForDebugging(JsonObject auditData, RoutingContext ctx) {
    MultiMap headers = ctx.request().headers();
    if (headers.contains(AUDIT_FILTER_VERBOSE)) {
      JsonObject reqHeaders = new JsonObject();
      headers.forEach(entry -> {
        String key = entry.getKey();
        String value = entry.getValue();
        if (reqHeaders.containsKey(key)) {
          Object obj = reqHeaders.getValue(key);
          if (obj instanceof JsonArray) {
            ((JsonArray) obj).add(value);
          } else {
            reqHeaders.put(key, new JsonArray().add(obj).add(value));
          }
        } else {
          reqHeaders.put(key, value);
        }
      });
      auditData.put("raw_headers", reqHeaders);
      // capture body for now
      String bodyString = ctx.getBodyAsString();
      if (bodyString.length() > 2000) {
        bodyString = bodyString.substring(0, 1000) + "...";
      }
      try {
        auditData.put("raw_body", new JsonObject(bodyString));
      } catch (Exception e) {
        auditData.put("raw_body", bodyString);
      }
      logger.debug(Json.encodePrettily(auditData));
    }
  }

  private JsonObject getBodyAsJsonObject(RoutingContext ctx) {
    try {
      return new JsonObject(ctx.getBodyAsString());
    } catch (Exception e) {
      return null;
    }
  }

  private void findId(JsonObject bodyJson, JsonObject parent) {
    bodyJson.iterator().forEachRemaining(entry -> {
      if (entry.getKey().equalsIgnoreCase("id")) {
        parent.put(AUDIT_TARGET_ID, entry.getValue());
      } else if (entry.getValue() instanceof JsonObject) {
        findId((JsonObject) entry.getValue(), parent);
      }
    });
  }

  private void findExtraIds(JsonObject bodyJson, JsonObject parent, String path) {
    bodyJson.iterator().forEachRemaining(entry -> {
      String key = entry.getKey();
      key = path.isEmpty() ? key : path + "/" + key;
      Object val = entry.getValue();
      if (val instanceof JsonObject) {
        findExtraIds((JsonObject) val, parent, key);
      } else if (val instanceof JsonArray) {
        findExtraIds((JsonArray) val, parent, key);
      } else if (AuditUtil.isUUID(val.toString())) {
        if (parent.containsKey(key)) {
          if (parent.getValue(key) instanceof JsonArray) {
            JsonArray ja = parent.getJsonArray(key);
            int jaSize = ja.size();
            if (jaSize < Constant.ID_LIMIT) {
              ja.add(val);
            } else if (jaSize == Constant.ID_LIMIT) {
              ja.add("...");
            }
          } else {
            JsonArray ja = new JsonArray();
            ja.add(parent.getValue(key)).add(val);
            parent.put(key, ja);
          }
        } else {
          parent.put(key, val);
        }
      }
    });
  }

  private void findExtraIds(JsonArray bodyJson, JsonObject parent, String path) {
    bodyJson.forEach(elem -> {
      if (elem instanceof JsonObject) {
        findExtraIds((JsonObject) elem, parent, path);
      } else if (elem instanceof JsonArray) {
        findExtraIds((JsonArray) elem, parent, path + "/");
      }
    });
  }

  private void addTarget(JsonObject parent, String path, String fallbackPath) {
    String[] ss = path.split(Pattern.quote("/"));
    // only if the last piece is UUID
    String tid = ss[ss.length - 1];
    if (AuditUtil.isUUID(tid)) {
      String ttype = path.replace(tid, "").trim();
      // in case path only contains UUID
      if (ttype.replace("/", "").isEmpty()) {
        ttype = fallbackPath.replace(tid, "").trim();
      }
      // in case Location header is not populated consistently
      if (ttype.toLowerCase().startsWith("http")) {
        ttype = fallbackPath.replace(tid, "").trim();
      }
      if (ttype.startsWith("/")) {
        ttype = ttype.substring(1, ttype.length());
      }
      if (ttype.endsWith("/")) {
        ttype = ttype.substring(0, ttype.length() - 1);
      }
      parent.put(AUDIT_TARGET_TYPE, ttype);
      parent.put(AUDIT_TARGET_ID, tid);
    }
  }

  private void addResult(RoutingContext ctx, String resultType, JsonObject parent, String key) {
    if (ctx.request().headers().contains(resultType)) {
      int result = Integer.parseInt(ctx.request().headers().get(resultType));
      parent.put(key + "_result", result);
      if (result < 200 || result >= 300) {
        addChild(parent, key + "_error", ctx.getBodyAsString());
      }
    }
  }

  private void addChild(JsonObject parent, String key, String value) {
    if (value == null || value.trim().isEmpty()) {
      return;
    }
    try {
      JsonObject jo = new JsonObject(value);
      parent.put(key, new JsonObject().put("body", jo));
    } catch (Exception e) {
      parent.put(key, new JsonObject().put("body", value));
    }
  }

}
