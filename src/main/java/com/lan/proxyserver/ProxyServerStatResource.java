package com.lan.proxyserver;

import com.lan.proxyserver.proxy.socks.SocksServerStat;
import io.quarkus.qute.Location;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/proxyserver/stat")
public class ProxyServerStatResource {
  @Location("proxyserver/stat/stat")
  Template stat;

  @GET
  @Produces(MediaType.TEXT_HTML)
  public TemplateInstance stat() {
    return stat.data("totolAcceptedConnections", SocksServerStat.totalAcceptedConnections.get())
        .data("currentConnections", SocksServerStat.currentConnections.get());
  }
}
