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

package com.ericsson.gerrit.plugins.highavailability.forwarder;

/** Allows to tag a forwarded event to avoid infinitely looping events. */
public class Context {
  private static final ThreadLocal<Boolean> FORWARDED_EVENT =
      new ThreadLocal<Boolean>() {
        @Override
        protected Boolean initialValue() {
          return false;
        }
      };

  private Context() {}

  public static Boolean isForwardedEvent() {
    return FORWARDED_EVENT.get();
  }

  public static void setForwardedEvent(Boolean b) {
    FORWARDED_EVENT.set(b);
  }

  public static void unsetForwardedEvent() {
    FORWARDED_EVENT.remove();
  }
}
