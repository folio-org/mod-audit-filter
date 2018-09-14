package org.folio.audit;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

import static io.restassured.RestAssured.*;
import static org.folio.audit.util.Constant.*;
import static org.hamcrest.Matchers.containsString;

import io.restassured.RestAssured;
import io.restassured.http.Header;
import io.restassured.specification.RequestSpecification;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;

import java.io.IOException;
import java.net.ServerSocket;

import org.junit.BeforeClass;
import org.junit.runner.RunWith;
import org.folio.audit.util.Constant;
import org.junit.AfterClass;

/**
 * Interface test for mod-audit.
 */
@RunWith(VertxUnitRunner.class)
public class AuditFilterTest {

  private static final Logger logger = LoggerFactory.getLogger(AuditFilterTest.class);

  private static int port;
  private static int mockPort;
  private static Header OKAPI_URL;
  private static Header APP_JSON = new Header("Content-Type", "application/json");
  private static Header OKAPI_FILTER_PRE = new Header(OKAPI_FILTER, PHASE_PRE);
  private static Header OKAPI_FILTER_POST = new Header(OKAPI_FILTER, PHASE_POST);
  private static Header OKAPI_TENANT = new Header(HTTP_HEADER_TENANT, "diku");

  private static String HEADER_TEST_CASE = "TEST_CASE";
  private static String HEADER_FAILED_AUDIT = "x-audit-module-failed";

  private static Vertx vertx;
  private static MockServer mockServer;

  private static AtomicInteger mockTestCount = new AtomicInteger(0);
  private static String testId = UUID.randomUUID().toString();

  @BeforeClass
  public static void setUpOnce(TestContext context) throws Exception {
    logger.debug("Set up test environment");
    port = nextFreePort();
    mockPort = nextFreePort();
    OKAPI_URL = new Header(Constant.OKAPI_URL, "http://localhost:" + mockPort);
    vertx = Vertx.vertx();
    mockServer = new MockServer(mockPort, vertx, context);
    mockServer.start();

    RestAssured.baseURI = "http://localhost:" + port;
    RestAssured.port = port;
    RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();

    JsonObject conf = new JsonObject();
    conf.put("port", port);
    DeploymentOptions opt = new DeploymentOptions().setConfig(conf);
    vertx.deployVerticle(AuditFilterVerticle.class.getName(), opt, context.asyncAssertSuccess());
  }

  @AfterClass
  public static void tearDownOnce(TestContext context) {
    vertx.close(context.asyncAssertSuccess(t -> logger.info("Vertx is closed")));
  }

  // Root path
  @Test
  public void testRoot(TestContext context) {
    Async async = context.async();
    given()
        .header(APP_JSON)
        .get("/")
        .then().log().ifValidationFails()
        .statusCode(200)
        .body(containsString("mod-audit-filter is up running"));
    async.complete();
  }

  // Standard health check
  @Test
  public void testHealth(TestContext context) {
    Async async = context.async();
    given()
        .header(APP_JSON)
        .get("/admin/health")
        .then().log().ifValidationFails()
        .statusCode(200)
        .body(containsString("OK"));
    async.complete();
  }

  // Skip PRE filter
  @Test
  public void testAuditDataPreFilter(TestContext context) throws Exception {
    Async async = context.async();
    given()
        .header(APP_JSON)
        .header(OKAPI_URL)
        .header(OKAPI_TENANT)
        .header(OKAPI_FILTER_PRE)
        .post(AUDIT_URL)
        .then().log().ifValidationFails()
        .statusCode(200);
    async.complete();
  }

  // Skip call from filter itself
  @Test
  public void testAuditDataPostFilterFromSelf(TestContext context) throws Exception {
    Async async = context.async();
    given()
        .header(APP_JSON)
        .header(OKAPI_URL)
        .header(OKAPI_TENANT)
        .header(OKAPI_FILTER_POST)
        .header(new Header(AUDIT_FILTER_ID, "abc"))
        .post(AUDIT_URL)
        .then().log().ifValidationFails()
        .statusCode(200);
    async.complete();
  }

  // Skip GET
  @Test
  public void testAuditDataPostFilterGet(TestContext context) throws Exception {
    Async async = context.async();
    given()
        .header(APP_JSON)
        .header(OKAPI_URL)
        .header(OKAPI_TENANT)
        .header(OKAPI_FILTER_POST)
        .header(new Header(HTTP_HEADER_REQUEST_METHOD, "GET"))
        .post(AUDIT_URL)
        .then().log().ifValidationFails()
        .statusCode(200);
    async.complete();
  }

  // Skip 200
  @Test
  public void testAuditDataPostFilter200(TestContext context) throws Exception {
    Async async = context.async();
    given()
        .header(APP_JSON)
        .header(OKAPI_URL)
        .header(OKAPI_TENANT)
        .header(OKAPI_FILTER_POST)
        .header(new Header(HTTP_HEADER_MODULE_RES, "200"))
        .post(AUDIT_URL)
        .then().log().ifValidationFails()
        .statusCode(200);
    async.complete();
  }

  // 201 and Location
  @Test
  public void testAuditDataPostFilter1(TestContext context) throws Exception {
    Async async = context.async();
    createBasicRequestSpecification()
        .header(new Header(HEADER_TEST_CASE, "1"))
        .header(new Header(HTTP_HEADER_REQUEST_METHOD, "POST"))
        .header(new Header(HTTP_HEADER_MODULE_RES, "201"))
        .header(new Header(HTTP_HEADER_LOCATION, "/test/" + testId))
        .post(AUDIT_URL + "/" + testId + "?p1=v1&p1=v2&p2=v3")
        .then().log().ifValidationFails()
        .statusCode(200);
    async.complete();

  }

  // Location has just id
  @Test
  public void testAuditDataPostFilter1_1(TestContext context) throws Exception {
    Async async = context.async();
    createBasicRequestSpecification()
        .header(new Header(HEADER_TEST_CASE, "1_1"))
        .header(new Header(HTTP_HEADER_REQUEST_METHOD, "POST"))
        .header(new Header(HTTP_HEADER_MODULE_RES, "201"))
        .header(new Header(HTTP_HEADER_LOCATION, "/" + testId))
        .post(AUDIT_URL + "/" + testId + "?p1=v1&p1=v2&p2=v3")
        .then().log().ifValidationFails()
        .statusCode(200);
    async.complete();
  }

  // Location starts with http
  @Test
  public void testAuditDataPostFilter1_2(TestContext context) throws Exception {
    Async async = context.async();
    createBasicRequestSpecification()
        .header(new Header(HEADER_TEST_CASE, "1_2"))
        .header(new Header(HTTP_HEADER_REQUEST_METHOD, "POST"))
        .header(new Header(HTTP_HEADER_MODULE_RES, "201"))
        .header(new Header(HTTP_HEADER_LOCATION, "http://localhost/test/" + testId))
        .post(AUDIT_URL + "/" + testId + "?p1=v1&p1=v2&p2=v3")
        .then().log().ifValidationFails()
        .statusCode(200);
    async.complete();
  }

  // POST with auth error
  @Test
  public void testAuditDataPostFilter2(TestContext context) throws Exception {
    Async async = context.async();
    createBasicRequestSpecification()
        .header(new Header(HEADER_TEST_CASE, "2"))
        .header(new Header(HTTP_HEADER_REQUEST_METHOD, "POST"))
        .header(new Header(HTTP_HEADER_AUTH_RES, "400"))
        .body("some auth error")
        .post(AUDIT_URL + "?p1=v1&p1=v2&p2=v3")
        .then().log().ifValidationFails()
        .statusCode(200);
    async.complete();
  }

  // POST with module error
  @Test
  public void testAuditDataPostFilter3(TestContext context) throws Exception {
    Async async = context.async();
    createBasicRequestSpecification()
        .header(new Header(HTTP_HEADER_REQUEST_METHOD, "POST"))
        .header(new Header(HTTP_HEADER_MODULE_RES, "400"))
        .body("some module error")
        .post(AUDIT_URL + "?p1=v1&p1=v2&p2=v3")
        .then().log().ifValidationFails()
        .statusCode(200);
    async.complete();
  }

  // POST with module error and empty error body
  @Test
  public void testAuditDataPostFilter3_1(TestContext context) throws Exception {
    Async async = context.async();
    createBasicRequestSpecification()
        .header(new Header(HTTP_HEADER_REQUEST_METHOD, "POST"))
        .header(new Header(HTTP_HEADER_MODULE_RES, "400"))
        .body("")
        .post(AUDIT_URL + "?p1=v1&p1=v2&p2=v3")
        .then().log().ifValidationFails()
        .statusCode(200);
    async.complete();
  }

  // POST with module error and JSON error body
  @Test
  public void testAuditDataPostFilter3_2(TestContext context) throws Exception {
    Async async = context.async();
    createBasicRequestSpecification()
        .header(new Header(HTTP_HEADER_REQUEST_METHOD, "POST"))
        .header(new Header(HTTP_HEADER_MODULE_RES, "400"))
        .body(new JsonObject().put("error", "some mod error"))
        .post(AUDIT_URL + "?p1=v1&p1=v2&p2=v3")
        .then().log().ifValidationFails()
        .statusCode(200);
    async.complete();
  }

  // POST with id in the body
  @Test
  public void testAuditDataPostFilter4(TestContext context) throws Exception {
    Async async = context.async();
    JsonObject jo = new JsonObject().put("id", testId);
    createBasicRequestSpecification()
        .header(new Header(HTTP_HEADER_REQUEST_METHOD, "POST"))
        .body(jo.encode())
        .post(AUDIT_URL + "?p1=v1&p1=v2&p2=v3")
        .then().log().ifValidationFails()
        .statusCode(200);
    async.complete();
  }

  // POST without id in the body
  @Test
  public void testAuditDataPostFilter5(TestContext context) throws Exception {
    Async async = context.async();
    JsonObject jo = new JsonObject().put("xid", "abc");
    createBasicRequestSpecification()
        .header(new Header(HTTP_HEADER_REQUEST_METHOD, "POST"))
        .body(jo.encode())
        .post(AUDIT_URL + "?p1=v1&p1=v2&p2=v3")
        .then().log().ifValidationFails()
        .statusCode(200);
    async.complete();
  }

  // PUT
  @Test
  public void testAuditDataPostFilter6(TestContext context) throws Exception {
    Async async = context.async();
    JsonObject jo = new JsonObject().put("id", testId);
    createBasicRequestSpecification()
        .header(new Header(HTTP_HEADER_REQUEST_METHOD, "PUT"))
        .body(jo.encode())
        .post(AUDIT_URL + "/" + testId + "?p1=v1&p1=v2&p2=v3")
        .then().log().ifValidationFails()
        .statusCode(200);
    async.complete();
  }

  // DELETE
  @Test
  public void testAuditDataPostFilter7(TestContext context) throws Exception {
    Async async = context.async();
    createBasicRequestSpecification()
        .header(new Header(HTTP_HEADER_REQUEST_METHOD, "DELETE"))
        .post(AUDIT_URL + "/" + testId + "?p1=v1&p1=v2&p2=v3")
        .then().log().ifValidationFails()
        .statusCode(200);
    async.complete();
  }

  // Mock error
  @Test
  public void testAuditDataPostFilter8(TestContext context) throws Exception {
    Async async = context.async();
    createBasicRequestSpecification()
        .header(new Header(HTTP_HEADER_REQUEST_METHOD, "500"))
        .header(new Header(HEADER_FAILED_AUDIT, "true"))
        .post(AUDIT_URL + "?p1=v1&p1=v2&p2=v3")
        .then().log().ifValidationFails()
        .statusCode(500);
    async.complete();
  }

  // Test with duplicated raw headers
  @Test
  public void testAuditDataPostFilter9(TestContext context) throws Exception {
    Async async = context.async();
    createBasicRequestSpecification()
        .header(new Header(HTTP_HEADER_REQUEST_METHOD, "POST"))
        .header(new Header("test1", "1"))
        .header(new Header("test1", "2"))
        .header(new Header("test1", "3"))
        .post(AUDIT_URL + "?p1=v1&p1=v2&p2=v3")
        .then().log().ifValidationFails()
        .statusCode(200);
    async.complete();
  }

  // Test with large raw body
  @Test
  public void testAuditDataPostFilter10(TestContext context) throws Exception {
    Async async = context.async();
    createBasicRequestSpecification()
        .header(new Header(HTTP_HEADER_REQUEST_METHOD, "POST"))
        .body(new String(new char[2000]))
        .post(AUDIT_URL + "?p1=v1&p1=v2&p2=v3")
        .then().log().ifValidationFails()
        .statusCode(200);
    async.complete();
  }

  private RequestSpecification createBasicRequestSpecification() {
    logger.debug("Preparing call: " + mockTestCount.incrementAndGet());
    RequestSpecification rs = given()
        .header(APP_JSON)
        .header(OKAPI_URL)
        .header(OKAPI_TENANT)
        .header(new Header(AUDIT_FILTER_ASYNC, "true"))
        .header(new Header(AUDIT_FILTER_VERBOSE, "true"))
        .header(OKAPI_FILTER_POST)
        .header(new Header(HTTP_HEADER_REQUEST_TIMESTAMP, "" + System.currentTimeMillis()))
        .header(new Header(HTTP_HEADER_USER, "abc"))
        .header(new Header(HTTP_HEADER_TOKEN,
            "eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJkaWt1X3VzZXIiLCJ1c2VyX2lkIjoiYWJjIiwidGVuYW50IjoiZGlrdSJ9.eMu6_Gjjo6G6TeTS3y--GmQGTtWryJtKznpGUUwpa0rDDwY1xLBDTQoHv06_mXYs2GyPOoeERUM_G_BEvpMZcA"))
        .header(new Header(HTTP_HEADER_REQUEST_ID, "123"))
        .header(new Header(HTTP_HEADER_REQUEST_IP, "10.0.0.1"));

    for (String header : HTTP_HEADER_EXTRA) {
      rs.header(new Header(header, header + "-val"));
    }

    JsonObject body = new JsonObject();
    body.put("extra_id_1", UUID.randomUUID().toString());
    body.put("extra_id_2", UUID.randomUUID().toString());
    body.put("jo_ids", getExtraIdAsJsonObject().put("more", getExtraIdAsArray()));
    body.put("arr_ids", getExtraIdAsArray().add(
        new JsonObject().put("more", getExtraIdAsArray())));
    rs.body(body.encode());

    return rs;
  }

  private JsonObject getExtraIdAsJsonObject() {
    return new JsonObject()
        .put("jo_ids_1", UUID.randomUUID().toString())
        .put("jo_ids_2", UUID.randomUUID().toString());
  }

  private JsonArray getExtraIdAsArray() {
    JsonArray ja = new JsonArray();
    for (int i = 0; i <= ID_LIMIT; i++) {
      ja.add(new JsonObject().put("jo_ids_1", UUID.randomUUID().toString()));
      ja.add(new JsonArray().add(getExtraIdAsJsonObject()));
    }
    return ja;
  }

  private static class MockServer {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final int port;
    private final Vertx vertx;
    private final TestContext context;

    private AtomicInteger mockCallCount = new AtomicInteger(0);

    public MockServer(int port, Vertx vertx, TestContext context) {
      this.port = port;
      this.vertx = vertx;
      this.context = context;
    }

    protected Router defineRoutes() {
      Router router = Router.router(vertx);
      router.route().handler(BodyHandler.create());
      router.route(HttpMethod.POST, AUDIT_URL).handler(this::handleAuditData);
      return router;
    }

    // public void start(TestContext context) {
    public void start() {
      HttpServer server = vertx.createHttpServer();
      final Async async = context.async();
      server.requestHandler(defineRoutes()::accept).listen(port, result -> {
        if (result.failed()) {
          logger.warn(result.cause());
        }
        context.assertTrue(result.succeeded());
        async.complete();
      });
    }

    private void verifyBasicAuidtData(JsonObject jo) {
      context.assertTrue(jo.containsKey("timestamp"));
      context.assertEquals("diku", jo.getString("tenant"));
      context.assertEquals("abc", jo.getString("user"));
      context.assertEquals("diku_user", jo.getString("login"));
      context.assertTrue(jo.containsKey("uri"));
      context.assertTrue(jo.containsKey("path"));

      context.assertTrue(jo.containsKey("params"));
      JsonObject params = jo.getJsonObject("params");
      context.assertEquals(2, params.size());
      context.assertTrue(params.containsKey("p1"));
      context.assertEquals(2, params.getJsonArray("p1").size());
      context.assertTrue(params.getJsonArray("p1").contains("v1"));
      context.assertTrue(params.getJsonArray("p1").contains("v2"));
      context.assertTrue(params.containsKey("p2"));
      context.assertEquals("v3", params.getString("p2"));

      context.assertEquals("123", jo.getString("request_id"));
      context.assertEquals("10.0.0.1", jo.getString("ip"));
    }

    // simulate calls to mod-audit module
    private void handleAuditData(RoutingContext ctx) {

      int callCnt = mockCallCount.incrementAndGet();
      logger.debug("Receiving call: " + callCnt);

      MultiMap headers = ctx.request().headers();
      JsonObject jo = ctx.getBodyAsJson();

      // simulate error response from mod-audit
      if ("500".equals(jo.getString("method"))) {
        logger.info("send 500");
        ctx.response()
            .setStatusCode(500)
            .setChunked(true)
            .end("Simulate failed audit module response");
        return;
      }

      try {
        if (callCnt > AuditFilterTest.mockTestCount.get()) {
          throw new Exception("Exceeding expected requests");
        }
        context.assertTrue(headers.contains(AUDIT_FILTER_ID));
        context.assertTrue(headers.contains(HTTP_HEADER_TENANT));
        context.assertTrue(headers.contains(HTTP_HEADER_TOKEN));
        verifyBasicAuidtData(jo);
        // fine test as needed
        String testCase = "" + headers.get(HEADER_TEST_CASE);
        switch (testCase) {
        case "1":
        case "1_1":
        case "1_2":
          context.assertEquals(testId, jo.getString("target_id"));
          break;
        case "2":
          context.assertEquals("400", jo.getString("auth_result"));
          context.assertEquals("some auth error", jo.getString("auth_error"));
          break;
        case "3":
          context.assertEquals("400", jo.getString("module_result"));
          context.assertEquals("some module error", jo.getString("module_error"));
          break;
        case "3_1":
          context.assertEquals("400", jo.getString("module_result"));
          context.assertFalse(jo.containsKey("module_error"));
          break;
        case "3_2":
          context.assertEquals("400", jo.getString("module_result"));
          context.assertEquals("some module error",
              jo.getJsonObject("module_error").getString("error"));
          break;
        case "4":
        case "6":
        case "7":
          context.assertEquals(testId, jo.getString("target_id"));
          break;
        case "5":
          context.assertEquals("xid", jo.getString("target_type"));
          context.assertEquals("abc", jo.getString("target_id"));
          break;
        case "8":
          context.assertEquals("500", jo.getString("method"));
          break;
        case "9":
          context.assertFalse(jo.containsKey("test1"));
          break;
        default:
          break;
        }
      } catch (Exception e) {
        ctx.response().setStatusCode(500).end(e.getMessage());
        return;
      }
      String id = UUID.randomUUID().toString();
      ctx.response()
          .setStatusCode(201)
          .putHeader("Location", "/audit-data/" + id)
          .setChunked(true)
          .end(ctx.getBodyAsJson().put("id", id).encodePrettily());
    }
  }

  public static int nextFreePort() {
    int maxTries = 10000;
    int port = ThreadLocalRandom.current().nextInt(49152, 65535);
    while (true) {
      if (isLocalPortFree(port)) {
        return port;
      } else {
        port = ThreadLocalRandom.current().nextInt(49152, 65535);
      }
      maxTries--;
      if (maxTries == 0) {
        return 8081;
      }
    }
  }

  private static boolean isLocalPortFree(int port) {
    try {
      new ServerSocket(port).close();
      return true;
    } catch (IOException e) {
      return false;
    }
  }
}
