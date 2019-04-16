package com.googlesource.gerrit.plugins.multisite.validation;

import org.apache.curator.RetryPolicy;

public class ZkConnectionConfig {

  public final RetryPolicy curatorRetryPolicy;
  public final Long transactionLockTimeout;


  public ZkConnectionConfig(RetryPolicy curatorRetryPolicy, Long transactionLockTimeout) {
    this.curatorRetryPolicy = curatorRetryPolicy;
    this.transactionLockTimeout = transactionLockTimeout;
  }
}
