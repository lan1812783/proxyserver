package com.lan.proxyserver.proxy.socks.command;

public interface CommandImpl extends AutoCloseable {
  public void execute();

  /**
   * The purpose of overriding {@link AutoCloseable#close()} method is for it to not throw {@link
   * Exception}
   */
  @Override
  public void close();

  public static CommandImpl noOpCommand =
      new CommandImpl() {
        @Override
        public void execute() {}

        @Override
        public void close() {}
      };
}
