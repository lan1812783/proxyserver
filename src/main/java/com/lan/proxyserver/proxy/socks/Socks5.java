package com.lan.proxyserver.proxy.socks;

import com.lan.proxyserver.util.Util;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.jboss.logging.Logger;

public class Socks5 implements SocksImpl {
  private static final byte RESERVED_BYTE = 0;
  private static final Logger logger = Logger.getLogger(Socks5.class);

  private static enum AuthMethod {
    // Listed in priority
    NO_AUTH(
        (byte) 0,
        (clientSocket) -> {
          return true;
        });

    private final byte authMethod;
    private final byte[] response;

    private static interface IAuthenticator {
      public boolean doAuth(Socket clientSocket);
    }

    private final IAuthenticator authenticator;

    AuthMethod(byte authMethod, IAuthenticator authenticator) {
      this.authMethod = authMethod;
      response = new byte[] { SocksVersion.SOCKS5.get(), authMethod };
      this.authenticator = authenticator;
    }

    public static AuthMethod Get(byte authMethod) {
      for (AuthMethod m : AuthMethod.values()) {
        if (m.authMethod == authMethod) {
          return m;
        }
      }
      return null;
    }

    public byte[] getResponse() {
      return response;
    }

    public boolean doAuth(Socket clientSocket) {
      return authenticator.doAuth(clientSocket);
    }
  }

  private static enum Command {
    CONNECT((byte) 1);
    // BIND((byte) 2),
    // UDP_ASSOCIATE((byte) 3);

    private final byte commandCode;

    Command(byte commandCode) {
      this.commandCode = commandCode;
    }

    public static Command Get(byte commandCode) {
      for (Command c : Command.values()) {
        if (c.commandCode == commandCode) {
          return c;
        }
      }
      return null;
    }
  }

  private static enum AddressType {
    IP_V4(
        (byte) 1,
        (clientSocket) -> {
          byte[] address = new byte[4];
          int len = clientSocket.getInputStream().read(address);
          if (len != address.length) {
            logger.debugf("Failed to read destination address octets");
            return null;
          }
          return address;
        });
    // DOMAINNAME((byte) 3, (clientSocket) -> null),
    // IP_V6((byte) 4, (clientSocket) -> new byte[16]);

    private final byte type;
    private final IAddressReader addressReader;

    private static interface IAddressReader {
      public byte[] read(Socket clientSocket) throws IOException;
    }

    AddressType(byte type, IAddressReader addressReader) {
      this.type = type;
      this.addressReader = addressReader;
    }

    public static AddressType Get(byte type) {
      for (AddressType at : AddressType.values()) {
        if (at.type == type) {
          return at;
        }
      }
      return null;
    }

    public static AddressType Get(ServerSocket serverSocket) {
      InetAddress inetAddress = serverSocket.getInetAddress();
      if (inetAddress instanceof Inet4Address) {
        return AddressType.IP_V4;
      }
      // if (inetAddress instanceof Inet6Address) {
      // return AddressType.IP_V6;
      // }
      return null;
    }

    public byte get() {
      return type;
    }

    public byte[] read(Socket clientSocket) throws IOException {
      return addressReader.read(clientSocket);
    }
  }

  private static enum ReplyCode {
    SUCCESS((byte) 0),
    GENERAL_FAILURE((byte) 1),
    CONNECTION_DISALLOWED((byte) 2),
    NETWORK_UNREACHABLE((byte) 3),
    HOST_UNREACHABLE((byte) 4),
    CONNECTION_REFUSED((byte) 5),
    TTL_EXPIRED((byte) 6),
    UNSUPPORTED_COMMAND((byte) 7),
    UNSUPPORTED_ADDRESS_TYPE((byte) 8);

    private final byte code;

    ReplyCode(byte code) {
      this.code = code;
    }

    public byte get() {
      return code;
    }
  }

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
      destSocket.setSoTimeout(5000);
    } catch (IOException e) {
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
