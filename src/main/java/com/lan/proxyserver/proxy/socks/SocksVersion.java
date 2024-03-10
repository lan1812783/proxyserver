package com.lan.proxyserver.proxy.socks;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import org.jboss.logging.Logger;

public enum SocksVersion {
  // SOCKS4(
  // (byte) 4,
  // (clientSocket) ->
  // () -> {
  // return false;
  // }),
  SOCKS5((byte) 5, (serverSocket, clientSocket) -> new Socks5(serverSocket, clientSocket));

  private static final Logger logger = Logger.getLogger(SocksVersion.class);

  private final byte version;
  private final SocksImplFactory socksImplFactory;

  private interface SocksImplFactory {
    public SocksImpl newImpl(ServerSocket serverSocket, Socket clientSocket);
  }

  SocksVersion(byte version, SocksImplFactory socksImplFactory) {
    this.version = version;
    this.socksImplFactory = socksImplFactory;
  }

  public static SocksVersion get(byte version) {
    for (SocksVersion sv : SocksVersion.values()) {
      if (sv.version == version) {
        return sv;
      }
    }
    return null;
  }

  public byte get() {
    return version;
  }

  public void perform(ServerSocket serverSocket, Socket clientSocket) {
    boolean success = false;

    SocksImpl socksImpl = null;
    try {
      socksImpl = socksImplFactory.newImpl(serverSocket, clientSocket);
    } catch (Exception e) {
      logger.error(e.getMessage(), e);
    }

    try {
      success = socksImpl.perform();
    } catch (IOException e) {
      logger.error(e.getMessage(), e);
    }

    if (!success) {
      logger.debugf("Perform socks version %d failed with client socket %s", version, clientSocket);
    }
  }
};
