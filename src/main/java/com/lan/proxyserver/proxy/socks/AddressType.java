package com.lan.proxyserver.proxy.socks;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import org.jboss.logging.Logger;

public enum AddressType {
  IP_V4(
      (byte) 1,
      (clientSocket) -> {
        byte[] address = new byte[4];
        int len = clientSocket.getInputStream().read(address);
        if (len != address.length) {
          LoggerHolder.logger.debug("Failed to read destination address octets");
          return null;
        }
        return address;
      });

  // DOMAINNAME((byte) 3, (clientSocket) -> null),
  // IP_V6((byte) 4, (clientSocket) -> new byte[16]);

  private static class LoggerHolder {
    private static final Logger logger = Logger.getLogger(AddressType.class);
  }

  private final byte type;
  private final AddressReader addressReader;

  private static interface AddressReader {
    public byte[] read(Socket clientSocket) throws IOException;
  }

  AddressType(byte type, AddressReader addressReader) {
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
