package com.lan.proxyserver;

import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import java.io.IOException;
import java.net.URISyntaxException;
import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestHTTPEndpoint(ProxyServerStatResource.class)
class ProxyServerStatResourceTest {
  // Don't know why following
  // https://quarkus.io/guides/getting-started-testing#testhttpresource
  // makes the endpoint ProxyServerStatResource.class return 400 status code which
  // consequentially fails the test, but successfully test by following
  // https://quarkus.io/guides/getting-started-testing#restassured

  @Test
  void testProxyServerStatEndpoint() throws IOException, URISyntaxException, InterruptedException {
    RestAssured.when()
        .get()
        .then()
        .statusCode(200)
        .body(CoreMatchers.containsString("Total accepted connections"));
  }
}
