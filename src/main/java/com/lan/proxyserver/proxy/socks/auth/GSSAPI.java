package com.lan.proxyserver.proxy.socks.auth;

import com.lan.proxyserver.util.Util;
import java.io.IOException;
import java.net.Socket;
import org.ietf.jgss.GSSContext;
import org.ietf.jgss.GSSCredential;
import org.ietf.jgss.GSSException;
import org.ietf.jgss.GSSManager;
import org.ietf.jgss.MessageProp;
import org.jboss.logging.Logger;

public class GSSAPI {
  private static final Logger logger = Logger.getLogger(GSSAPI.class);

  private static final byte PROTOCOL_VERSION_NUMBER = 1;
  private static final byte[] securityContextFailureResponse = {
    PROTOCOL_VERSION_NUMBER, (byte) 0xFF
  };
  public static final String cfgStrPrefix = "proxy_server.socks.5.auth.method.gssapi";

  private final Socket clientSocket;
  private final GSSContext gssContext;

  private enum MessageType {
    AUTHENTICATION((byte) 1),
    NEGOTIATION((byte) 2);

    private final byte type;

    MessageType(byte type) {
      this.type = type;
    }

    public byte get() {
      return type;
    }

    public static MessageType get(byte type) {
      for (MessageType mt : MessageType.values()) {
        if (mt.type == type) {
          return mt;
        }
      }
      return null;
    }
  }

  private enum ProtectionLevel {
    // Didn't see this option in rfc1961 (but found it in curl source at
    // https://github.com/curl/curl/blob/master/lib/socks_gssapi.c#L342)
    NO_PROTECTION((byte) 0),
    REQUIRED_PER_MESSAGE_INTEGRITY((byte) 1),
    REQUIRED_PER_MESSAGE_INTEGRITY_AND_CONFIDENTIALITY((byte) 2);

    private final byte level;
    private final byte[] unwrappedToken;

    ProtectionLevel(byte level) {
      this.level = level;
      unwrappedToken = new byte[] {level};
    }

    public static ProtectionLevel get(byte level) {
      for (ProtectionLevel protectionLevel : ProtectionLevel.values()) {
        if (protectionLevel.level == level) {
          return protectionLevel;
        }
      }
      return null;
    }

    public byte[] getUnwrappedToken() {
      return unwrappedToken;
    }
  }

  // static {
  // System.setProperty("javax.security.auth.useSubjectCredsOnly", "false");
  // System.setProperty(
  // "java.security.auth.login.config",
  // <path/to/JAAS/login/configuration/file>); // don't know why relative path
  // doesn't work
  // }

  public GSSAPI(Socket clientSocket) throws GSSException {
    this.clientSocket = clientSocket;

    GSSManager gssManager = GSSManager.getInstance();
    gssContext = gssManager.createContext((GSSCredential) null);
  }

  public boolean doAuth() {
    try {
      if (!establishContext()) {
        replyFailure();
        return false;
      }
      return subnegotiation();
    } catch (IOException e) {
      logger.error(e.getMessage(), e);
    }
    replyFailure();
    return false;
  }

  private boolean establishContext() throws IOException {
    byte versionNumber = Util.readByte(clientSocket);
    if (versionNumber != PROTOCOL_VERSION_NUMBER) {
      logger.debugf("Unsupported protocol version %02x", versionNumber);
      return false;
    }

    byte messageType = Util.readByte(clientSocket);
    if (messageType != MessageType.AUTHENTICATION.get()) {
      logger.debugf("Wrong message type %s", MessageType.get(messageType));
      return false;
    }

    int len = Util.readNBytesAsInt(clientSocket, 2);
    while (!gssContext.isEstablished()) {
      byte[] clientToken = Util.readExactlyNBytes(clientSocket, len);
      byte[] serverToken = null;
      try {
        serverToken = gssContext.acceptSecContext(clientToken, 0, clientToken.length);
      } catch (GSSException e) {
        logger.error(e.getMessage(), e);
        return false;
      }
      if (serverToken != null) {
        reply(MessageType.AUTHENTICATION, serverToken);
      }
    }

    return true;
  }

  private void replyFailure() {
    try {
      clientSocket.getOutputStream().write(securityContextFailureResponse);
      clientSocket.getOutputStream().flush();
    } catch (IOException e) {
      logger.error(e.getMessage(), e);
    }

    logger.info("Server replies security context failure to client");
  }

  private void reply(MessageType messageType, byte[] serverToken) throws IOException {
    byte[] response = new byte[4 + serverToken.length];
    response[0] = PROTOCOL_VERSION_NUMBER;
    response[1] = messageType.get();
    response[2] = (byte) ((serverToken.length & 0xFF00) >> Byte.SIZE);
    response[3] = (byte) (serverToken.length & 0xFF);
    System.arraycopy(serverToken, 0, response, 4 /* from 4th index */, serverToken.length);

    clientSocket.getOutputStream().write(response);
    clientSocket.getOutputStream().flush();

    logger.infof(
        "Server replies server token to client, message type %s, server token (%d byte(s)): %s",
        messageType, serverToken.length, Util.toHexString(serverToken, ":"));
  }

  private boolean subnegotiation() throws IOException {
    byte versionNumber = Util.readByte(clientSocket);
    if (versionNumber != PROTOCOL_VERSION_NUMBER) {
      logger.debugf("Unsupported protocol version %02x", versionNumber);
      return false;
    }

    byte messageType = Util.readByte(clientSocket);
    if (messageType != MessageType.NEGOTIATION.get()) {
      logger.debugf("Wrong message type %s", MessageType.get(messageType));
      return false;
    }

    // --- Get client protection level ---

    int len = Util.readNBytesAsInt(clientSocket, 2);
    byte[] clientToken = Util.readExactlyNBytes(clientSocket, len);
    byte[] clientDecapsulatedProtectionLevel = null;
    try {
      clientDecapsulatedProtectionLevel =
          gssContext.unwrap(clientToken, 0, clientToken.length, new MessageProp(0, false));
    } catch (GSSException e) {
      logger.error(e.getMessage(), e);
    }

    if (clientDecapsulatedProtectionLevel == null) {
      logger.debug("Unwrapping client's encapsulated protection level failed");
      return false;
    }
    if (clientDecapsulatedProtectionLevel.length != 1) {
      logger.debugf(
          "Invalid unwrapped client's encapsulated protection level octet length, expected %d, but"
              + " got %d",
          1, clientDecapsulatedProtectionLevel.length);
      return false;
    }

    ProtectionLevel clientProtectionLevel =
        ProtectionLevel.get(clientDecapsulatedProtectionLevel[0]);
    if (clientProtectionLevel == null) {
      logger.debugf("Unsupported client protection level %d", clientDecapsulatedProtectionLevel[0]);
      return false;
    }
    logger.infof(
        "Server receives decapsulated client's protection level %s", clientProtectionLevel);

    // --- Wrap server choice of protection level ---

    byte[] serverProtectectionLevelOctets = clientProtectionLevel.getUnwrappedToken();
    byte[] serverEncapsulatedProtectionLevel = null;
    try {
      serverEncapsulatedProtectionLevel =
          gssContext.wrap(
              serverProtectectionLevelOctets,
              0,
              serverProtectectionLevelOctets.length,
              new MessageProp(0, false));
    } catch (GSSException e) {
      logger.error(e.getMessage(), e);
      return false;
    }
    if (serverEncapsulatedProtectionLevel == null) {
      logger.debugf("Wrapping server's protection level failed");
      return false;
    }

    // --- Reply to client ---

    reply(MessageType.NEGOTIATION, serverEncapsulatedProtectionLevel);

    return true;
  }
}
