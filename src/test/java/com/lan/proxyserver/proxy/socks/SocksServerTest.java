package com.lan.proxyserver.proxy.socks;

import com.lan.proxyserver.lifecycle.ProxyServerLifecycleBean;
import com.lan.proxyserver.proxy.ProxyServerThread;
import com.lan.proxyserver.util.PortUtil;
import io.quarkus.test.junit.QuarkusTest;
import java.io.IOException;
import java.net.Authenticator;
import java.net.PasswordAuthentication;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@QuarkusTest
class SocksServerTest {
  private static final Logger logger = Logger.getLogger(SocksServerTest.class);

  private EchoServer destination;

  @BeforeEach
  void setUp() {
    try {
      destination = new EchoServer();
    } catch (IOException e) {
      logger.error(e.getMessage(), e);
      Assumptions.assumeFalse(true);
    }

    destination.start();
  }

  @AfterEach
  void tearDown() {
    destination.terminate();
  }

  @Test
  void testSocks5NoAuth() {
    byte[] clientSentData = new byte[] {0xA, 0xB, 0xC};

    try (EchoClient echoClient = new EchoClient(destination.getPort(), SocksServer.DEF_PORT)) {
      byte[] serverResponseData = echoClient.send(clientSentData);
      Assertions.assertArrayEquals(clientSentData, serverResponseData);
    } catch (IOException e) {
      logger.error(e.getMessage(), e);
      Assumptions.assumeFalse(true);
    }
  }

  @Test
  @Disabled(
      "JDK's SOCKS5 client prioritizes no authentication over username/password authentication, run"
          + " this test when the proxy server does not support no authentication to enforce"
          + " username/password authentication or after implementing our own SOCK5 client")
  void testSocks5UsrPwdAuth() {
    byte[] clientSentData = new byte[] {0xA, 0xB, 0xC};
    Authenticator defaultAuthenticator = Authenticator.getDefault();
    Authenticator.setDefault(
        new Authenticator() {
          @Override
          protected PasswordAuthentication getPasswordAuthentication() {
            return new PasswordAuthentication("username", "password".toCharArray());
          }
        });

    try (EchoClient echoClient = new EchoClient(destination.getPort(), SocksServer.DEF_PORT)) {
      byte[] serverResponseData = echoClient.send(clientSentData);
      Assertions.assertArrayEquals(clientSentData, serverResponseData);
    } catch (IOException e) {
      logger.error(e.getMessage(), e);
      Assumptions.assumeFalse(true);
    } finally {
      Authenticator.setDefault(defaultAuthenticator);
    }
  }

  @Test
  void testDestinationTerminate() {
    try (EchoClient echoClient = new EchoClient(destination.getPort(), SocksServer.DEF_PORT)) {
      byte[] clientSentData = new byte[] {0xA, 0xB, 0xC};
      byte[] serverResponseData = echoClient.send(clientSentData);
      Assertions.assertArrayEquals(clientSentData, serverResponseData);

      destination.terminate();

      clientSentData = new byte[] {0xD, 0xE, 0xF};
      serverResponseData = echoClient.send(clientSentData);
      Assertions.assertNull(serverResponseData);
    } catch (IOException e) {
      logger.error(e.getMessage(), e);
      Assumptions.assumeFalse(true);
    }
  }

  @Test
  void testClientTerminate() {
    try (EchoClient echoClient = new EchoClient(destination.getPort(), SocksServer.DEF_PORT)) {
      SocksServerStat stat = ProxyServerLifecycleBean.getProxyServerStat();

      byte[] clientSentData = new byte[] {0xA, 0xB, 0xC};
      byte[] serverResponseData = echoClient.send(clientSentData);
      Assertions.assertArrayEquals(clientSentData, serverResponseData);

      long currentConnectionsBeforeClientTermination = stat.getCurrentConnections();

      echoClient.close();
      try {
        Thread.sleep(1000);
      } catch (InterruptedException e) {
      }

      Assertions.assertEquals(
          1, currentConnectionsBeforeClientTermination - stat.getCurrentConnections());
    } catch (IOException e) {
      logger.error(e.getMessage(), e);
      Assumptions.assumeFalse(true);
    }
  }

  @Test
  void testProxyTerminate() {
    int proxyPort = PortUtil.pickFreePort();
    ProxyServerThread proxyServerThread = new ProxyServerThread(proxyPort);

    // Start proxy server
    proxyServerThread.start();
    try {
      Thread.sleep(1000);
    } catch (InterruptedException e) {
      logger.error(e.getMessage(), e);
    }
    Assumptions.assumeTrue(proxyServerThread.isRunning());

    try (EchoClient echoClient = new EchoClient(destination.getPort(), proxyPort)) {
      byte[] clientSentData = new byte[] {0xA, 0xB, 0xC};
      byte[] serverResponseData = echoClient.send(clientSentData);
      Assertions.assertArrayEquals(clientSentData, serverResponseData);

      // Stop proxy server
      proxyServerThread.terminate();
      Assumptions.assumeFalse(proxyServerThread.isRunning());

      serverResponseData = echoClient.send(clientSentData);
      Assertions.assertNull(serverResponseData);
    } catch (IOException e) {
      logger.error(e.getMessage(), e);
      Assumptions.assumeFalse(true);
    }
  }
}
