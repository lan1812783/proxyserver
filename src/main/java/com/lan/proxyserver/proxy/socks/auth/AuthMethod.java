package com.lan.proxyserver.proxy.socks.auth;

import com.lan.proxyserver.config.Configer;
import com.lan.proxyserver.proxy.socks.SocksVersion;
import java.net.Socket;
import org.ietf.jgss.GSSException;
import org.jboss.logging.Logger;

public enum AuthMethod {
  NO_AUTH(
      (byte) 0,
      (clientSocket) -> {
        return true;
      },
      Configer.getBool(false, "proxy_server.socks.5.auth.method.no_auth.enable")),
  GSSAPI(
      (byte) 1,
      (clientSocket) -> {
        try {
          return new GSSAPI(clientSocket).doAuth();
        } catch (GSSException e) {
          LoggerHolder.logger.error(e.getMessage(), e);
        }
        return false;
      },
      Configer.getBool(false, com.lan.proxyserver.proxy.socks.auth.GSSAPI.cfgStrPrefix, "enable")),
  USR_PWD(
      (byte) 2,
      (clientSocket) -> {
        return new UsernamePassword(clientSocket).doAuth();
      },
      Configer.getBool(false, UsernamePassword.cfgStrPrefix, "enable"));

  private final byte authMethod;
  private final byte[] response;

  private static class LoggerHolder {
    private static final Logger logger = Logger.getLogger(AuthMethod.class);
  }

  private static interface Authenticator {
    public boolean doAuth(Socket clientSocket);
  }

  private final Authenticator authenticator;
  private final boolean enable;

  AuthMethod(byte authMethod, Authenticator authenticator, boolean enable) {
    this.authMethod = authMethod;
    response = new byte[] {SocksVersion.SOCKS5.get(), authMethod};
    this.authenticator = authenticator;
    this.enable = enable;
  }

  public static AuthMethod get(byte authMethod) {
    for (AuthMethod m : AuthMethod.values()) {
      if (m.authMethod == authMethod && m.enable) {
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
