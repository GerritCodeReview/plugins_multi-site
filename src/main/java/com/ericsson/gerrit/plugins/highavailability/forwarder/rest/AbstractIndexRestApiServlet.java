// Copyright (C) 2017 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.ericsson.gerrit.plugins.highavailability.forwarder.rest;

import static javax.servlet.http.HttpServletResponse.SC_CONFLICT;
import static javax.servlet.http.HttpServletResponse.SC_METHOD_NOT_ALLOWED;
import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import static javax.servlet.http.HttpServletResponse.SC_NO_CONTENT;

import com.ericsson.gerrit.plugins.highavailability.forwarder.Context;
import com.google.common.util.concurrent.Striped;
import com.google.gwtorm.server.OrmException;
import java.io.IOException;
import java.util.concurrent.locks.Lock;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public abstract class AbstractIndexRestApiServlet<T> extends AbstractRestApiServlet {
  private static final long serialVersionUID = -1L;

  private final IndexName indexName;
  private final boolean allowDelete;
  private final Striped<Lock> idLocks;

  enum Operation {
    INDEX,
    DELETE;

    @Override
    public String toString() {
      return name().toLowerCase();
    }
  }

  public enum IndexName {
    CHANGE,
    ACCOUNT,
    GROUP;

    @Override
    public String toString() {
      return name().toLowerCase();
    }
  }

  abstract T parse(String id);

  abstract void index(T id, Operation operation) throws IOException, OrmException;

  AbstractIndexRestApiServlet(IndexName indexName, boolean allowDelete) {
    this.indexName = indexName;
    this.allowDelete = allowDelete;
    this.idLocks = Striped.lock(10);
  }

  AbstractIndexRestApiServlet(IndexName indexName) {
    this(indexName, false);
  }

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse rsp) {
    process(req, rsp, Operation.INDEX);
  }

  @Override
  protected void doDelete(HttpServletRequest req, HttpServletResponse rsp) {
    if (!allowDelete) {
      sendError(
          rsp, SC_METHOD_NOT_ALLOWED, String.format("cannot delete %s from index", indexName));
    } else {
      process(req, rsp, Operation.DELETE);
    }
  }

  private void process(HttpServletRequest req, HttpServletResponse rsp, Operation operation) {
    setHeaders(rsp);
    String path = req.getPathInfo();
    T id = parse(path.substring(path.lastIndexOf('/') + 1));
    logger.debug("{} {} {}", operation, indexName, id);
    try {
      Context.setForwardedEvent(true);
      Lock idLock = idLocks.get(id);
      idLock.lock();
      try {
        index(id, operation);
      } finally {
        idLock.unlock();
      }
      rsp.setStatus(SC_NO_CONTENT);
    } catch (IOException e) {
      sendError(rsp, SC_CONFLICT, e.getMessage());
      logger.error("Unable to update {} index", indexName, e);
    } catch (OrmException e) {
      String msg = String.format("Error trying to find %s \n", indexName);
      sendError(rsp, SC_NOT_FOUND, msg);
      logger.debug(msg, e);
    } finally {
      Context.unsetForwardedEvent();
    }
  }
}
