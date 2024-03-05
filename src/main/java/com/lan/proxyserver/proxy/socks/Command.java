package com.lan.proxyserver.proxy.socks;

public enum Command {
  CONNECT((byte) 1);
  // BIND((byte) 2),
  // UDP_ASSOCIATE((byte) 3);

  private final byte commandCode;

  Command(byte commandCode) {
    this.commandCode = commandCode;
  }

  public static Command Get(byte commandCode) {
    for (Command c : Command.values()) {
      if (c.commandCode == commandCode) {
        return c;
      }
    }
    return null;
  }
}
