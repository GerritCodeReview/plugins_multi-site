package com.googlesource.gerrit.plugins.multisite;

import com.google.gerrit.extensions.systemstatus.ServerInformation;
import com.google.gerrit.server.util.PluginLogFile;
import com.google.gerrit.server.util.SystemLog;
import com.google.inject.Inject;
import org.apache.log4j.PatternLayout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MultiSiteLogFile extends PluginLogFile {

  private static final String LOG_NAME = "message_log";
  public static final Logger msgLog = LoggerFactory.getLogger(LOG_NAME);

  @Inject
  public MultiSiteLogFile(SystemLog systemLog, ServerInformation serverInfo) {
    super(systemLog, serverInfo, LOG_NAME, new PatternLayout("[%d] [%t] %-5p %c %x: %m%n"));
  }
}
