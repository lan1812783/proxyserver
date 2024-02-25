package com.lan.proxyserver.util;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

public class Util {
  public static byte readByte(Socket socket) throws IOException {
    return (byte) socket.getInputStream().read();
  }

  public static String join(List<Byte> bytes, String delimeter) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < bytes.size(); i++) {
      sb.append(Byte.toString(bytes.get(i)) + (i < bytes.size() - 1 ? delimeter : ""));
    }
    return sb.toString();
  }

  public static InetAddress getInetAdress(byte[] octets) {
    if (octets.length != 4) {
      return null;
    }
    try {
      return InetAddress.getByAddress(octets);
    } catch (UnknownHostException e) {
      return null;
    }
  }

  public static int getPort(byte[] octets) {
    if (octets.length != 2) {
      return -1;
    }
    return (octets[0] & 0xFF) << 8 | (octets[1] & 0xFF);
  }

  public static void byte2hex(byte b, StringBuffer buf) {
    char[] hexChars = {
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'
    };
    int high = ((b & 0xf0) >> 4);
    int low = (b & 0x0f);
    buf.append(hexChars[high]);
    buf.append(hexChars[low]);
  }

  public static String toHexString(byte[] block) {
    return toHexString(block, "");
  }

  public static String toHexString(byte[] block, String delim) {
    StringBuffer buf = new StringBuffer();
    int len = block.length;
    for (int i = 0; i < len; i++) {
      byte2hex(block[i], buf);
      if (i < len - 1) {
        buf.append(delim);
      }
    }
    return buf.toString();
  }

  public static boolean shutdownAndAwaitTermination(
      ExecutorService threadPool, long timeout, TimeUnit unit) {
    threadPool.shutdown();
    try {
      if (!threadPool.awaitTermination(timeout, unit)) {
        threadPool.shutdownNow();
      }
    } catch (InterruptedException e) {
      threadPool.shutdownNow();
      Thread.currentThread().interrupt();
    }
    return threadPool.isTerminated();
  }
}
