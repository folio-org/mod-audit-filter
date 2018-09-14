package org.folio.audit.util;

import static org.junit.Assert.*;

import java.util.UUID;

import org.folio.audit.util.AuditUtil;
import org.junit.Test;

import io.vertx.core.MultiMap;
import io.vertx.core.json.JsonObject;

public class AuditUtilTest {

  @Test
  public void testDecodeOkapiToken() {
    String token = "eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJkaWt1X3VzZXIiLCJ1c2VyX2lkIjoiYWJjIiwidGVuYW50IjoiZGlrdSJ9.eMu6_Gjjo6G6TeTS3y--GmQGTtWryJtKznpGUUwpa0rDDwY1xLBDTQoHv06_mXYs2GyPOoeERUM_G_BEvpMZcA";
    JsonObject jo = AuditUtil.decodeOkapiToken(token);
    assertEquals("diku_user", jo.getValue("sub"));
    assertEquals("abc", jo.getValue("user_id"));
    assertEquals("diku", jo.getValue("tenant"));
  }

  @Test(expected = IllegalArgumentException.class)
  public void testDecodeOkapiTokenInvalidTokenFomrat() {
    String token = "abc";
    AuditUtil.decodeOkapiToken(token);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testDecodeOkapiTokenInvalidJson() {
    String token = "abc.def";
    AuditUtil.decodeOkapiToken(token);
  }

  @Test
  public void testConvertMultiMapToJsonObject() {
    MultiMap map = MultiMap.caseInsensitiveMultiMap();
    map.add("a", "1 a");
    map.add("a", "2 a");
    map.add("a", "3 a");
    map.add("b", "1 b");
    JsonObject jo = AuditUtil.convertMultiMapToJsonObject(map);
    assertTrue(jo.getJsonArray("a").contains("1 a"));
    assertTrue(jo.getJsonArray("a").contains("2 a"));
    assertTrue(jo.getJsonArray("a").contains("3 a"));
    assertEquals("1 b", jo.getString("b"));
  }

  @Test
  public void testIsUUID() {
    assertTrue(AuditUtil.isUUID(UUID.randomUUID().toString()));
  }

  @Test
  public void testIsNotUUID() {
    assertFalse(AuditUtil.isUUID("diku_admin"));
  }
}
