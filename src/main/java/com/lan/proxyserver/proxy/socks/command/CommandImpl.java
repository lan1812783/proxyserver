package com.lan.proxyserver.proxy.socks.command;

public abstract class CommandImpl implements AutoCloseable {
  public abstract void execute();

  public static CommandImpl noOpCommand =
      new CommandImpl() {
        @Override
        public void execute() {}

        @Override
        public void close() throws Exception {}
      };
}
