package com.lan.proxyserver.proxy.socks;

import com.lan.proxyserver.util.Util;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.net.ServerSocketFactory;
import org.jboss.logging.Logger;

public class SocksServer implements Runnable {
  private static final Logger logger = Logger.getLogger(SocksServer.class);

  private final ServerSocket serverSocket;
  private final AtomicBoolean running;
  private final AtomicBoolean stop;
  private final ExecutorService pool;

  public SocksServer(int port) throws IOException {
    serverSocket = ServerSocketFactory.getDefault().createServerSocket(port);
    running = new AtomicBoolean(false);
    stop = new AtomicBoolean(false);
    pool = Executors.newCachedThreadPool();
  }

  public void stop() {
    stop.set(true);
    cleanup();
  }

  private void cleanup() {
    closeServerSocket();
    terminatePool();
  }

  private void closeServerSocket() {
    try {
      serverSocket.close();
      logger.infof("Close server socket %s", serverSocket);
    } catch (IOException e) {
      logger.error(e.getMessage(), e);
    }
  }

  private void terminatePool() {
    long timeout = 10;
    TimeUnit unit = TimeUnit.SECONDS;
    logger.info("Wait for client connection handler pool to shutdown");
    boolean terminated = Util.shutdownAndAwaitTermination(pool, timeout, unit);
    if (!terminated) {
      logger.infof(
          "Client connection handler pool didn't terminate after %d %s -> FORCE QUIT",
          timeout, unit.toString());
    }
  }

  @Override
  public void run() {
    if (stop.get()) {
      logger.error(
          "Socks server has been ordered to stop running and clean itself up, create a new one to"
              + " use");
    }
    if (!running.compareAndSet(false, true)) {
      logger.error("Socks server has already been running");
      return;
    }
    logger.infof("Socks server is listening on port %d", serverSocket.getLocalPort());
    while (!stop.get()) {
      try {
        runImpl();
      } catch (IOException e) {
        logger.error(e.getMessage(), e);
      }
    }
    cleanup();
    running.set(false);
  }

  private void runImpl() throws IOException {
    Socket clientSocket = serverSocket.accept();
    logger.info("Socks server accepts a connection");
    pool.execute(new Handler(serverSocket, clientSocket));

    SocksServerStat.totalAcceptedConnections.incrementAndGet();
  }

  private static class Handler implements Runnable {
    private final ServerSocket serverSocket;
    private final Socket clientSocket;

    Handler(ServerSocket serverSocket, Socket clientSocket) {
      this.serverSocket = serverSocket;
      this.clientSocket = clientSocket;
    }

    @Override
    public void run() {
      try {
        runImpl();
      } catch (IOException e) {
        logger.error(e.getMessage(), e);
      }
    }

    public void runImpl() throws IOException {
      SocksServerStat.currentConnections.incrementAndGet();

      byte versionNumber = Util.readByte(clientSocket);
      SocksVersion socksVersion = SocksVersion.Get(versionNumber);
      if (socksVersion == null) {
        logger.debugf("Unsupported socks version %02x", versionNumber);
        return;
      }
      socksVersion.perform(serverSocket, clientSocket);

      try {
        clientSocket.close();
        logger.infof("Close client socket %s", clientSocket);

        SocksServerStat.currentConnections.decrementAndGet();
      } catch (IOException e) {
        logger.debug(e.getMessage(), e);
      }
    }
  }
}
