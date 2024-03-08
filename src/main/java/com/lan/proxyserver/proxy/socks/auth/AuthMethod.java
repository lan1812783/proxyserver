package com.lan.proxyserver.proxy.socks.auth;

import com.lan.proxyserver.proxy.socks.SocksVersion;
import java.net.Socket;
import org.jboss.logging.Logger;

public enum AuthMethod {
  // Listed in priority
  NO_AUTH(
      (byte) 0,
      (clientSocket) -> {
        return true;
      });

  private final byte authMethod;
  private final byte[] response;

  private static class LoggerHolder {
    private static final Logger logger = Logger.getLogger(AuthMethod.class);
  }

  private static interface Authenticator {
    public boolean doAuth(Socket clientSocket);
  }

  private final Authenticator authenticator;

  AuthMethod(byte authMethod, Authenticator authenticator) {
    this.authMethod = authMethod;
    response = new byte[] {SocksVersion.SOCKS5.get(), authMethod};
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
