// Copyright (C) 2015 Ericsson
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

package com.ericsson.gerrit.plugins.syncindex;

import static javax.servlet.http.HttpServletResponse.SC_CONFLICT;
import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import static javax.servlet.http.HttpServletResponse.SC_NO_CONTENT;

import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.index.ChangeIndexer;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Singleton
class SyncIndexRestApiServlet extends HttpServlet {
  private static final long serialVersionUID = -1L;
  private static final Logger logger =
      LoggerFactory.getLogger(SyncIndexRestApiServlet.class);

  private final ChangeIndexer indexer;
  private final SchemaFactory<ReviewDb> schemaFactory;

  @Inject
  SyncIndexRestApiServlet(ChangeIndexer indexer,
      SchemaFactory<ReviewDb> schemaFactory) {
    this.indexer = indexer;
    this.schemaFactory = schemaFactory;
  }

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse rsp)
      throws IOException, ServletException {
    rsp.setContentType("text/plain");
    rsp.setCharacterEncoding("UTF-8");
    Change.Id id = getIdFromRequest(req.getPathInfo());

    try (ReviewDb db = schemaFactory.open()) {
      Context.setForwardedEvent(true);
      Change change = db.changes().get(id);
      if (change == null) {
        throw new NoSuchChangeException(id);
      }
      indexer.index(db, change);
      rsp.setStatus(SC_NO_CONTENT);
    } catch (IOException e) {
      rsp.sendError(SC_CONFLICT, e.getMessage());
      logger.error("Unable to update index", e);
    } catch (OrmException | NoSuchChangeException e) {
      rsp.sendError(SC_NOT_FOUND, "Change not found\n");
      logger.debug("Error trying to find a change ", e);
    } finally {
      Context.unsetForwardedEvent();
    }
  }

  @Override
  protected void doDelete(HttpServletRequest req, HttpServletResponse rsp)
      throws IOException, ServletException {
    rsp.setContentType("text/plain");
    rsp.setCharacterEncoding("UTF-8");
    Change.Id id = getIdFromRequest(req.getPathInfo());

    try {
      Context.setForwardedEvent(true);
      indexer.delete(id);
      rsp.setStatus(SC_NO_CONTENT);
    } catch (IOException e) {
      rsp.sendError(SC_CONFLICT, e.getMessage());
      logger.error("Unable to update index", e);
    } finally {
      Context.unsetForwardedEvent();
    }
  }

  private Change.Id getIdFromRequest(String path) {
    String changeId = path.substring(path.lastIndexOf('/') + 1);
    return Change.Id.parse(changeId);
  }
}
