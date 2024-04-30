package com.lan.proxyserver.config;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;

public class Configer {
  private static volatile Config config;

  public static Config getConfig() {
    if (config == null) {
      synchronized (Configer.class) {
        if (config == null) {
          config = ConfigProvider.getConfig();
        }
      }
    }
    return config;
  }

  private static String buildKey(String... sections) {
    return String.join(".", sections);
  }

  public static boolean getBool(boolean def, String... sections) {
    return getConfig().getOptionalValue(buildKey(sections), Boolean.class).orElse(def);
  }

  public static String getStr(String def, String... sections) {
    return getConfig().getOptionalValue(buildKey(sections), String.class).orElse(def);
  }
}
