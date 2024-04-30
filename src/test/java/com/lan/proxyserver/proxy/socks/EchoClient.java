package com.lan.proxyserver.proxy.socks;

import com.lan.proxyserver.util.Util;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;
import java.net.UnknownHostException;
import org.jboss.logging.Logger;

class EchoClient extends Socket {
  private static final Logger logger = Logger.getLogger(EchoServer.class);

  public EchoClient(int destPort, int proxyPort) throws UnknownHostException, IOException {
    super(new Proxy(Proxy.Type.SOCKS, new InetSocketAddress("0.0.0.0", proxyPort)));
    setSoTimeout(1000);
    connect(new InetSocketAddress("0.0.0.0", destPort));
  }

  public byte[] send(byte[] data) {
    try {
      getOutputStream().write(data);
      getOutputStream().flush();

      byte[] serverResponseData = Util.readExactlyNBytes(this, data.length);
      return serverResponseData;
    } catch (IOException e) {
      // logger.error(e.getMessage(), e);
    }
    return null;
  }
}
