package com.lan.proxyserver.proxy.socks.auth;

import com.lan.proxyserver.config.Configer;
import com.lan.proxyserver.util.Util;
import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jboss.logging.Logger;

public class UsernamePassword {
  private static final Logger logger = Logger.getLogger(UsernamePassword.class);

  private static final byte PROTOCOL_VERSION_NUMBER = 1;
  private static final byte[] successResponse = {PROTOCOL_VERSION_NUMBER, 0};
  private static final byte[] failureResponse = {PROTOCOL_VERSION_NUMBER, 1};
  private static final Map<String, String> usrPwds = new ConcurrentHashMap<>();

  public static final String cfgStrPrefix = "proxy_server.socks.5.auth.method.usr_pwd";

  private final Socket clientSocket;

  public static void init() {
    boolean enable = Configer.getBool(false, cfgStrPrefix, "enable");
    if (!enable) {
      return;
    }

    String defaultUsername = Configer.getStr(null, cfgStrPrefix, "default_username");
    String defaultPassword = Configer.getStr(null, cfgStrPrefix, "default_password");
    if (defaultUsername != null || defaultPassword != null) {
      usrPwds.put(defaultUsername, defaultPassword);
    }

    if (usrPwds.isEmpty()) {
      logger.fatal("No credentials found");
    }
  }

  public UsernamePassword(Socket clientSocket) {
    this.clientSocket = clientSocket;
  }

  public boolean doAuth() {
    try {
      boolean success = verify();
      reply(success);
      return success;
    } catch (IOException e) {
      logger.error(e.getMessage(), e);
    }
    reply(false);
    return false;
  }

  private boolean verify() throws IOException {
    byte versionNumber = Util.readByte(clientSocket);
    if (versionNumber != PROTOCOL_VERSION_NUMBER) {
      logger.debugf("Unsupported protocol version %02x", versionNumber);
      return false;
    }

    byte usernameLen = Util.readByte(clientSocket);
    byte[] usernameOctets = Util.readExactlyNBytes(clientSocket, usernameLen);
    String username = new String(usernameOctets, StandardCharsets.US_ASCII);

    byte passwordLen = Util.readByte(clientSocket);
    byte[] passwordOcters = Util.readExactlyNBytes(clientSocket, passwordLen);
    String password = new String(passwordOcters, StandardCharsets.US_ASCII);

    return verifyPwdForUser(username, password);
  }

  private boolean verifyPwdForUser(String username, String password) {
    String usrPwd = usrPwds.get(username);
    if (usrPwd == null) {
      logger.debugf("Username '%s' does not exist", username);
      return false;
    }
    if (usrPwd.equals(password)) {
      logger.infof("Password verification for username '%s' succeeded", username);
      return true;
    }
    logger.debugf("Wrong password for username '%s'", username);
    return false;
  }

  private void reply(boolean success) {
    try {
      clientSocket.getOutputStream().write(success ? successResponse : failureResponse);
    } catch (IOException e) {
      logger.error(e.getMessage(), e);
    }
  }
}
