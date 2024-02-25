package com.lan.proxyserver.proxy.socks;

import java.io.IOException;

public interface SocksImpl {
  public boolean perform() throws IOException;
}
