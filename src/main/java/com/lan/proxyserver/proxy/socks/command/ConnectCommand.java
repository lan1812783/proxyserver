package com.lan.proxyserver.proxy.socks.command;

import com.lan.proxyserver.proxy.socks.ReplyCode;
import com.lan.proxyserver.util.Util;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.util.Arrays;
import org.jboss.logging.Logger;

public class ConnectCommand extends CommandImpl {
  private static final Logger logger = Logger.getLogger(ConnectCommand.class);

  private final Socket clientSocket;
  private final Socket destSocket;

  private ConnectCommand(Socket clientSocket, Socket destSocket) {
    this.clientSocket = clientSocket;
    this.destSocket = destSocket;
  }

  public static CommandConstructionResult build(
      Socket clientSocket, byte[] destAddressOctets, byte[] destPortOctets) {
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
      logger.error(e.getMessage(), e);
      String msg = e.getMessage();
      return msg != null && msg.startsWith("Network is unreachable")
          ? new CommandConstructionResult(ReplyCode.NETWORK_UNREACHABLE)
          : new CommandConstructionResult(ReplyCode.HOST_UNREACHABLE);
    } catch (IOException e) {
      logger.error(e.getMessage(), e);
      return new CommandConstructionResult(ReplyCode.HOST_UNREACHABLE);
    }

    try {
      destSocket.setSoTimeout(5000);
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
        ReplyCode.SUCCESS, new ConnectCommand(clientSocket, destSocket));
  }

  @Override
  public void execute() {
    try {
      while (forwardAndBackward()) {
        Thread.yield();
      }
    } catch (IOException e) {
      logger.error(e.getMessage(), e);
    }
  }

  private boolean forwardAndBackward() throws IOException {
    byte[] payload = new byte[4096];

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

    len = destSocket.getInputStream().read(payload);
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
  public void close() throws Exception {
    try {
      destSocket.close();
      logger.infof("Close destination socket %s", destSocket);
    } catch (IOException e) {
      logger.error(e.getMessage(), e);
    }
  }
}
