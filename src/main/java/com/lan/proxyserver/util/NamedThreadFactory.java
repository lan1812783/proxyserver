package com.lan.proxyserver.util;

import java.util.Objects;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;

public class NamedThreadFactory implements ThreadFactory {
  private final AtomicLong id;
  private final String namePrefix;

  public NamedThreadFactory(String namePrefix) {
    id = new AtomicLong();
    this.namePrefix = Objects.requireNonNull(namePrefix);
  }

  @Override
  public Thread newThread(Runnable r) {
    Thread t = new Thread(r);
    t.setName(namePrefix + "-" + id.getAndIncrement());
    return t;
  }
}
