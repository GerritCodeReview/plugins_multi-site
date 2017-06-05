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

import static java.nio.charset.StandardCharsets.UTF_8;
import static javax.servlet.http.HttpServletResponse.SC_CONFLICT;
import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import static javax.servlet.http.HttpServletResponse.SC_NO_CONTENT;

import com.ericsson.gerrit.plugins.highavailability.forwarder.Context;
import com.google.gwtorm.server.OrmException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractIndexRestApiServlet<T> extends HttpServlet {
  private static final long serialVersionUID = -1L;
  private static final Logger logger = LoggerFactory.getLogger(AbstractIndexRestApiServlet.class);
  private final Map<T, AtomicInteger> idLocks = new HashMap<>();
  private final String type;

  enum Operation {
    INDEX,
    DELETE
  }

  abstract T parse(String id);

  abstract void index(T id, Operation operation) throws IOException, OrmException;

  AbstractIndexRestApiServlet(String type) {
    this.type = type;
  }

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse rsp)
      throws IOException, ServletException {
    process(req, rsp, Operation.INDEX);
  }

  @Override
  protected void doDelete(HttpServletRequest req, HttpServletResponse rsp)
      throws IOException, ServletException {
    process(req, rsp, Operation.DELETE);
  }

  private void process(HttpServletRequest req, HttpServletResponse rsp, Operation operation) {
    rsp.setContentType("text/plain");
    rsp.setCharacterEncoding(UTF_8.name());
    String path = req.getPathInfo();
    T id = parse(path.substring(path.lastIndexOf('/') + 1));
    try {
      Context.setForwardedEvent(true);
      AtomicInteger idLock = getAndIncrementIdLock(id);
      synchronized (idLock) {
        index(id, operation);
      }
      if (idLock.decrementAndGet() == 0) {
        removeIdLock(id);
      }
      rsp.setStatus(SC_NO_CONTENT);
    } catch (IOException e) {
      sendError(rsp, SC_CONFLICT, e.getMessage());
      logger.error(String.format("Unable to update %s index", type), e);
    } catch (OrmException e) {
      String msg = String.format("Error trying to find %s \n", type);
      sendError(rsp, SC_NOT_FOUND, msg);
      logger.debug(msg, e);
    } finally {
      Context.unsetForwardedEvent();
    }
  }

  private AtomicInteger getAndIncrementIdLock(T id) {
    synchronized (idLocks) {
      AtomicInteger lock = idLocks.get(id);
      if (lock == null) {
        lock = new AtomicInteger(1);
        idLocks.put(id, lock);
      } else {
        lock.incrementAndGet();
      }
      return lock;
    }
  }

  private void removeIdLock(T id) {
    synchronized (idLocks) {
      idLocks.remove(id);
    }
  }

  private void sendError(HttpServletResponse rsp, int statusCode, String message) {
    try {
      rsp.sendError(statusCode, message);
    } catch (IOException e) {
      logger.error("Failed to send error messsage: " + e.getMessage(), e);
    }
  }
}
