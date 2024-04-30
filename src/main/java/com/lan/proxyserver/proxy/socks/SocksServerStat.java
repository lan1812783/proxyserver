package com.lan.proxyserver.proxy.socks;

import com.lan.proxyserver.util.NoOpExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicLong;

public class SocksServerStat {
  private final ExecutorService pool;
  private final AtomicLong totalAcceptedConnections;
  private final AtomicLong currentConnections;

  public static final SocksServerStat EmptyStat = new SocksServerStat(new NoOpExecutorService());

  SocksServerStat(ExecutorService pool) {
    this.pool = pool;
    totalAcceptedConnections = new AtomicLong();
    currentConnections = new AtomicLong();
  }

  void incTotalAcceptedConnections() {
    totalAcceptedConnections.incrementAndGet();
  }

  public long getTotalAcceptedConnections() {
    return totalAcceptedConnections.get();
  }

  void incCurrentConnections() {
    currentConnections.incrementAndGet();
  }

  void decCurrentConnections() {
    currentConnections.decrementAndGet();
  }

  public long getCurrentConnections() {
    return currentConnections.get();
  }
}
