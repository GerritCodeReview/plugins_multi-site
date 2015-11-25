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

package com.ericsson.gerrit.plugins.syncevents;

import static com.google.common.net.MediaType.JSON_UTF_8;
import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import static javax.servlet.http.HttpServletResponse.SC_NO_CONTENT;

import com.google.common.base.Supplier;
import com.google.common.io.CharStreams;
import com.google.common.net.MediaType;
import com.google.gerrit.common.EventDispatcher;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.events.Event;
import com.google.gerrit.server.events.EventDeserializer;
import com.google.gerrit.server.events.SupplierDeserializer;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
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
class SyncEventsRestApiServlet extends HttpServlet {
  private static final long serialVersionUID = -1L;
  private static final Logger logger = LoggerFactory
      .getLogger(SyncEventsRestApiServlet.class);

  private final EventDispatcher dispatcher;
  private final SchemaFactory<ReviewDb> schema;

  @Inject
  SyncEventsRestApiServlet(EventDispatcher dispatcher,
      SchemaFactory<ReviewDb> schema) {
    this.dispatcher = dispatcher;
    this.schema = schema;
  }

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse rsp)
      throws IOException, ServletException {
    rsp.setContentType("text/plain");
    rsp.setCharacterEncoding("UTF-8");
    try {
      Context.setForwardedEvent();
      Event event = getEventFromRequest(req);
      try (ReviewDb db = schema.open()) {
        dispatcher.postEvent(event, db);
      }
      rsp.setStatus(SC_NO_CONTENT);
    } catch (OrmException e) {
      rsp.sendError(SC_NOT_FOUND, "Change not found\n");
      logger.debug("Error trying to find a change ", e);
    } catch (IOException e) {
      rsp.sendError(SC_BAD_REQUEST, e.getMessage());
      logger.error("Unable to re-trigger event", e);
    } finally {
      Context.unsetForwardedEvent();
    }
  }

  private Event getEventFromRequest(HttpServletRequest req) throws IOException {
    if (MediaType.parse(req.getContentType()).is(JSON_UTF_8)) {
      String jsonEvent = CharStreams.toString(req.getReader());
      Gson gson = new GsonBuilder()
          .registerTypeAdapter(Event.class, new EventDeserializer())
          .registerTypeAdapter(Supplier.class, new SupplierDeserializer())
          .create();
      return gson.fromJson(jsonEvent, Event.class);
    }
    return null;
  }
}
