package com.lan.proxyserver.lifecycle;

import com.lan.proxyserver.proxy.ProxyServerThread;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.jboss.logging.Logger;

@ApplicationScoped
public class ProxyServerLifecycleBean {
  private static final Logger logger = Logger.getLogger("ProxyServerLifecycleBean");

  private ProxyServerThread proxyServerThread;

  void onStart(@Observes StartupEvent ev) {
    logger.info("Proxy server is starting...");

    proxyServerThread = new ProxyServerThread();

    proxyServerThread.start();
  }

  void onStop(@Observes ShutdownEvent ev) {
    logger.info("Proxy server is stopping...");

    proxyServerThread.terminate();
  }
}
