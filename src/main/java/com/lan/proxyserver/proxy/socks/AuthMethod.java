package com.lan.proxyserver.proxy.socks;

import java.net.Socket;

public enum AuthMethod {
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
