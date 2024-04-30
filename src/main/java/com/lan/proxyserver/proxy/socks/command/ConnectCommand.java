package com.lan.proxyserver.proxy.socks.command;

import com.lan.proxyserver.proxy.socks.ReplyCode;
import com.lan.proxyserver.util.Util;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jboss.logging.Logger;

public class ConnectCommand implements CommandImpl {
  private static final Logger logger = Logger.getLogger(ConnectCommand.class);

  private final Socket clientSocket;
  private final Socket destSocket;
  private final ExecutorService pool;

  private ConnectCommand(Socket clientSocket, Socket destSocket, ExecutorService pool) {
    this.clientSocket = clientSocket;
    this.destSocket = destSocket;
    this.pool = pool;
  }

  public static CommandConstructionResult build(
      Socket clientSocket, byte[] destAddressOctets, byte[] destPortOctets, ExecutorService pool) {
    InetAddress destInetAddress = Util.getV4InetAdress(destAddressOctets);
    int destPort = Util.getPort(destPortOctets);
    if (destInetAddress == null || destPort < 0) {
      logger.debugf(
          "Failed to get destination inet address from these octets: %s, and/or destination port"
              + " from these octets: %s",
          Util.toHexString(destAddressOctets, ":"), Util.toHexString(destPortOctets, ":"));
      return new CommandConstructionResult(ReplyCode.GENERAL_FAILURE);
    }

    logger.infof(
        "Execute %s command with destination inet address: %s, and destination port: %d",
        Command.CONNECT, destInetAddress, destPort);

    Socket destSocket;
    try {
      destSocket = new Socket(destInetAddress, destPort);
    } catch (SocketException e) {
      logger.error(
          String.format(
              "Destination socket at %s:%d set up unsuccessfully", destInetAddress, destPort),
          e);
      String msg = e.getMessage();
      return msg != null && msg.startsWith("Network is unreachable")
          ? new CommandConstructionResult(ReplyCode.NETWORK_UNREACHABLE)
          : new CommandConstructionResult(ReplyCode.HOST_UNREACHABLE);
    } catch (IOException e) {
      logger.error(e.getMessage(), e);
      return new CommandConstructionResult(ReplyCode.HOST_UNREACHABLE);
    }

    try {
      // Ref:
      // https://github.com/apache/thrift/blob/master/lib/java/src/main/java/org/apache/thrift/transport/TSocket.java#L150
      destSocket.setSoLinger(false, 0);
      destSocket.setTcpNoDelay(true); // turn Nagle's algorithm off
      destSocket.setKeepAlive(true);
    } catch (SocketException e) {
      try {
        destSocket.close();
      } catch (IOException e1) {
        logger.error(e1.getMessage(), e1);
      }
      logger.error(e.getMessage(), e);
      return new CommandConstructionResult(ReplyCode.GENERAL_FAILURE);
    }

    return new CommandConstructionResult(
        ReplyCode.SUCCESS, new ConnectCommand(clientSocket, destSocket, pool));
  }

  @Override
  public void execute() {
    // Do not let backward direction log socket error when forward direction stops,
    // because forward direction will close destination socket afterward
    final AtomicBoolean fwStop = new AtomicBoolean(false);

    // --- Backward direction ---
    pool.submit(
        () -> {
          try {
            while (backward(new byte[4096])) {
              Thread.yield();
            }
          } catch (IOException e) {
            if (!Thread.currentThread().isInterrupted() && !fwStop.get()) {
              logger.error(e.getMessage(), e);
            }
          }
        });

    // --- Forward direction ---
    try {
      while (true) {
        try {
          if (!forward(new byte[4096])) {
            break;
          }
        } catch (SocketTimeoutException e) {
          if (Thread.currentThread().isInterrupted()) {
            break;
          }
        }

        Thread.yield();
      }
    } catch (IOException e) {
      logger.error(e.getMessage(), e);
    }
    fwStop.set(true);
  }

  private boolean forward(byte[] payload) throws IOException {
    int len = clientSocket.getInputStream().read(payload);
    if (len < 0) {
      logger.debugf("Read %d byte(s) from client", len);
      return false;
    }
    logger.debugf(
        "Read %d byte(s) from client, payload: %s",
        len, Util.toHexString(Arrays.copyOf(payload, len), ":"));

    destSocket.getOutputStream().write(payload, 0, len);
    destSocket.getOutputStream().flush();
    logger.debugf(
        "Write %d byte(s) to destination, payload: %s",
        len, Util.toHexString(Arrays.copyOf(payload, len), ":"));

    return true;
  }

  private boolean backward(byte[] payload) throws IOException {
    int len = destSocket.getInputStream().read(payload);
    if (len < 0) {
      logger.debugf("Read %d byte(s) from destination", len);
      return false;
    }
    logger.debugf(
        "Read %d byte(s) from destination, payload: %s",
        len, Util.toHexString(Arrays.copyOf(payload, len), ":"));

    clientSocket.getOutputStream().write(payload, 0, len);
    clientSocket.getOutputStream().flush();
    logger.debugf(
        "Write %d byte(s) to client, payload: %s",
        len, Util.toHexString(Arrays.copyOf(payload, len), ":"));

    return true;
  }

  @Override
  public void close() {
    try {
      destSocket.close();
      logger.infof("Close destination socket %s", destSocket);
    } catch (IOException e) {
      logger.error(e.getMessage(), e);
    }
  }
}
