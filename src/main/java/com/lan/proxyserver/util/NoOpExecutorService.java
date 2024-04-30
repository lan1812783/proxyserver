package com.lan.proxyserver.util;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class NoOpExecutorService implements ExecutorService {

  @Override
  public void execute(Runnable command) {
    // TODO Auto-generated method stub
    throw new UnsupportedOperationException("Unimplemented method 'execute'");
  }

  @Override
  public void shutdown() {
    // TODO Auto-generated method stub
    throw new UnsupportedOperationException("Unimplemented method 'shutdown'");
  }

  @Override
  public List<Runnable> shutdownNow() {
    // TODO Auto-generated method stub
    throw new UnsupportedOperationException("Unimplemented method 'shutdownNow'");
  }

  @Override
  public boolean isShutdown() {
    // TODO Auto-generated method stub
    throw new UnsupportedOperationException("Unimplemented method 'isShutdown'");
  }

  @Override
  public boolean isTerminated() {
    // TODO Auto-generated method stub
    throw new UnsupportedOperationException("Unimplemented method 'isTerminated'");
  }

  @Override
  public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
    // TODO Auto-generated method stub
    throw new UnsupportedOperationException("Unimplemented method 'awaitTermination'");
  }

  @Override
  public <T> Future<T> submit(Callable<T> task) {
    // TODO Auto-generated method stub
    throw new UnsupportedOperationException("Unimplemented method 'submit'");
  }

  @Override
  public <T> Future<T> submit(Runnable task, T result) {
    // TODO Auto-generated method stub
    throw new UnsupportedOperationException("Unimplemented method 'submit'");
  }

  @Override
  public Future<?> submit(Runnable task) {
    // TODO Auto-generated method stub
    throw new UnsupportedOperationException("Unimplemented method 'submit'");
  }

  @Override
  public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks)
      throws InterruptedException {
    // TODO Auto-generated method stub
    throw new UnsupportedOperationException("Unimplemented method 'invokeAll'");
  }

  @Override
  public <T> List<Future<T>> invokeAll(
      Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
      throws InterruptedException {
    // TODO Auto-generated method stub
    throw new UnsupportedOperationException("Unimplemented method 'invokeAll'");
  }

  @Override
  public <T> T invokeAny(Collection<? extends Callable<T>> tasks)
      throws InterruptedException, ExecutionException {
    // TODO Auto-generated method stub
    throw new UnsupportedOperationException("Unimplemented method 'invokeAny'");
  }

  @Override
  public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
      throws InterruptedException, ExecutionException, TimeoutException {
    // TODO Auto-generated method stub
    throw new UnsupportedOperationException("Unimplemented method 'invokeAny'");
  }
}
