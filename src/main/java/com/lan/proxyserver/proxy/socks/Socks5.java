package com.lan.proxyserver.proxy.socks;

import com.lan.proxyserver.proxy.socks.auth.AuthMethod;
import com.lan.proxyserver.proxy.socks.command.Command;
import com.lan.proxyserver.proxy.socks.command.CommandConstructionResult;
import com.lan.proxyserver.proxy.socks.command.CommandImpl;
import com.lan.proxyserver.util.Util;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import org.jboss.logging.Logger;

public class Socks5 implements SocksImpl {
  private static final byte RESERVED_BYTE = 0;
  private static final Logger logger = Logger.getLogger(Socks5.class);
  private static final byte[] noSuppoertedMethodsResponse = {
      SocksVersion.SOCKS5.get(), (byte) 0xFF
  };

  private final ExecutorService pool;

  private final AddressType serverAddressType;
  private final byte[] serverAddressOctets;
  private final byte[] serverPortOctets;
  private final Socket clientSocket;
  private Command command;
  private AddressType destAddressType;
  private byte[] destAddressOctets;
  private byte[] destPortOctets;

  Socks5(ServerSocket serverSocket, Socket clientSocket, ExecutorService pool) {
    serverAddressType = AddressType.get(serverSocket);
    if (serverAddressType == null) {
      throw new UnsupportedOperationException("Unsupported server address version" + serverSocket);
    }
    serverAddressOctets = serverSocket.getInetAddress().getAddress();

    int serverPort = serverSocket.getLocalPort();
    serverPortOctets = new byte[2];
    serverPortOctets[0] = (byte) ((serverPort & 0xFF00) >> Byte.SIZE);
    serverPortOctets[1] = (byte) (serverPort & 0xFF);

    this.clientSocket = clientSocket;
    this.pool = pool;
  }

  @Override
  public boolean perform() throws IOException {
    if (!doAuth()) {
      logger.debug("Authentication failed");
      clientSocket.getOutputStream().write(noSuppoertedMethodsResponse);
      return false;
    }

    ReplyCode replyCode = processRequest();
    if (replyCode != ReplyCode.SUCCESS) {
      logger.debug("Process request failed");
      reply(replyCode);
      return false;
    }

    CommandConstructionResult res = command.build(clientSocket, destAddressOctets, destPortOctets, pool);
    try (CommandImpl commandImpl = res.commandImpl) {
      if (!reply(res.replyCode)) {
        return false;
      }
      commandImpl.execute();
    }

    return true;
  }

  public boolean doAuth() throws IOException {
    byte nmethods = Util.readByte(clientSocket);
    byte[] clientAuthMethods = Util.readExactlyNBytes(clientSocket, nmethods);
    AuthMethod authMethod = null;
    for (byte cliAuthMethod : clientAuthMethods) {
      if ((authMethod = AuthMethod.get(cliAuthMethod)) != null) {
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
    byte versionNumber = Util.readByte(clientSocket);
    SocksVersion socksVersion = SocksVersion.get(versionNumber);
    if (socksVersion == null) {
      logger.debugf("Unsupported socks version %02x", versionNumber);
      return ReplyCode.GENERAL_FAILURE;
    }

    byte commandCode = Util.readByte(clientSocket);
    command = Command.get(commandCode);
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
    destAddressType = AddressType.get(addrType);
    if (destAddressType == null) {
      logger.debugf("Unsupported address type %02x", addrType);
      return ReplyCode.UNSUPPORTED_ADDRESS_TYPE;
    }
    destAddressOctets = destAddressType.read(clientSocket);

    destPortOctets = new byte[2];
    int len = clientSocket.getInputStream().read(destPortOctets);
    if (len != destPortOctets.length) {
      logger.debug("Failed to read destination port octets");
      return ReplyCode.GENERAL_FAILURE;
    }

    logger.infof("Server receives %s command", command);

    return ReplyCode.SUCCESS;
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

    logger.infof("Server replies %s to client", replyCode);
    return true;
  }
}
