package com.lan.proxyserver.proxy.socks.command;

import com.lan.proxyserver.proxy.socks.ReplyCode;

public class CommandConstructionResult {
  public final ReplyCode replyCode;
  public final CommandImpl commandImpl;

  public CommandConstructionResult(ReplyCode replyCode) {
    this(replyCode, CommandImpl.noOpCommand);
  }

  public CommandConstructionResult(ReplyCode replyCode, CommandImpl commandImpl) {
    this.replyCode = replyCode;
    this.commandImpl = commandImpl;
  }
}
