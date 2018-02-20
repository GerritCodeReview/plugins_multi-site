// Copyright (C) 2018 The Android Open Source Project
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

package com.ericsson.gerrit.plugins.highavailability.autoreindex;

import com.ericsson.gerrit.plugins.highavailability.forwarder.rest.AbstractIndexRestApiServlet;
import com.ericsson.gerrit.plugins.highavailability.forwarder.rest.AbstractIndexRestApiServlet.IndexName;
import com.google.gerrit.extensions.annotations.PluginData;
import com.google.gerrit.extensions.events.AccountIndexedListener;
import com.google.gerrit.extensions.events.ChangeIndexedListener;
import com.google.gerrit.extensions.events.GroupIndexedListener;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.git.WorkQueue;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class IndexTs
    implements ChangeIndexedListener, AccountIndexedListener, GroupIndexedListener {
  private static final Logger log = LoggerFactory.getLogger(IndexTs.class);
  private static final DateTimeFormatter formatter = DateTimeFormatter.ISO_DATE_TIME;

  private final Path dataDir;
  private final WorkQueue.Executor exec;
  private final FlusherRunner flusher;
  private final SchemaFactory<ReviewDb> schemaFactory;

  private volatile LocalDateTime changeTs;
  private volatile LocalDateTime accountTs;
  private volatile LocalDateTime groupTs;

  class FlusherRunner implements Runnable {
    private Map<AbstractIndexRestApiServlet.IndexName, LocalDateTime> storedTs = new HashMap<>();

    @Override
    public void run() {
      store(AbstractIndexRestApiServlet.IndexName.CHANGE, changeTs);
      store(AbstractIndexRestApiServlet.IndexName.ACCOUNT, accountTs);
      store(AbstractIndexRestApiServlet.IndexName.GROUP, groupTs);
    }

    private void store(AbstractIndexRestApiServlet.IndexName index, LocalDateTime latestTs) {
      LocalDateTime currTs = storedTs.get(index);
      if (currTs == null || latestTs.isAfter(currTs)) {
        Path indexTsFile = dataDir.resolve(index.name().toLowerCase());
        try {
          Files.write(indexTsFile, latestTs.format(formatter).getBytes(StandardCharsets.UTF_8));
          storedTs.put(index, currTs);
        } catch (IOException e) {
          log.error("Unable to update last timestamp for index " + index, e);
        }
      }
    }
  }

  @Inject
  public IndexTs(@PluginData Path dataDir, WorkQueue queue, SchemaFactory<ReviewDb> schemaFactory) {
    this.dataDir = dataDir;
    this.exec = queue.getDefaultQueue();
    this.flusher = new FlusherRunner();
    this.schemaFactory = schemaFactory;
  }

  @Override
  public void onGroupIndexed(String uuid) {
    update(IndexName.GROUP, LocalDateTime.now());
  }

  @Override
  public void onAccountIndexed(int id) {
    update(IndexName.ACCOUNT, LocalDateTime.now());
  }

  @Override
  public void onChangeIndexed(int id) {
    Change change = null;
    try (ReviewDb db = schemaFactory.open()) {
      change = db.changes().get(new Change.Id(id));
      update(
          IndexName.CHANGE,
          change == null ? LocalDateTime.now() : change.getLastUpdatedOn().toLocalDateTime());
    } catch (OrmException e) {
      log.warn("Unable to update the latest TS for change {}", e);
    }
  }

  @Override
  public void onChangeDeleted(int id) {
    update(IndexName.CHANGE, LocalDateTime.now());
  }

  public Optional<LocalDateTime> getUpdateTs(AbstractIndexRestApiServlet.IndexName index) {
    try {
      Path indexTsFile = dataDir.resolve(index.name().toLowerCase());
      if (indexTsFile.toFile().exists()) {
        String tsString = Files.readAllLines(indexTsFile).get(0);
        return Optional.of(LocalDateTime.parse(tsString, formatter));
      }
    } catch (Exception e) {
      log.warn("Unable to read last timestamp for index {}", index, e);
    }
    return Optional.empty();
  }

  void update(AbstractIndexRestApiServlet.IndexName index, LocalDateTime dateTime) {
    switch (index) {
      case CHANGE:
        changeTs = dateTime;
        break;
      case ACCOUNT:
        accountTs = dateTime;
        break;
      case GROUP:
        groupTs = dateTime;
        break;
      default:
        throw new IllegalArgumentException("Unsupported index " + index);
    }
    exec.execute(flusher);
  }
}
