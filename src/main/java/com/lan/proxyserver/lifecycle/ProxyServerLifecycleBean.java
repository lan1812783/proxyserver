package com.lan.proxyserver.lifecycle;

import com.lan.proxyserver.proxy.ProxyServerThread;
import com.lan.proxyserver.proxy.socks.SocksServerStat;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

@ApplicationScoped
public class ProxyServerLifecycleBean {
  private static ProxyServerThread proxyServerThread = new ProxyServerThread();

  void onStart(@Observes StartupEvent ev) {
    proxyServerThread.start();
  }

  void onStop(@Observes ShutdownEvent ev) {
    proxyServerThread.terminate();
  }

  public static SocksServerStat getProxyServerStat() {
    return proxyServerThread.getSocksServerStat();
  }
}
