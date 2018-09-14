package org.folio.audit.util;

import java.util.Base64;
import java.util.UUID;

import io.vertx.core.MultiMap;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class AuditUtil {

  /**
   * Decode Okapi token to {@link JsonObject}
   * 
   * @param okapiToken
   * @return
   */
  public static JsonObject decodeOkapiToken(String okapiToken) {
    String encodedJson;
    try {
      encodedJson = okapiToken.split("\\.")[1];
    } catch (ArrayIndexOutOfBoundsException e) {
      throw new IllegalArgumentException(e.getMessage());
    }
    String decodedJson = new String(Base64.getDecoder().decode(encodedJson));
    try {
      return new JsonObject(decodedJson);
    } catch (DecodeException e) {
      throw new IllegalArgumentException(e.getMessage());
    }
  }

  /**
   * Convert {@link MultiMap} to {@link JsonObject}. Collapse values with same key
   * to an array.
   * 
   * @param multiMap
   * @return
   */
  public static JsonObject convertMultiMapToJsonObject(MultiMap multiMap) {
    JsonObject jo = new JsonObject();
    multiMap.forEach(entry -> {
      String key = entry.getKey();
      String value = entry.getValue();
      if (jo.containsKey(key)) {
        Object obj = jo.getValue(key);
        if (obj instanceof JsonArray) {
          ((JsonArray) obj).add(value);
        } else {
          jo.put(key, new JsonArray().add(obj).add(value));
        }
      } else {
        jo.put(key, value);
      }
    });
    return jo;
  }

  /**
   * Check if given string is a {@link UUID}.
   * 
   * @param id
   * @return
   */
  public static boolean isUUID(String id) {
    try {
      UUID.fromString(id);
    } catch (Exception e) {
      return false;
    }
    return true;
  }

}
