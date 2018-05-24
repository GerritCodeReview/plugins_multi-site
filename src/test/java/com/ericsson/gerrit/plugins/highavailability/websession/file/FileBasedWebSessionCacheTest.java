// Copyright (C) 2015 The Android Open Source Project
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

package com.ericsson.gerrit.plugins.highavailability.websession.file;

import static com.google.common.truth.Truth.assertThat;

import com.ericsson.gerrit.plugins.highavailability.websession.file.FileBasedWebsessionCache.TimeMachine;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gerrit.httpd.WebSessionManager.Val;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class FileBasedWebSessionCacheTest {

  private static final String EXISTING_KEY = "aSceprtBc02YaMY573T5jfW64ZudJfPbDq";
  private static final String EMPTY_KEY = "aOc2prqlZRpSO3LpauGO5efCLs1L9r9KkG";
  private static final String INVALID_KEY = "aOFdpHriBM6dN055M13PjDdTZagl5r5aSG";
  private static final String NEW_KEY = "abcde12345";

  @Rule public TemporaryFolder tempFolder = new TemporaryFolder();

  private FileBasedWebsessionCache cache;
  private Path websessionDir;

  @Before
  public void setUp() throws Exception {
    websessionDir = tempFolder.newFolder("websessions").toPath();
    cache = new FileBasedWebsessionCache(tempFolder.getRoot().toPath());
  }

  @Test
  public void asMapTest() throws Exception {
    loadKeyToCacheDir(EMPTY_KEY);
    assertThat(cache.asMap()).isEmpty();

    loadKeyToCacheDir(INVALID_KEY);
    assertThat(cache.asMap()).isEmpty();

    loadKeyToCacheDir(EXISTING_KEY);
    assertThat(cache.asMap()).containsKey(EXISTING_KEY);
  }

  @Test
  public void constructorCreateDir() throws IOException {
    assertThat(websessionDir.toFile().delete()).isTrue();
    cache = new FileBasedWebsessionCache(tempFolder.getRoot().toPath());
    assertThat(websessionDir.toFile().exists()).isTrue();
  }

  @Test
  public void cleanUpTest() throws Exception {
    loadKeyToCacheDir(EXISTING_KEY);
    try {
      long existingKeyExpireAt = cache.getIfPresent(EXISTING_KEY).getExpiresAt();
      TimeMachine.useFixedClockAt(
          Instant.ofEpochMilli(existingKeyExpireAt).minus(1, ChronoUnit.HOURS));
      cache.cleanUp();
      assertThat(isDirEmpty(websessionDir)).isFalse();

      TimeMachine.useFixedClockAt(
          Instant.ofEpochMilli(existingKeyExpireAt).plus(1, ChronoUnit.HOURS));
      cache.cleanUp();
      assertThat(isDirEmpty(websessionDir)).isTrue();
    } finally {
      TimeMachine.useSystemDefaultZoneClock();
    }
  }

  @Test
  public void cleanUpWithErrorsWhileListingFilesTest() throws Exception {
    tempFolder.delete();
    cache.cleanUp();
    assertThat(cache.size()).isEqualTo(0);
  }

  @Test
  public void cleanUpWithErrorsWhileDeleteFileTest() throws Exception {
    loadKeyToCacheDir(EXISTING_KEY);
    try {
      websessionDir.toFile().setWritable(false);
      cache.cleanUp();
      assertThat(cache.size()).isEqualTo(1);
    } finally {
      websessionDir.toFile().setWritable(true);
    }
  }

  @Test
  public void getIfPresentEmptyKeyTest() throws Exception {
    assertThat(cache.getIfPresent(EMPTY_KEY)).isNull();
  }

  @Test
  public void getIfPresentObjectNonStringTest() throws Exception {
    assertThat(cache.getIfPresent(new Object())).isNull();
  }

  @Test
  public void getIfPresentInvalidKeyTest() throws Exception {
    loadKeyToCacheDir(INVALID_KEY);
    Path path = websessionDir.resolve(INVALID_KEY);
    assertThat(cache.getIfPresent(path)).isNull();
  }

  @Test
  public void getIfPresentTest() throws Exception {
    loadKeyToCacheDir(EXISTING_KEY);
    assertThat(cache.getIfPresent(EXISTING_KEY)).isNotNull();
  }

  @Test
  public void getAllPresentTest() throws Exception {
    loadKeyToCacheDir(EMPTY_KEY);
    loadKeyToCacheDir(INVALID_KEY);
    loadKeyToCacheDir(EXISTING_KEY);
    List<String> keys = ImmutableList.of(EMPTY_KEY, EXISTING_KEY);
    assertThat(cache.getAllPresent(keys).size()).isEqualTo(1);
    assertThat(cache.getAllPresent(keys)).containsKey(EXISTING_KEY);
  }

  @Test
  public void getTest() throws Exception {
    class ValueLoader implements Callable<Val> {
      @Override
      public Val call() throws Exception {
        return null;
      }
    }
    assertThat(cache.get(EXISTING_KEY, new ValueLoader())).isNull();

    loadKeyToCacheDir(EXISTING_KEY);
    assertThat(cache.get(EXISTING_KEY, new ValueLoader())).isNotNull();
  }

  @Test(expected = ExecutionException.class)
  public void getTestCallableThrowsException() throws Exception {
    class ValueLoader implements Callable<Val> {
      @Override
      public Val call() throws Exception {
        throw new Exception();
      }
    }
    assertThat(cache.get(EXISTING_KEY, new ValueLoader())).isNull();
  }

  @Test
  public void invalidateAllCollectionTest() throws Exception {
    int numberOfKeys = 15;
    List<String> keys = loadKeysToCacheDir(numberOfKeys);
    assertThat(cache.size()).isEqualTo(numberOfKeys);
    assertThat(isDirEmpty(websessionDir)).isFalse();

    cache.invalidateAll(keys);
    assertThat(cache.size()).isEqualTo(0);
    assertThat(isDirEmpty(websessionDir)).isTrue();
  }

  @Test
  public void invalidateAllTest() throws Exception {
    int numberOfKeys = 5;
    loadKeysToCacheDir(numberOfKeys);
    assertThat(cache.size()).isEqualTo(numberOfKeys);
    assertThat(isDirEmpty(websessionDir)).isFalse();

    cache.invalidateAll();
    assertThat(cache.size()).isEqualTo(0);
    assertThat(isDirEmpty(websessionDir)).isTrue();
  }

  @Test
  public void invalidateTest() throws Exception {
    Path fileToDelete = Files.createFile(websessionDir.resolve(EXISTING_KEY));
    assertThat(Files.exists(fileToDelete)).isTrue();
    cache.invalidate(EXISTING_KEY);
    assertThat(Files.exists(fileToDelete)).isFalse();
  }

  @Test
  public void invalidateTestObjectNotString() throws Exception {
    loadKeyToCacheDir(EXISTING_KEY);
    assertThat(cache.size()).isEqualTo(1);
    cache.invalidate(new Object());
    assertThat(cache.size()).isEqualTo(1);
  }

  @Test
  public void putTest() throws Exception {
    loadKeyToCacheDir(EXISTING_KEY);
    Val val = cache.getIfPresent(EXISTING_KEY);
    cache.put(NEW_KEY, val);
    assertThat(cache.getIfPresent(NEW_KEY)).isNotNull();
  }

  @Test
  public void putAllTest() throws Exception {
    loadKeyToCacheDir(EXISTING_KEY);
    Val val = cache.getIfPresent(EXISTING_KEY);
    Map<String, Val> sessions = ImmutableMap.of(NEW_KEY, val);
    cache.putAll(sessions);
    assertThat(cache.asMap()).containsKey(NEW_KEY);
  }

  @Test
  public void putWithErrorsTest() throws Exception {
    loadKeyToCacheDir(EXISTING_KEY);
    Val val = cache.getIfPresent(EXISTING_KEY);
    tempFolder.delete();
    cache.put(NEW_KEY, val);
    assertThat(cache.getIfPresent(NEW_KEY)).isNull();
  }

  @Test
  public void sizeTest() throws Exception {
    int numberOfKeys = 10;
    loadKeysToCacheDir(numberOfKeys);
    assertThat(cache.size()).isEqualTo(numberOfKeys);
  }

  @Test
  public void statTest() throws Exception {
    assertThat(cache.stats()).isNull();
  }

  private List<String> loadKeysToCacheDir(int number) throws IOException {
    List<String> keys = new ArrayList<>();
    for (int i = 0; i < number; i++) {
      Path tmp = Files.createTempFile(websessionDir, "cache", null);
      keys.add(tmp.getFileName().toString());
    }
    return keys;
  }

  private static boolean isDirEmpty(final Path dir) throws IOException {
    try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(dir)) {
      return !dirStream.iterator().hasNext();
    }
  }

  private Path loadKeyToCacheDir(String key) throws IOException {
    if (key.equals(EMPTY_KEY)) {
      return Files.createFile(websessionDir.resolve(EMPTY_KEY));
    }
    try (InputStream in = loadFile(key)) {
      Path target = websessionDir.resolve(key);
      Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
      return target;
    }
  }

  private InputStream loadFile(String file) {
    return this.getClass().getResourceAsStream("/" + file);
  }
}
