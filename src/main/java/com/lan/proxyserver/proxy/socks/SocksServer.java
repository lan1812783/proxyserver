package com.lan.proxyserver.proxy.socks;

import com.lan.proxyserver.proxy.socks.auth.UsernamePassword;
import com.lan.proxyserver.util.NamedThreadFactory;
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
  public static final int DEF_PORT = 1080;

  private final ServerSocket serverSocket;
  private final AtomicBoolean running;
  private final AtomicBoolean stop;
  private final ExecutorService pool;

  private final SocksServerStat stat;

  public SocksServer(int port) throws IOException {
    serverSocket = ServerSocketFactory.getDefault().createServerSocket(port);
    running = new AtomicBoolean(false);
    stop = new AtomicBoolean(false);
    pool =
        Executors.newCachedThreadPool(
            new NamedThreadFactory(SocksServer.class.getSimpleName() + "-pool"));

    stat = new SocksServerStat(pool);

    UsernamePassword.init();
  }

  public void stop() {
    if (!stop.compareAndSet(false, true)) {
      logger.info("Socks server has been stopped");
      return;
    }
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
    logger.info("Wait for client connection handler pool to shutdown...");
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
        // Socket.close() (called when cleaning up) causes any thread currently blocked
        // in Socket.accept() will throw a SocketException with message "Socket closed",
        // put a check here to not log this misleading messages
        if (!stop.get()) {
          logger.error(e.getMessage(), e);
        }
      }
    }
    running.set(false);
  }

  private void runImpl() throws IOException {
    Socket clientSocket = serverSocket.accept();
    // Client socket timeout is useful when graceful socks server shutdown is
    // performed (forward and backward threads are interrupted), the socks server
    // will then wait for forward direction to time out, then close the destination
    // socket which in turn cause backward direction to throw socket
    // exception, later end also
    // Note that server socket does not need timeout because it doesn't have the
    // ability to close client socket, so if it does have timeout, it will just
    // ignore the timeout exception and move on
    clientSocket.setSoTimeout(5000);
    logger.info("Socks server accepts a connection");
    pool.execute(new Handler(serverSocket, clientSocket));

    stat.incTotalAcceptedConnections();
  }

  public SocksServerStat getSocksServerStat() {
    return stat;
  }

  public boolean isRunning() {
    return running.get();
  }

  private class Handler implements Runnable {
    private final ServerSocket serverSocket;
    private final Socket clientSocket;

    Handler(ServerSocket serverSocket, Socket clientSocket) {
      this.serverSocket = serverSocket;
      this.clientSocket = clientSocket;
    }

    @Override
    public void run() {
      stat.incCurrentConnections();

      try {
        runImpl();
      } catch (IOException e) {
        logger.error(e.getMessage(), e);
      }

      try {
        clientSocket.close();
        logger.infof("Close client socket %s", clientSocket);
      } catch (IOException e) {
        logger.debug(e.getMessage(), e);
      }

      stat.decCurrentConnections();
    }

    public void runImpl() throws IOException {
      byte versionNumber = Util.readByte(clientSocket);
      SocksVersion socksVersion = SocksVersion.get(versionNumber);
      if (socksVersion == null) {
        logger.debugf("Unsupported socks version %02x", versionNumber);
        return;
      }
      socksVersion.perform(serverSocket, clientSocket, pool);
    }
  }
}
