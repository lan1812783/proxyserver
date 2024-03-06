package com.lan.proxyserver.proxy.socks.command;

import java.net.Socket;

public enum Command {
  CONNECT(
      (byte) 1,
      (clientSocket, destAddressOctets, destPortOctets) -> ConnectCommand.build(clientSocket, destAddressOctets,
          destPortOctets));
  // BIND((byte) 2),
  // UDP_ASSOCIATE((byte) 3);

  private final byte commandCode;
  private final CommandImplBuilder commandImplBuilder;

  private static interface CommandImplBuilder {
    public CommandConstructionResult build(
        Socket clientSocket, byte[] destAddressOctets, byte[] destPortOctets);
  }

  Command(byte commandCode, CommandImplBuilder commandImplBuilder) {
    this.commandCode = commandCode;
    this.commandImplBuilder = commandImplBuilder;
  }

  public static Command Get(byte commandCode) {
    for (Command c : Command.values()) {
      if (c.commandCode == commandCode) {
        return c;
      }
    }
    return null;
  }

  public CommandConstructionResult build(
      Socket clientSocket, byte[] destAddressOctets, byte[] destPortOctets) {
    return commandImplBuilder.build(clientSocket, destAddressOctets, destPortOctets);
  }
}
