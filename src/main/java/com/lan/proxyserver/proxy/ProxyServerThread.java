package com.lan.proxyserver.proxy;

import com.lan.proxyserver.proxy.socks.SocksServer;
import com.lan.proxyserver.proxy.socks.SocksServerStat;
import java.io.IOException;
import org.jboss.logging.Logger;

public class ProxyServerThread extends Thread {
  private static final Logger logger = Logger.getLogger(ProxyServerThread.class);

  private SocksServer socksServer;

  public ProxyServerThread() {
    this(SocksServer.DEF_PORT);
  }

  public ProxyServerThread(int port) {
    super("ProxyServer");
    try {
      socksServer = new SocksServer(port);
    } catch (IOException e) {
      logger.error(e.getMessage(), e);
      logger.errorf("Cannot start proxy server on port %d", port);
    }
  }

  @Override
  public void start() {
    logger.info("Proxy server is starting...");

    super.start();
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
      logger.info("Proxy server had been stopped");
      return;
    }

    logger.info("Proxy server is stopping...");

    socksServer.stop();

    interrupt();
    try {
      join();
    } catch (InterruptedException e) {
      logger.error(e.getMessage(), e);
    }

    socksServer = null;

    logger.info("Proxy server stopped");
  }

  public SocksServerStat getSocksServerStat() {
    if (socksServer == null) {
      return SocksServerStat.EmptyStat;
    }
    return socksServer.getSocksServerStat();
  }

  public boolean isRunning() {
    if (socksServer == null) {
      return false;
    }
    return socksServer.isRunning();
  }
}
