package com.lan.proxyserver.proxy;

import com.lan.proxyserver.proxy.socks.SocksServer;
import java.io.IOException;
import org.jboss.logging.Logger;

public class ProxyServerThread extends Thread {
  private static final Logger logger = Logger.getLogger(ProxyServerThread.class);

  private final int port;
  private SocksServer socksServer;

  public ProxyServerThread() {
    port = 9003;
    try {
      socksServer = new SocksServer(port);
    } catch (IOException e) {
      logger.error(e.getMessage(), e);
      logger.errorf("Cannot start proxy server on port %d", port);
    }
  }

  @Override
  public void run() {
    if (socksServer == null) {
      return;
    }
    socksServer.run();
  }

  public void terminate() {
    if (socksServer == null) {
      return;
    }
    socksServer.stop();

    interrupt();
    try {
      join();
    } catch (InterruptedException e) {
      logger.error(e.getMessage(), e);
    }
  }
}
