package com.lan.proxyserver.proxy.socks;

import com.lan.proxyserver.util.Util;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.jboss.logging.Logger;

public class Socks5 implements SocksImpl {
  private static final byte RESERVED_BYTE = 0;
  private static final Logger logger = Logger.getLogger(Socks5.class);
  private static final byte[] NoSuppoertedMethodsResponse = {
      SocksVersion.SOCKS5.get(), (byte) 0xFF
  };

  private final AddressType serverAddressType;
  private final byte[] serverAddressOctets;
  private final byte[] serverPortOctets;
  private final Socket clientSocket;
  private Command command;
  private AddressType destAddressType;
  private byte[] destAddressOctets;
  private byte[] destPortOctets;
  private Socket destSocket;

  Socks5(ServerSocket serverSocket, Socket clientSocket) {
    serverAddressType = AddressType.Get(serverSocket);
    if (serverAddressType == null) {
      throw new UnsupportedOperationException("Unsupported server address version" + serverSocket);
    }
    serverAddressOctets = serverSocket.getInetAddress().getAddress();

    int serverPort = serverSocket.getLocalPort();
    serverPortOctets = new byte[2];
    serverPortOctets[0] = (byte) ((serverPort & 0xFF00) >> 8);
    serverPortOctets[1] = (byte) (serverPort & 0xFF);

    this.clientSocket = clientSocket;
  }

  @Override
  public boolean perform() throws IOException {
    if (!doAuth()) {
      logger.debug("Authentication failed");
      clientSocket.getOutputStream().write(NoSuppoertedMethodsResponse);
      return false;
    }

    ReplyCode replyCode = processRequest();
    if (replyCode != ReplyCode.SUCCESS) {
      logger.debug("Process request failed");
      reply(replyCode);
      return false;
    }

    boolean commandExecSuccess = false;
    switch (command) {
      case CONNECT:
        commandExecSuccess = executeConnectCommand();
    }

    if (!commandExecSuccess) {
      logger.debugf("Execute command %s failed", command);
    }

    return commandExecSuccess;
  }

  public boolean doAuth() throws IOException {
    byte nauth = Util.readByte(clientSocket);
    AuthMethod authMethod = null;
    List<Byte> clientAuthMethods = new ArrayList<>(nauth);
    for (int i = 0; i < nauth; i++) {
      byte cliAuthMethod = Util.readByte(clientSocket);
      clientAuthMethods.add(cliAuthMethod);
      if ((authMethod = AuthMethod.Get(cliAuthMethod)) != null) {
        break;
      }
    }

    if (authMethod == null) {
      logger.debugf(
          "No supported authentication method found in methods offered by client: %s",
          Util.join(clientAuthMethods, ", "));
      return false;
    }
    clientSocket.getOutputStream().write(authMethod.getResponse());

    logger.infof("Server chooses authentication method %s", authMethod);

    return authMethod.doAuth(clientSocket);
  }

  public ReplyCode processRequest() throws IOException {
    // byte[] buf = new byte[128];
    // int len = clientSocket.getInputStream().read(buf);
    // logger.debugf("Buf: %s, len: %d", Util.ToHexString(buf, ":"), len);
    // return ReplyCode.GENERAL_FAILURE;

    Util.readByte(clientSocket); // TODO: why redundant byte here (because of NO_AUTH ???)

    byte versionNumber = Util.readByte(clientSocket);
    SocksVersion socksVersion = SocksVersion.Get(versionNumber);
    if (socksVersion == null) {
      logger.debugf("Unsupported socks version %02x", versionNumber);
      return ReplyCode.GENERAL_FAILURE;
    }

    byte commandCode = Util.readByte(clientSocket);
    command = Command.Get(commandCode);
    if (command == null) {
      logger.debugf("Unsupported command code %02x", commandCode);
      return ReplyCode.UNSUPPORTED_COMMAND;
    }

    byte reservedByte = Util.readByte(clientSocket);
    if (reservedByte != RESERVED_BYTE) {
      logger.debugf(
          "Wrong reseved byte, expected %02x, received %02x", RESERVED_BYTE, reservedByte);
      return ReplyCode.GENERAL_FAILURE;
    }

    byte addrType = Util.readByte(clientSocket);
    destAddressType = AddressType.Get(addrType);
    if (destAddressType == null) {
      logger.debugf("Unsupported address type %02x", addrType);
      return ReplyCode.UNSUPPORTED_ADDRESS_TYPE;
    }
    destAddressOctets = destAddressType.read(clientSocket);

    destPortOctets = new byte[2];
    int len = clientSocket.getInputStream().read(destPortOctets);
    if (len != destPortOctets.length) {
      logger.debugf("Failed to read destination port octets");
      return ReplyCode.GENERAL_FAILURE;
    }

    return ReplyCode.SUCCESS;
  }

  private boolean executeConnectCommand() {
    InetAddress destInetAddress = Util.getV4InetAdress(destAddressOctets);
    int destPort = Util.getPort(destPortOctets);
    if (destInetAddress == null || destPort < 0) {
      reply(ReplyCode.GENERAL_FAILURE);
      logger.debugf(
          "Failed to get destination inet address from these octets: %s, and/or destination port"
              + " from these octets: %s",
          Util.toHexString(destAddressOctets, ":"), Util.toHexString(destPortOctets, ":"));
      return false;
    }

    logger.infof(
        "Execute %s command with destination inet address: %s, and destination port: %d",
        Command.CONNECT, destInetAddress, destPort);

    try {
      destSocket = new Socket(destInetAddress, destPort);
    } catch (SocketException e) {
      logger.error(e.getMessage(), e);
      String msg = e.getMessage();
      if (msg != null && msg.startsWith("Network is unreachable")) {
        reply(ReplyCode.NETWORK_UNREACHABLE);
      } else {
        reply(ReplyCode.HOST_UNREACHABLE);
      }
      return false;
    } catch (IOException e) {
      logger.error(e.getMessage(), e);
      reply(ReplyCode.HOST_UNREACHABLE);
      return false;
    }

    try {
      destSocket.setSoTimeout(5000);
    } catch (SocketException e) {
      cleanup();
      logger.error(e.getMessage(), e);
      reply(ReplyCode.GENERAL_FAILURE);
      return false;
    }

    if (!reply(ReplyCode.SUCCESS)) {
      cleanup();
      return false;
    }

    try {
      while (forwardAndBackward()) {
        Thread.yield();
      }
    } catch (IOException e) {
      cleanup();
      logger.error(e.getMessage(), e);
    }

    cleanup();

    return true;
  }

  private boolean reply(ReplyCode replyCode) {
    byte[] response = new byte[6 + serverAddressOctets.length];
    response[0] = SocksVersion.SOCKS5.get();
    response[1] = replyCode.get();
    response[2] = RESERVED_BYTE;
    response[3] = serverAddressType.get();

    for (int i = 0; i < serverAddressOctets.length; i++) {
      response[4 + i] = serverAddressOctets[i];
    }

    for (int i = 0; i < serverPortOctets.length; i++) {
      response[4 + serverAddressOctets.length + i] = serverPortOctets[i];
    }

    try {
      clientSocket.getOutputStream().write(response);
    } catch (IOException e) {
      logger.error(e.getMessage(), e);
      return false;
    }

    return true;
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

  private void cleanup() {
    try {
      destSocket.close();
      logger.infof("Close destination socket %s", destSocket);
    } catch (IOException e) {
      logger.error(e.getMessage(), e);
    }
  }
}
