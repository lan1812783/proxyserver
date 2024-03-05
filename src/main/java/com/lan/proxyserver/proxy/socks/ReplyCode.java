package com.lan.proxyserver.proxy.socks;

public enum ReplyCode {
  SUCCESS((byte) 0),
  GENERAL_FAILURE((byte) 1),
  CONNECTION_DISALLOWED((byte) 2),
  NETWORK_UNREACHABLE((byte) 3),
  HOST_UNREACHABLE((byte) 4),
  CONNECTION_REFUSED((byte) 5),
  TTL_EXPIRED((byte) 6),
  UNSUPPORTED_COMMAND((byte) 7),
  UNSUPPORTED_ADDRESS_TYPE((byte) 8);

  private final byte code;

  ReplyCode(byte code) {
    this.code = code;
  }

  public byte get() {
    return code;
  }
}
