package com.lan.proxyserver.util;

import java.io.IOException;
import java.net.ServerSocket;

public final class PortUtil {
  public static synchronized int pickFreePort() {
    try (ServerSocket ss = new ServerSocket(0) /*
                                                * a port number of 0 means that the port number is automatically
                                                * allocated, typically from an ephemeral port range
                                                */) {
      ss.setReuseAddress(true);
      return ss.getLocalPort();
    } catch (IOException e) {
    }
    return -1;
  }
}
