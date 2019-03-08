package com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb;

import java.util.NoSuchElementException;
import org.eclipse.jgit.lib.ObjectId;

public class NoOpDfsRefDatabase implements DfsRefDatabase {
  @Override
  public boolean updateRefId(String refName, ObjectId newId, ObjectId expectedOldId)
      throws NoSuchElementException {
    return true;
  }

  @Override
  public void createRef(String refName, ObjectId id) throws IllegalArgumentException {}

  @Override
  public boolean deleteRef(String refName, ObjectId expectedId) throws NoSuchElementException {
    return true;
  }
}
