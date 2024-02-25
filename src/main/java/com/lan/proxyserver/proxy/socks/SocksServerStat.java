package com.lan.proxyserver.proxy.socks;

import java.util.concurrent.atomic.AtomicLong;

public class SocksServerStat {
  public static final AtomicLong totalAcceptedConnections = new AtomicLong();
  public static final AtomicLong currentConnections = new AtomicLong();
}
