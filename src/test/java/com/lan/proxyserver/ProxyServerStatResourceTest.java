package com.lan.proxyserver;

import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@QuarkusTest
class ProxyServerStatResourceTest {
  @TestHTTPEndpoint(ProxyServerStatResource.class)
  @TestHTTPResource
  URL url;

  @Test
  void testProxyServerStatEndpoint() throws IOException {
    try (InputStream in = url.openStream()) {
      String contents = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      Assertions.assertTrue(contents.contains("Total accepted connections"));
      Assertions.assertTrue(contents.contains("Current connections"));
    }
  }
}
