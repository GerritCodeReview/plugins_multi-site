package com.googlesource.gerrit.plugins.multisite.validation;

import com.googlesource.gerrit.plugins.multisite.LockWrapper;
import org.junit.Ignore;

@Ignore
public class DummyLockWrapper implements LockWrapper.Factory {

  @Override
  public LockWrapper create(String project, String refName, AutoCloseable lock) {
    return new LockWrapper(new DisabledSharedRefLogger(), project, refName, lock);
  }
}
