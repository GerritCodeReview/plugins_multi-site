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

package com.ericsson.gerrit.plugins.highavailability.forwarder.rest;

import static com.google.common.net.MediaType.JSON_UTF_8;
import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import static javax.servlet.http.HttpServletResponse.SC_NO_CONTENT;
import static javax.servlet.http.HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE;

import com.ericsson.gerrit.plugins.highavailability.forwarder.ForwardedEventHandler;
import com.google.common.base.Supplier;
import com.google.common.io.CharStreams;
import com.google.common.net.MediaType;
import com.google.gerrit.server.events.Event;
import com.google.gerrit.server.events.EventDeserializer;
import com.google.gerrit.server.events.SupplierDeserializer;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Singleton
class EventRestApiServlet extends AbstractRestApiServlet {
  private static final long serialVersionUID = -1L;

  private final ForwardedEventHandler forwardedEventHandler;

  @Inject
  EventRestApiServlet(ForwardedEventHandler forwardedEventHandler) {
    this.forwardedEventHandler = forwardedEventHandler;
  }

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse rsp) {
    setHeaders(rsp);
    try {
      if (!MediaType.parse(req.getContentType()).is(JSON_UTF_8)) {
        sendError(
            rsp, SC_UNSUPPORTED_MEDIA_TYPE, "Expecting " + JSON_UTF_8.toString() + " content type");
        return;
      }
      forwardedEventHandler.dispatch(getEventFromRequest(req));
      rsp.setStatus(SC_NO_CONTENT);
    } catch (OrmException e) {
      log.debug("Error trying to find a change ", e);
      sendError(rsp, SC_NOT_FOUND, "Change not found\n");
    } catch (IOException e) {
      log.error("Unable to re-trigger event", e);
      sendError(rsp, SC_BAD_REQUEST, e.getMessage());
    }
  }

  private static Event getEventFromRequest(HttpServletRequest req) throws IOException {
    String jsonEvent = CharStreams.toString(req.getReader());
    Gson gson =
        new GsonBuilder()
            .registerTypeAdapter(Event.class, new EventDeserializer())
            .registerTypeAdapter(Supplier.class, new SupplierDeserializer())
            .create();
    return gson.fromJson(jsonEvent, Event.class);
  }
}
