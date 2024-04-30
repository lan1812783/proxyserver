package com.lan.proxyserver.proxy.socks;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jboss.logging.Logger;

class EchoServer extends Thread {
  private static final Logger logger = Logger.getLogger(EchoServer.class);

  private final ServerSocket serverSocket;
  private final AtomicBoolean stop;

  public EchoServer() throws IOException {
    super("EchoServer");
    serverSocket = new ServerSocket(0);
    stop = new AtomicBoolean(false);
  }

  @Override
  public void start() {
    logger.info("Echo server is starting...");

    super.start();
  }

  @Override
  public void run() {
    logger.infof("Echo server is listening on port %d", serverSocket.getLocalPort());

    while (!stop.get()) {
      try {
        Socket clientSocket = serverSocket.accept();

        byte[] serverReceivedData = new byte[1024];
        int len = clientSocket.getInputStream().read(serverReceivedData);
        if (len < 0) {
          logger.debugf("Read %d from client", len);
          continue;
        }

        clientSocket.getOutputStream().write(serverReceivedData, 0, len);
        clientSocket.getOutputStream().flush();
      } catch (IOException e) {
        if (!stop.get()) {
          logger.error(e.getMessage(), e);
        }
      }
    }
  }

  public void terminate() {
    if (!stop.compareAndSet(false, true)) {
      logger.info("Echo server had been stopped");
      return;
    }

    logger.info("Echo server is stopping...");

    try {
      serverSocket.close();
    } catch (IOException e) {
      logger.error(e.getMessage(), e);
    }

    try {
      join(1000);
    } catch (InterruptedException e) {
      logger.error(e.getMessage(), e);
    }

    logger.info("Echo server stopped");
  }

  public int getPort() {
    return serverSocket.getLocalPort();
  }
}
