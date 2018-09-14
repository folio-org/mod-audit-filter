package org.folio.audit.util;

import java.util.Arrays;
import java.util.List;

public class Constant {

  public static final String OKAPI_FILTER = "X-Okapi-Filter";
  public static final String PHASE = "PHASE";
  public static final String PHASE_PRE = "pre";
  public static final String PHASE_POST = "post";

  public static final String OKAPI_URL = "X-Okapi-Url";
  public static final String AUDIT_URL = "/audit-data";

  public static final String AUDIT_FILTER_ID = "x-okapi-audit-filter-id";
  public static final String AUDIT_FILTER_ASYNC = "x-okapi-audit-filter-async";
  public static final String AUDIT_FILTER_VERBOSE = "x-okapi-audit-filter-verbose";
  public static final String AUDIT_FILTER_TEST_CASE = "x-okapi-audit-filter-test-case";

  public static final String HTTP_HEADER_REQUEST_IP = "X-Okapi-request-ip";
  public static final String HTTP_HEADER_REQUEST_TIMESTAMP = "X-Okapi-request-timestamp";
  public static final String HTTP_HEADER_REQUEST_METHOD = "X-Okapi-request-method";
  public static final String HTTP_HEADER_TENANT = "x-okapi-tenant";
  public static final String HTTP_HEADER_USER = "x-okapi-user-id";
  public static final String HTTP_HEADER_TOKEN = "x-okapi-token";
  public static final String HTTP_HEADER_REQUEST_ID = "x-okapi-request-id";
  public static final String HTTP_HEADER_AUTH_RES = "x-okapi-auth-result";
  public static final String HTTP_HEADER_MODULE_RES = "x-okapi-handler-result";
  public static final String HTTP_HEADER_LOCATION = "location";
  public static final List<String> HTTP_HEADER_EXTRA = Arrays.asList("x-forwarded-for", "x-real-ip");

  public static final String AUDIT_TARGET_TYPE = "target_type";
  public static final String AUDIT_TARGET_ID = "target_id";

  public static final int ID_LIMIT = 50;

}
